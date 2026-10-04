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
import android.media.AudioManager
import android.media.AudioTrack
import android.os.Process
import android.os.SystemClock
import com.google.android.libraries.accessibility.utils.log.LogUtils
import java.nio.ByteBuffer
import java.util.ArrayDeque
import java.util.concurrent.Executors

/**
 * Plays Backtalk's sounds and speech on one low-latency audio track at the device's own sample
 * rate, so that Android can give it its fast path, which reaches the speaker tens of milliseconds
 * sooner than ordinary tracks. Short sounds are mixed together, and speech streams play one after
 * another, as they arrive from the engine.
 *
 * The track keeps running while there is something to play and for a moment after, so that the
 * next sound starts at once, then pauses.
 */
class LowLatencyAudio private constructor(context: Context, private val attributes: AudioAttributes) {
  /** Output frames per second. */
  val sampleRate: Int

  private val burstFrames: Int
  private val lock = java.lang.Object()
  private val clips = ArrayList<PlayingClip>()
  private val streams = ArrayDeque<SpeechStream>()
  // Listener callbacks run here, in order, so that they never hold up the audio thread.
  private val callbacks = Executors.newSingleThreadExecutor()
  @Volatile private var track: AudioTrack?
  // Set when no track can be made, or the track stops taking audio and cannot be remade. Then audio
  // plays the usual way.
  @Volatile private var broken = false
  // When the track last took audio, to find a track that has stopped.
  @Volatile private var lastWrite = 0L
  private var watchdog: java.util.concurrent.ScheduledExecutorService? = null
  private var thread: Thread? = null
  private var idleFrames = 0
  // While held, speech streams keep what they have and keep receiving audio, but do not play.
  private var held = false

  init {
    val audioManager = context.getSystemService(AudioManager::class.java)
    sampleRate =
      audioManager?.getProperty(AudioManager.PROPERTY_OUTPUT_SAMPLE_RATE)?.toIntOrNull()
        ?: DEFAULT_RATE
    burstFrames =
      audioManager?.getProperty(AudioManager.PROPERTY_OUTPUT_FRAMES_PER_BUFFER)?.toIntOrNull()
        ?: DEFAULT_BURST
    track = createTrack()
  }

  /** A short sound, as stereo frames at [sampleRate]. */
  class Clip internal constructor(internal val frames: FloatArray)

  private class PlayingClip(val clip: Clip, val left: Float, val right: Float, val step: Double) {
    var position = 0.0
  }

  /** Converts decoded audio to a clip for this output. */
  fun prepare(decoded: AudioDecoder.Decoded): Clip {
    val resampled =
      SincResampler(decoded.sampleRate, sampleRate, decoded.channels).let {
        val out = it.process(decoded.samples)
        out + it.flush()
      }
    return Clip(toStereo(resampled, decoded.channels))
  }

  /** Plays [clip] at [left] and [right] volumes from 0 to 1, and [rate], 1 for its own speed. */
  fun play(clip: Clip, left: Float, right: Float, rate: Float) {
    synchronized(lock) {
      clips += PlayingClip(clip, left, right, rate.toDouble().coerceIn(0.25, 4.0))
      wake()
    }
  }

  /** Called on a callback thread as a speech stream plays. */
  interface StreamListener {
    /** The stream's first sound reached the output. */
    fun onStarted(id: String)

    /** Speech reached the word range [start] to [end] of the text. */
    fun onRange(id: String, start: Int, end: Int)

    /** The stream played to its end, or was stopped before it, if not [completed]. */
    fun onFinished(id: String, completed: Boolean)

    /** The engine finished without giving any audio, so the stream played nothing. */
    fun onNoAudio(id: String)

    /**
     * The stream waited at the front of the queue for [STALL_MS] without the engine giving it any
     * audio or finishing, so it was dropped, to let the speech after it play.
     */
    fun onStalled(id: String)

    /**
     * The engine's audio has been digital silence for [SILENT_MS], as if it applied the zero volume
     * Backtalk gives it itself, so the stream plays nothing.
     */
    fun onSilent(id: String)
  }

