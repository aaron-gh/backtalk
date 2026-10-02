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

package com.google.android.accessibility.utils.output

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.os.Handler
import android.os.HandlerThread
import com.google.android.accessibility.utils.R
import com.google.android.libraries.accessibility.utils.log.LogUtils
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Plays short sounds in 3D through headphones with [Hrtf]. Sounds are rendered and played on a
 * thread of their own, and a new sound stops the one before it, as in Unspoken, so swiping quickly
 * never piles sounds up.
 *
 * Only mono, 16 bit WAV resources at 44.1 kHz can be played this way.
 */
class SpatialSoundPlayer(private val context: Context) {
  private val thread = HandlerThread("SpatialSoundPlayer").apply { start() }
  private val handler = Handler(thread.looper)

  // Everything below is used only on the player's thread.
  private var hrtf: Hrtf? = null
  private val sounds = HashMap<Int, FloatArray?>()
  private var track: AudioTrack? = null

  /**
   * Plays [resId] as if it came from [x] and [y], fractions of the screen from its left and top
   * edges, at [volume] from 0 to 1.
   */
  fun play(resId: Int, x: Float, y: Float, volume: Float) {
    handler.post {
      try {
        val mono = sounds.getOrPut(resId) { readWav(resId) } ?: return@post
        val hrtf = hrtf ?: loadHrtf().also { hrtf = it }
        val stereo =
          hrtf.render(mono, Hrtf.azimuthForScreen(x), Hrtf.elevationForScreen(y))
        start(stereo, volume)
      } catch (e: RuntimeException) {
        // A broken sound or a refused audio track must never take the screen reader down.
        LogUtils.e(TAG, "Could not play sound %d: %s", resId, e)
      }
    }
  }

  /** Stops the sound playing now, if any, and frees the thread. */
  fun shutdown() {
    handler.post {
      stopCurrent()
      thread.quitSafely()
    }
  }

  private fun start(stereo: FloatArray, volume: Float) {
    stopCurrent()
    val frames = stereo.size / 2
    val newTrack =
      AudioTrack.Builder()
        .setAudioAttributes(
          AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_ASSISTANCE_ACCESSIBILITY)
            .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
            .build()
        )
        .setAudioFormat(
          AudioFormat.Builder()
            .setEncoding(AudioFormat.ENCODING_PCM_FLOAT)
            .setSampleRate(Hrtf.SAMPLE_RATE)
            .setChannelMask(AudioFormat.CHANNEL_OUT_STEREO)
            .build()
        )
        .setTransferMode(AudioTrack.MODE_STATIC)
        .setBufferSizeInBytes(stereo.size * Float.SIZE_BYTES)
        .build()
    newTrack.write(stereo, 0, stereo.size, AudioTrack.WRITE_BLOCKING)
    newTrack.setVolume(volume.coerceIn(0f, 1f))
    newTrack.play()
    track = newTrack
    // Free the track once it has finished, unless a newer sound has replaced it already.
    val millis = frames * 1000L / Hrtf.SAMPLE_RATE
    handler.postDelayed({ if (track === newTrack) stopCurrent() }, millis + RELEASE_DELAY_MS)
  }

  private fun stopCurrent() {
    val current = track ?: return
    track = null
    try {
      current.stop()
    } catch (e: IllegalStateException) {
      // Already stopped.
    }
    current.release()
  }

  private fun loadHrtf(): Hrtf =
    context.resources.openRawResource(R.raw.hrtf_kemar).use { Hrtf.parse(it.readBytes()) }

  /** Returns the samples of a mono, 16 bit, 44.1 kHz WAV resource, or null for anything else. */
  private fun readWav(resId: Int): FloatArray? {
    val bytes = context.resources.openRawResource(resId).use { it.readBytes() }
    val samples = decodeWav(bytes)
    if (samples == null) LogUtils.w(TAG, "Sound %d is not mono 16 bit 44.1 kHz WAV", resId)
    return samples
  }

  companion object {
    private const val TAG = "SpatialSoundPlayer"
    private const val RELEASE_DELAY_MS = 200L

    /** Decodes a mono, 16 bit, 44.1 kHz PCM WAV file, or returns null for any other format. */
    @JvmStatic
    fun decodeWav(bytes: ByteArray): FloatArray? {
      val buffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
      if (bytes.size < 12 || tag(buffer, 0) != "RIFF" || tag(buffer, 8) != "WAVE") return null
      var position = 12
      var formatOk = false
      while (position + 8 <= bytes.size) {
        val id = tag(buffer, position)
        val size = buffer.getInt(position + 4)
        val body = position + 8
        if (size < 0 || body + size > bytes.size) return null
        when (id) {
          "fmt " -> {
            if (size < 16) return null
            val format = buffer.getShort(body).toInt()
            val channels = buffer.getShort(body + 2).toInt()
            val rate = buffer.getInt(body + 4)
            val bits = buffer.getShort(body + 14).toInt()
            formatOk = format == 1 && channels == 1 && rate == Hrtf.SAMPLE_RATE && bits == 16
          }
          "data" -> {
            if (!formatOk) return null
            return FloatArray(size / 2) { buffer.getShort(body + 2 * it) / 32768f }
          }
        }
        // Chunks are padded to an even length.
        position = body + size + (size and 1)
      }
      return null
    }

    private fun tag(buffer: ByteBuffer, at: Int): String =
      String(ByteArray(4) { buffer.get(at + it) }, Charsets.US_ASCII)
  }
}
