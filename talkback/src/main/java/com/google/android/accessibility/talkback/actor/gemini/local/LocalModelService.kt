/*
 * Copyright 2026 Backtalk contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */

package com.google.android.accessibility.talkback.actor.gemini.local

import android.app.ActivityManager
import android.app.Service
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.HandlerThread
import android.os.IBinder
import android.os.Message
import android.os.Messenger
import android.os.RemoteException
import android.util.Log
import java.io.File
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger

/**
 * Runs the on-device model in its own process, `:ondeviceai`. A model takes gigabytes of memory,
 * and when the phone runs out, Android stops the process that holds it. In Backtalk's process that
 * stopped the screen reader. Here it only stops the model, and Backtalk says what happened.
 *
 * Backtalk talks to it through [RemoteLocalLlm], with the messages in the companion object. Before
 * loading a model, it checks that the phone has enough free memory for it.
 */
class LocalModelService : Service() {
  private data class Config(
    val path: String,
    val lastModified: Long,
    val useGpu: Boolean,
    val maxTokens: Int?,
  )

  private val worker =
    Executors.newSingleThreadExecutor { Thread(it, "on-device-ai").apply { isDaemon = true } }
  private lateinit var ipcThread: HandlerThread
  private lateinit var messenger: Messenger
  // Only the worker thread changes these.
  @Volatile private var llm: LiteRtLmLocalLlm? = null
  private var llmConfig: Config? = null
  @Volatile private var runningId = 0
  @Volatile private var checkMemory = true
  private val cancelledUpTo = AtomicInteger(0)

  override fun onCreate() {
    super.onCreate()
    ipcThread = HandlerThread("on-device-ai-ipc").apply { start() }
    messenger =
      Messenger(
        Handler(ipcThread.looper) {
          handle(it)
          true
        }
      )
  }

  override fun onBind(intent: Intent): IBinder = messenger.binder

  override fun onDestroy() {
    llm?.cancel()
    worker.execute {
      llm?.close()
      llm = null
    }
    worker.shutdown()
    ipcThread.quitSafely()
    super.onDestroy()
  }

  private fun handle(message: Message) {
    when (message.what) {
      MSG_GENERATE -> {
        val id = message.arg1
        val data = Bundle(message.data)
        val replyTo = message.replyTo
        worker.execute { generate(id, data, replyTo) }
      }
      MSG_CANCEL -> {
        val id = message.arg1
        cancelledUpTo.accumulateAndGet(id, ::maxOf)
        if (runningId == id) llm?.cancel()
      }
    }
  }

  private fun generate(id: Int, data: Bundle, replyTo: Messenger) {
    val reply =
      try {
        if (id <= cancelledUpTo.get()) throw LocalLlmCancelledException()
        runningId = id
        checkMemory = data.getBoolean(KEY_CHECK_MEMORY, true)
        val jpeg = data.getString(KEY_IMAGE_PATH)?.let { File(it).readBytes() }
        val answer = llmFor(data).generate(data.getString(KEY_PROMPT).orEmpty(), jpeg)
        if (id <= cancelledUpTo.get()) throw LocalLlmCancelledException()
        Message.obtain(null, MSG_ANSWER).apply { this.data.putString(KEY_TEXT, answer) }
      } catch (_: LocalLlmCancelledException) {
        Message.obtain(null, MSG_CANCELLED)
      } catch (_: LocalLlmMemoryException) {
        Message.obtain(null, MSG_LOW_MEMORY)
      } catch (e: Exception) {
        Message.obtain(null, MSG_FAILED).apply {
          this.data.putString(KEY_TEXT, e.message ?: e.toString())
        }
      } finally {
        runningId = 0
      }
    reply.arg1 = id
    try {
      replyTo.send(reply)
    } catch (e: RemoteException) {
      Log.w(TAG, "Backtalk is gone", e)
    }
  }

  /** The model for [data]'s settings, replacing the loaded one when they changed. */
  private fun llmFor(data: Bundle): LiteRtLmLocalLlm {
    val file = File(data.getString(KEY_MODEL_PATH).orEmpty())
    val maxTokens = data.getInt(KEY_MAX_TOKENS, NO_MAX_TOKENS).takeIf { it != NO_MAX_TOKENS }
    val config = Config(file.path, file.lastModified(), data.getBoolean(KEY_USE_GPU), maxTokens)
    llm?.let {
      if (config == llmConfig) return it
      it.close()
    }
    return LiteRtLmLocalLlm(
        this,
        file,
        config.useGpu,
        maxTokens,
        beforeLoad = { if (checkMemory) checkFreeMemory(file.length()) },
      )
      .also {
        llm = it
        llmConfig = config
      }
  }

  private fun checkFreeMemory(modelBytes: Long) {
    val memory = ActivityManager.MemoryInfo()
    getSystemService(ActivityManager::class.java).getMemoryInfo(memory)
    Log.i(TAG, "${memory.availMem / MIB} MB free for a ${modelBytes / MIB} MB model")
    if (!OnDeviceAiSettings.hasFreeMemoryFor(modelBytes, memory.availMem, memory.lowMemory)) {
      throw LocalLlmMemoryException("Not enough free memory")
    }
  }

  companion object {
    private const val TAG = "LocalModelService"
    private const val MIB = 1024L * 1024L

    /** Backtalk asks for an answer. arg1 is the request ID, replyTo gets the answer. */
    const val MSG_GENERATE = 1

    /** Backtalk cancels the request whose ID is in arg1, and any before it. */
    const val MSG_CANCEL = 2

    /** The answer, in [KEY_TEXT]. */
    const val MSG_ANSWER = 3

    const val MSG_CANCELLED = 4

    /** There was not enough free memory to load the model. */
    const val MSG_LOW_MEMORY = 5

    /** Any other failure, described in [KEY_TEXT]. */
    const val MSG_FAILED = 6

    const val KEY_MODEL_PATH = "model_path"
    const val KEY_USE_GPU = "use_gpu"
    const val KEY_MAX_TOKENS = "max_tokens"
    const val KEY_PROMPT = "prompt"

    /** False to load without checking for free memory, to test running out. */
    const val KEY_CHECK_MEMORY = "check_memory"

    /** A JPEG file to look at. Images go through a file because a message can only hold 1 MB. */
    const val KEY_IMAGE_PATH = "image_path"
    const val KEY_TEXT = "text"
    const val NO_MAX_TOKENS = -1
  }
}