  /**
   * Speech from an engine, played after the streams opened before it. Audio is written to it as
   * the engine makes it.
   */
  inner class SpeechStream internal constructor(
    val id: String,
    private val volume: Float,
    private val pan: Float,
    private val listener: StreamListener,
  ) {
    private var resampler: SincResampler? = null
    private var inChannels = 1
    private var inRate = 0
    private var inFrames = 0L
    private var heardSound = false
    private var reportedSilent = false
    private var inEncoding = AudioFormat.ENCODING_PCM_16BIT
    private val pending = ArrayDeque<FloatArray>()
    private var headOffset = 0
    private var framesWritten = 0L
    private var framesPlayed = 0L
    private var started = false
    private var ended = false
    private var finished = false
    // When the engine last gave this stream anything, or it reached the front of the queue.
    private var lastActivity = SystemClock.uptimeMillis()
    // When this stream reached the front of the queue, or 0 before.
    private var headSince = 0L
    // Word ranges by the output frame they start at.
    private val ranges = ArrayDeque<LongArray>()

    /** The engine's format, from the start of synthesis. */
    fun begin(rate: Int, encoding: Int, channels: Int) {
      synchronized(lock) {
        lastActivity = SystemClock.uptimeMillis()
        inChannels = channels.coerceAtLeast(1)
        inRate = rate
        inEncoding = encoding
        resampler = SincResampler(rate, sampleRate, inChannels)
      }
    }

    /** A piece of the engine's audio. */
    fun write(audio: ByteArray) {
      val samples = AudioDecoder.FloatList()
      AudioDecoder.toFloats(ByteBuffer.wrap(audio), inEncoding, samples)
      val floats = samples.toArray()
      synchronized(lock) {
        lastActivity = SystemClock.uptimeMillis()
        val resampler = resampler ?: return
        if (finished) return
        inFrames += floats.size / inChannels
        if (!heardSound && floats.any { it != 0f }) heardSound = true
        if (!heardSound && inRate > 0 && inFrames * 1000 / inRate >= SILENT_MS) reportSilent()
        add(toStereo(resampler.process(floats), inChannels))
      }
    }

    /**
     * The engine reached a word range, at [frame] of its audio, or at the end of the audio written
     * so far if [frame] is unknown.
     */
    fun range(start: Int, end: Int, frame: Int) {
      synchronized(lock) {
        val at =
          if (frame > 0 && inRate > 0) frame.toLong() * sampleRate / inRate else framesWritten
        ranges += longArrayOf(at, start.toLong(), end.toLong())
      }
    }

    private fun reportSilent() {
      if (reportedSilent) return
      reportedSilent = true
      callbacks.execute { listener.onSilent(id) }
    }

    /** The engine finished making the audio. */
    fun end() {
      synchronized(lock) {
        // A short utterance that is all silence, such as a pause or punctuation, says nothing
        // about whether the engine silenced its audio. Only SILENT_MS of silence does.
        resampler?.let { add(toStereo(it.flush(), inChannels)) }
        ended = true
        wake()
      }
    }

    private fun add(frames: FloatArray) {
      if (frames.isEmpty()) return
      pending += frames
      framesWritten += frames.size / 2
      wake()
    }

    /** Mixes this stream's next frames into [out], and returns whether it has more to play. */
    internal fun mixInto(out: FloatArray, frames: Int): Boolean {
      var written = 0
      while (written < frames && pending.isNotEmpty()) {
        val head = pending.peekFirst()!!
        val available = head.size / 2 - headOffset
        val count = minOf(available, frames - written)
        val left = volume * minOf(1f, 1 - pan)
        val right = volume * minOf(1f, 1 + pan)
        for (i in 0 until count) {
          out[(written + i) * 2] += head[(headOffset + i) * 2] * left
          out[(written + i) * 2 + 1] += head[(headOffset + i) * 2 + 1] * right
        }
        written += count
        headOffset += count
        if (headOffset * 2 >= head.size) {
          pending.removeFirst()
          headOffset = 0
        }
      }
      if (written > 0 && !started) {
        started = true
        callbacks.execute { listener.onStarted(id) }
      }
      framesPlayed += written
      while (ranges.isNotEmpty() && ranges.peekFirst()!![0] <= framesPlayed) {
        val range = ranges.removeFirst()
        callbacks.execute { listener.onRange(id, range[1].toInt(), range[2].toInt()) }
      }
      if (ended && pending.isEmpty()) {
        finish(completed = true)
        return false
      }
      return true
    }

    /**
     * Whether this stream, at the front of the queue since [now] or before, has waited [STALL_MS]
     * without the engine giving it audio or finishing, such as after the engine was shut down or
     * frozen.
     */
    internal fun isStalled(now: Long): Boolean {
      if (headSince == 0L) headSince = now
      return !ended && pending.isEmpty() && now - maxOf(lastActivity, headSince) > STALL_MS
    }

    /** Ends this stream for another way to play it, without reporting that it finished. */
    internal fun abandon() {
      finished = true
      pending.clear()
      callbacks.execute { listener.onStalled(id) }
    }

    internal fun finish(completed: Boolean) {
      if (finished) return
      finished = true
      pending.clear()
      val noAudio = completed && framesWritten == 0L
      callbacks.execute {
        if (noAudio) listener.onNoAudio(id) else listener.onFinished(id, completed)
      }
    }

    /** Stops this stream, whether or not it has started playing. */
    fun stop() {
      synchronized(lock) {
        if (streams.remove(this)) finish(completed = false)
      }
    }

    /** Removes this stream without reporting anything, as if it had never been opened. */
    fun discard() {
      synchronized(lock) {
        streams.remove(this)
        finished = true
        pending.clear()
      }
    }
  }

