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

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.Message
import android.os.Messenger
import android.os.RemoteException
import android.util.Log
import com.google.android.accessibility.talkback.R
import com.google.android.accessibility.utils.SharedPreferencesUtils
import java.io.File
import java.io.IOException
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CountDownLatch
import java.util.concurrent.ExecutionException
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

/**
 * Runs [model] in [LocalModelService]'s process, so that Backtalk keeps running when Android stops
 * the model for lack of memory. Then [generate] throws [LocalLlmMemoryException] with a message to
 * speak, as it does when there is not enough free memory to load the model.
 *
 * Stays bound to the service while answering, and unbinds after it has been idle as long as the
 * model stays loaded, so the process and its memory go away.
 */
class RemoteLocalLlm(
  context: Context,
  private val model: LocalModel,
  private val modelFile: File,
  private val useGpu: Boolean,
) : LocalLlm {
  private class Reply(val what: Int, val text: String?)

  private val appContext = context.applicationContext
  private val prefs = SharedPreferencesUtils.getSharedPreferences(appContext)
  private val main = Handler(Looper.getMainLooper())
  private val replies =
    Messenger(
      Handler(Looper.getMainLooper()) {
        pending[it.arg1]?.complete(Reply(it.what, it.data.getString(LocalModelService.KEY_TEXT)))
        true
      }
    )
  private val pending = ConcurrentHashMap<Int, CompletableFuture<Reply>>()
  private val lock = Any()
  // Guarded by lock.
  private var service: Messenger? = null
  private var bound = false
  private var connected = CountDownLatch(1)
  @Volatile private var currentId = 0
  @Volatile private var cancelRequested = false
  private val unbindIdle = Runnable { unbind() }

  private val connection =
    object : ServiceConnection {
      override fun onServiceConnected(name: ComponentName, binder: IBinder) {
        synchronized(lock) {
          service = Messenger(binder)
          connected.countDown()
        }
      }

      override fun onServiceDisconnected(name: ComponentName) {
        // The process stopped, most likely because Android ran out of memory. Drop the binding,
        // so it is not started again until the next request.
        Log.w(TAG, "The on-device AI process stopped")
        unbind()
        stopAll()
      }

      override fun onBindingDied(name: ComponentName) {
        unbind()
        stopAll()
      }
    }

  override fun generate(prompt: String, jpeg: ByteArray?): String {
    cancelRequested = false
    main.removeCallbacks(unbindIdle)
    val id = NEXT_ID.incrementAndGet()
    val future = CompletableFuture<Reply>()
    pending[id] = future
    currentId = id
    var imageFile: File? = null
    try {
      val messenger = connect()
      imageFile = jpeg?.let { File(appContext.cacheDir, "on-device-ai-$id.jpg").apply { writeBytes(it) } }
      val request = Message.obtain(null, LocalModelService.MSG_GENERATE)
      request.arg1 = id
      request.replyTo = replies
      request.data.apply {
        putString(LocalModelService.KEY_MODEL_PATH, modelFile.path)
        putBoolean(LocalModelService.KEY_USE_GPU, useGpu)
        putInt(
          LocalModelService.KEY_MAX_TOKENS,
          model.maxTokens ?: LocalModelService.NO_MAX_TOKENS,
        )
        putBoolean(LocalModelService.KEY_CHECK_MEMORY, !OnDeviceAiSettings.ignoreMemoryLimits(prefs))
        putString(LocalModelService.KEY_PROMPT, prompt)
        imageFile?.let { putString(LocalModelService.KEY_IMAGE_PATH, it.path) }
      }
      messenger.send(request)
      if (cancelRequested) cancel()
      val reply = future.get()
      return when (reply.what) {
        LocalModelService.MSG_ANSWER -> reply.text.orEmpty()
        LocalModelService.MSG_CANCELLED -> throw LocalLlmCancelledException()
        LocalModelService.MSG_LOW_MEMORY -> throw notEnoughMemory()
        MSG_STOPPED -> throw stopped()
        else -> throw LocalLlmException(reply.text ?: "The on-device model failed")
      }
    } catch (_: RemoteException) {
      throw stopped()
    } catch (e: IOException) {
      throw LocalLlmException("Could not pass the image to the model", e)
    } catch (e: ExecutionException) {
      throw LocalLlmException(e.cause?.message ?: "The on-device model failed", e)
    } finally {
      pending.remove(id)
      currentId = 0
      imageFile?.delete()
      main.postDelayed(unbindIdle, IDLE_UNBIND_MS)
    }
  }

  override fun cancel() {
    cancelRequested = true
    val id = currentId
    if (id == 0) return
    // Let Backtalk go on at once. The service drops the answer when it comes.
    pending[id]?.complete(Reply(LocalModelService.MSG_CANCELLED, null))
    val messenger = synchronized(lock) { service } ?: return
    try {
      messenger.send(Message.obtain(null, LocalModelService.MSG_CANCEL).apply { arg1 = id })
    } catch (e: RemoteException) {
      Log.w(TAG, "Could not cancel", e)
    }
  }

  /** Stops the model and lets its process go. */
  fun close() {
    cancel()
    main.removeCallbacks(unbindIdle)
    unbind()
  }

  /** Binds to the service if needed, and waits for it. */
  private fun connect(): Messenger {
    val latch =
      synchronized(lock) {
        service?.let {
          return it
        }
        if (!bound) {
          connected = CountDownLatch(1)
          // Not perceptible: when memory runs out, Android stops the model before Backtalk.
          val flags =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
              Context.BIND_AUTO_CREATE or Context.BIND_NOT_PERCEPTIBLE
            } else {
              Context.BIND_AUTO_CREATE
            }
          bound =
            appContext.bindService(
              Intent(appContext, LocalModelService::class.java),
              connection,
              flags,
            )
          if (!bound) throw LocalLlmException("Could not start on-device AI")
        }
        connected
      }
    if (!latch.await(CONNECT_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
      throw LocalLlmException("On-device AI did not start")
    }
    return synchronized(lock) { service } ?: throw stopped()
  }

  private fun unbind() {
    synchronized(lock) {
      if (bound) {
        try {
          appContext.unbindService(connection)
        } catch (e: IllegalArgumentException) {
          Log.w(TAG, "Was not bound", e)
        }
      }
      bound = false
      service = null
      connected.countDown()
    }
  }

  /** Ends every request in progress, because the process that was answering them is gone. */
  private fun stopAll() {
    pending.values.forEach { it.complete(Reply(MSG_STOPPED, null)) }
  }

  private fun notEnoughMemory() =
    LocalLlmMemoryException(
      appContext.getString(R.string.on_device_ai_not_enough_memory, model.name)
    )

  private fun stopped() =
    LocalLlmMemoryException(appContext.getString(R.string.on_device_ai_stopped, model.name))

  companion object {
    private const val TAG = "RemoteLocalLlm"
    private const val CONNECT_TIMEOUT_SECONDS = 10L

    /** A reply that never comes from the service: its process stopped. */
    private const val MSG_STOPPED = -1

    /** A little longer than the service keeps an idle model loaded. */
    private const val IDLE_UNBIND_MS = LiteRtLmLocalLlm.DEFAULT_IDLE_UNLOAD_MS + 10_000L

    private val NEXT_ID = AtomicInteger(0)
  }
}
