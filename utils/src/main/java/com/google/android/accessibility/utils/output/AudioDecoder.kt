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
import android.media.AudioFormat
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import com.google.android.libraries.accessibility.utils.log.LogUtils
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Decodes short sounds to float samples with Android's decoders: Ogg Vorbis, WAV, MP3 and the rest,
 * and MIDI, which Android's extractor renders to raw audio itself.
 */
object AudioDecoder {
  private const val TAG = "AudioDecoder"
  private const val TIMEOUT_US = 10_000L
  // Sounds are short, so a decoder that never finishes gives up rather than holding a thread.
  private const val MAX_STEPS = 4_000

  /** Interleaved float samples, from -1 to 1. */
  class Decoded(val samples: FloatArray, val channels: Int, val sampleRate: Int)

  /** Decodes a raw resource, or returns null if Android cannot decode it. */
  @JvmStatic
  fun decode(context: Context, resId: Int): Decoded? {
    val extractor = MediaExtractor()
    return try {
      context.resources.openRawResourceFd(resId).use {
        extractor.setDataSource(it.fileDescriptor, it.startOffset, it.length)
      }
      decode(extractor)
    } catch (e: Exception) {
      LogUtils.w(TAG, "Cannot decode sound %d: %s", resId, e)
      null
    } finally {
      extractor.release()
    }
  }

  private fun decode(extractor: MediaExtractor): Decoded? {
    val track =
      (0 until extractor.trackCount).firstOrNull {
        extractor.getTrackFormat(it).getString(MediaFormat.KEY_MIME)?.startsWith("audio/") == true
      } ?: return null
    extractor.selectTrack(track)
    val format = extractor.getTrackFormat(track)
    val mime = format.getString(MediaFormat.KEY_MIME)!!
    val rate = format.getInteger(MediaFormat.KEY_SAMPLE_RATE)
    val channels = format.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
    return if (mime == MediaFormat.MIMETYPE_AUDIO_RAW) {
      readRaw(extractor, format, rate, channels)
    } else {
      decodeWithCodec(extractor, format, mime, rate, channels)
    }
  }

  /** Reads audio the extractor already gives as samples, such as rendered MIDI. */
  private fun readRaw(
    extractor: MediaExtractor,
    format: MediaFormat,
    rate: Int,
    channels: Int,
  ): Decoded {
    val encoding = pcmEncoding(format)
    val samples = FloatList()
    val buffer = ByteBuffer.allocate(64 * 1024)
    while (true) {
      buffer.clear()
      val size = extractor.readSampleData(buffer, 0)
      if (size < 0) break
      buffer.limit(size)
      toFloats(buffer, encoding, samples)
      extractor.advance()
    }
    return Decoded(samples.toArray(), channels, rate)
  }

  private fun decodeWithCodec(
    extractor: MediaExtractor,
    format: MediaFormat,
    mime: String,
    initialRate: Int,
    initialChannels: Int,
  ): Decoded? {
    var rate = initialRate
    var channels = initialChannels
    var encoding = AudioFormat.ENCODING_PCM_16BIT
    val codec = MediaCodec.createDecoderByType(mime)
    try {
      codec.configure(format, null, null, 0)
      codec.start()
      val samples = FloatList()
      val info = MediaCodec.BufferInfo()
      var inputDone = false
      repeat(MAX_STEPS) {
        if (!inputDone) {
          val index = codec.dequeueInputBuffer(TIMEOUT_US)
          if (index >= 0) {
            val size = extractor.readSampleData(codec.getInputBuffer(index)!!, 0)
            if (size < 0) {
              codec.queueInputBuffer(index, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
              inputDone = true
            } else {
              codec.queueInputBuffer(index, 0, size, extractor.sampleTime, 0)
              extractor.advance()
            }
          }
        }
        val index = codec.dequeueOutputBuffer(info, TIMEOUT_US)
        if (index == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
          val output = codec.outputFormat
          rate = output.getInteger(MediaFormat.KEY_SAMPLE_RATE)
          channels = output.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
          encoding = pcmEncoding(output)
        } else if (index >= 0) {
          val output = codec.getOutputBuffer(index)!!
          output.position(info.offset)
          output.limit(info.offset + info.size)
          toFloats(output, encoding, samples)
          codec.releaseOutputBuffer(index, false)
          if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) {
            return Decoded(samples.toArray(), channels, rate)
          }
        }
      }
      return null
    } finally {
      try {
        codec.stop()
      } catch (e: IllegalStateException) {
        // Never started.
      }
      codec.release()
    }
  }

  private fun pcmEncoding(format: MediaFormat): Int =
    if (format.containsKey(MediaFormat.KEY_PCM_ENCODING)) {
      format.getInteger(MediaFormat.KEY_PCM_ENCODING)
    } else {
      AudioFormat.ENCODING_PCM_16BIT
    }

  /** Appends the samples in [buffer], in [encoding], to [out] as floats. */
  @JvmStatic
  fun toFloats(buffer: ByteBuffer, encoding: Int, out: FloatList) {
    val data = buffer.slice().order(ByteOrder.nativeOrder())
    when (encoding) {
      AudioFormat.ENCODING_PCM_FLOAT -> {
        val floats = data.asFloatBuffer()
        while (floats.hasRemaining()) out.add(floats.get())
      }
      AudioFormat.ENCODING_PCM_8BIT -> {
        while (data.hasRemaining()) out.add(((data.get().toInt() and 0xFF) - 128) / 128f)
      }
      else -> {
        val shorts = data.asShortBuffer()
        while (shorts.hasRemaining()) out.add(shorts.get() / 32768f)
      }
    }
  }

  /** A growing list of floats, without boxing each sample. */
  class FloatList {
    private var values = FloatArray(4096)
    var size = 0
      private set

    fun add(value: Float) {
      if (size == values.size) values = values.copyOf(size * 2)
      values[size++] = value
    }

    fun toArray(): FloatArray = values.copyOf(size)
  }
}