  /**
   * Opens a speech stream at [volume] from 0 to 1 and [pan] from -1, left, to 1, right. It plays
   * once the streams before it have finished.
   */
  fun openStream(id: String, volume: Float, pan: Float, listener: StreamListener): SpeechStream {
    synchronized(lock) {
      val stream = SpeechStream(id, volume.coerceIn(0f, 1f), pan.coerceIn(-1f, 1f), listener)
      streams += stream
      wake()
      return stream
    }
  }

  /** Stops every speech stream, which each report that they were stopped. */
  fun stopStreams() {
    synchronized(lock) {
      held = false
      while (streams.isNotEmpty()) streams.removeFirst().finish(completed = false)
    }
  }

  /** Removes the speech stream [id] without reporting anything, if it is still queued. */
  fun discardStream(id: String) {
    synchronized(lock) { streams.firstOrNull { it.id == id }?.discard() }
  }

  /** The ID of the speech stream playing, or next to play, or null if there is none. */
  fun headStreamId(): String? = synchronized(lock) { streams.peekFirst()?.id }

  /**
   * Holds speech where it is, mid-word if need be, so that [release] carries on from exactly there.
   * The streams keep receiving the engine's audio meanwhile. Sounds still play.
   */
  fun hold() {
    synchronized(lock) { held = true }
  }

  /** Carries on playing held speech. */
  fun release() {
    synchronized(lock) {
      held = false
      wake()
    }
  }

  /** Stops the sounds playing now. */
  fun stopClips() {
    synchronized(lock) { clips.clear() }
  }

  private fun wake() {
    idleFrames = 0
    if (thread == null) {
      thread = Thread(::run, "LowLatencyAudio").apply { start() }
      // Separate from the audio thread, so that a stuck track cannot stop it.
      watchdog =
        Executors.newSingleThreadScheduledExecutor().also {
          it.scheduleWithFixedDelay(
            ::checkForStalls,
            WATCHDOG_MS,
            WATCHDOG_MS,
            java.util.concurrent.TimeUnit.MILLISECONDS,
          )
        }
    } else {
      lock.notifyAll()
    }
  }

  /**
   * Drops a speech stream the engine stopped feeding, and gives up on the track if it stopped taking
   * audio, so that speech never waits in silence. Abandoned speech is said the usual way.
   */
  private fun checkForStalls() {
    synchronized(lock) {
      val now = SystemClock.uptimeMillis()
      val head = if (held) null else streams.peekFirst()
      if (head != null && head.isStalled(now)) {
        streams.removeFirst()
        head.abandon()
        LogUtils.w(TAG, "Speech %s got no audio in time", head.id)
      }
      val busy = clips.isNotEmpty() || (streams.isNotEmpty() && !held)
      if (busy && lastWrite != 0L && now - lastWrite > TRACK_STUCK_MS) {
        LogUtils.e(TAG, "Track took no audio for %d ms", now - lastWrite)
        giveUp()
      }
    }
  }

  /** Stops using the low-latency track: everything waiting plays the usual way. */
  private fun giveUp() {
    synchronized(lock) {
      broken = true
      clips.clear()
      while (streams.isNotEmpty()) streams.removeFirst().abandon()
      lock.notifyAll()
    }
  }

  private fun run() {
    Process.setThreadPriority(Process.THREAD_PRIORITY_URGENT_AUDIO)
    val buffer = FloatArray(burstFrames * 2)
    while (!broken) {
      val output = track ?: return
      synchronized(lock) {
        while (
          !broken &&
            clips.isEmpty() &&
            (streams.isEmpty() || held) &&
            idleFrames >= idleLimit()
        ) {
          if (output.playState == AudioTrack.PLAYSTATE_PLAYING) {
            output.pause()
            output.flush()
          }
          lastWrite = 0L
          lock.wait()
        }
        buffer.fill(0f)
        mix(buffer)
        if (clips.isEmpty() && (streams.isEmpty() || held)) idleFrames += burstFrames
      }
      if (broken) return
      if (!play(output) || !writeAll(output, buffer)) {
        // Android stopped taking audio on this track, such as after its output changed. Make a new
        // one, or play the usual way from now on.
        LogUtils.w(TAG, "Track stopped taking audio, making a new one")
        output.release()
        track = createTrack()
        if (track == null) {
          giveUp()
          return
        }
      }
    }
  }

  private fun play(output: AudioTrack): Boolean =
    try {
      if (output.playState != AudioTrack.PLAYSTATE_PLAYING) output.play()
      true
    } catch (e: IllegalStateException) {
      false
    }

  /**
   * Writes [buffer] without blocking for longer than [WRITE_STUCK_MS], and returns whether the track
   * took it all.
   */
  private fun writeAll(output: AudioTrack, buffer: FloatArray): Boolean {
    var offset = 0
    var waitingSince = 0L
    while (offset < buffer.size) {
      val count =
        output.write(buffer, offset, buffer.size - offset, AudioTrack.WRITE_NON_BLOCKING)
      if (count < 0) return false
      val now = SystemClock.uptimeMillis()
      if (count > 0) {
        offset += count
        waitingSince = 0L
        lastWrite = now
      } else {
        if (waitingSince == 0L) {
          waitingSince = now
          if (lastWrite == 0L) lastWrite = now
        } else if (now - waitingSince > WRITE_STUCK_MS) {
          return false
        }
        Thread.sleep(1)
      }
    }
    return true
  }

  private fun idleLimit(): Int = sampleRate * IDLE_MS / 1000

  /** Mixes the next burst of every sound and the speech stream at the head of the queue. */
  private fun mix(out: FloatArray) {
    val iterator = clips.iterator()
    while (iterator.hasNext()) {
      val playing = iterator.next()
      val frames = playing.clip.frames
      val total = frames.size / 2
      for (i in 0 until burstFrames) {
        val position = playing.position
        val index = position.toInt()
        if (index >= total) break
        val next = minOf(index + 1, total - 1)
        val fraction = (position - index).toFloat()
        out[i * 2] +=
          (frames[index * 2] * (1 - fraction) + frames[next * 2] * fraction) * playing.left
        out[i * 2 + 1] +=
          (frames[index * 2 + 1] * (1 - fraction) + frames[next * 2 + 1] * fraction) * playing.right
        playing.position += playing.step
      }
      if (playing.position >= total) iterator.remove()
    }
    val head = if (held) null else streams.peekFirst()
    if (head != null && !head.mixInto(out, burstFrames)) streams.removeFirst()
    for (i in out.indices) out[i] = out[i].coerceIn(-1f, 1f)
  }

  private fun createTrack(): AudioTrack? =
    try {
      val minBytes =
        AudioTrack.getMinBufferSize(
          sampleRate,
          AudioFormat.CHANNEL_OUT_STEREO,
          AudioFormat.ENCODING_PCM_FLOAT,
        )
      AudioTrack.Builder()
        .setAudioAttributes(attributes)
        .setAudioFormat(
          AudioFormat.Builder()
            .setEncoding(AudioFormat.ENCODING_PCM_FLOAT)
            .setSampleRate(sampleRate)
            .setChannelMask(AudioFormat.CHANNEL_OUT_STEREO)
            .build()
        )
        .setPerformanceMode(AudioTrack.PERFORMANCE_MODE_LOW_LATENCY)
        .setTransferMode(AudioTrack.MODE_STREAM)
        .setBufferSizeInBytes(minBytes)
        .build()
        .also {
          // Two bursts queued at most, so new sound waits no longer than that.
          it.setBufferSizeInFrames(burstFrames * QUEUED_BURSTS)
          LogUtils.d(
            TAG,
            "Track at %d Hz, %d frames, low latency=%b",
            sampleRate,
            it.bufferSizeInFrames,
            it.performanceMode == AudioTrack.PERFORMANCE_MODE_LOW_LATENCY,
          )
        }
    } catch (e: RuntimeException) {
      LogUtils.e(TAG, "Cannot create a low-latency track: %s", e)
      null
    }

  companion object {
    private const val TAG = "LowLatencyAudio"
    private const val DEFAULT_RATE = 48_000
    private const val DEFAULT_BURST = 192
    private const val QUEUED_BURSTS = 2
    private const val IDLE_MS = 2_000

    /** How long a stream at the front of the queue waits for audio before it is dropped. */
    const val STALL_MS = 4_000L

    /** How much digital silence from the engine means it silenced the audio itself. */
    const val SILENT_MS = 500L

    /** How often the watchdog looks for stalled speech and a stuck track. */
    private const val WATCHDOG_MS = 250L

    /** How long the audio thread waits for the track to take audio before remaking it. */
    private const val WRITE_STUCK_MS = 300L

    /** How long the track may take no audio before the watchdog gives up on it. */
    private const val TRACK_STUCK_MS = 1_500L

    private val instances = HashMap<Int, LowLatencyAudio>()

    /** Stops every speech stream of every player made so far, without making new ones. */
    @JvmStatic
    fun stopAllStreams() {
      val players = synchronized(instances) { instances.values.toList() }
      for (player in players) player.stopStreams()
    }

    /**
     * The player for [attributes]' usage, one for each kind of audio, or null if the device cannot
     * make a low-latency track, and audio should play the usual way.
     */
    @JvmStatic
    fun get(context: Context, attributes: AudioAttributes): LowLatencyAudio? =
      synchronized(instances) {
        instances
          .getOrPut(attributes.usage) { LowLatencyAudio(context.applicationContext, attributes) }
          .takeIf { it.track != null && !it.broken }
      }

    /** Turns interleaved audio with [channels] channels into stereo frames. */
    @JvmStatic
    fun toStereo(samples: FloatArray, channels: Int): FloatArray =
      when (channels) {
        2 -> samples
        1 -> FloatArray(samples.size * 2) { samples[it / 2] }
        else -> {
          val frames = samples.size / channels
          FloatArray(frames * 2) { i -> samples[(i / 2) * channels + (i % 2)] }
        }
      }
  }
}
