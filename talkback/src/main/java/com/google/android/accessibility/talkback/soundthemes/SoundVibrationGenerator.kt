/* Copyright 2026 Backtalk contributors. Licensed under the Apache License, Version 2.0. */
package com.google.android.accessibility.talkback.soundthemes

import com.google.android.accessibility.utils.output.AudioDecoder
import kotlin.math.roundToInt
import kotlin.math.sqrt
import org.json.JSONArray
import org.json.JSONObject

/** Translates a sound's rhythm, loudness envelope and pitch direction into bounded haptics. */
object SoundVibrationGenerator {
  const val MAX_DURATION_MS = 1000
  private const val MAX_WINDOWS = 32

  fun generate(audio: AudioDecoder.Decoded): Any? {
    val samples = audio.samples
    val channels = audio.channels
    val rate = audio.sampleRate
    if (channels !in 1..2 || rate <= 0 || samples.isEmpty() || samples.size % channels != 0 ||
        samples.any { !it.isFinite() }) return null
    val frames = samples.size / channels
    val duration = (frames * 1000L / rate).coerceIn(35, MAX_DURATION_MS.toLong()).toInt()
    val windows = minOf(MAX_WINDOWS, maxOf(1, duration / 20), frames)
    val energy = DoubleArray(windows)
    val pitch = DoubleArray(windows)
    for (w in 0 until windows) {
      val start = w * frames / windows
      val end = (w + 1) * frames / windows
      var crossings = 0
      var previous = samples[start * channels]
      var squares = 0.0
      for (f in start until end) {
        // Sum channel energy rather than mixing, so opposite-phase stereo never cancels out.
        for (c in 0 until channels) squares += samples[f * channels + c].toDouble().let { it * it }
        val value = samples[f * channels]
        if ((previous < 0 && value >= 0) || (previous >= 0 && value < 0)) crossings++
        previous = value
      }
      energy[w] = sqrt(squares / ((end - start) * channels))
      pitch[w] = crossings.toDouble() * rate / (2 * (end - start))
    }
    val peak = energy.maxOrNull() ?: return null
    if (peak == 0.0) return SoundThemeManifest.NONE
    val active = energy.indices.filter { energy[it] >= peak * 0.005 }
    val first = active.first()
    val last = active.last()
    val edge = maxOf(1, active.size / 3)
    val earlyPitch = active.take(edge).map { pitch[it] }.average()
    val latePitch = active.takeLast(edge).map { pitch[it] }.average()
    val rising = active.size >= 4 && earlyPitch > 40 && latePitch > earlyPitch * 1.35
    val falling = active.size >= 4 && latePitch > 40 && earlyPitch > latePitch * 1.35
    val strength = JSONArray()
    val pattern = mutableListOf(0)
    var on = false
    for (w in 0 until windows) {
      val ms = (w + 1) * duration / windows - w * duration / windows
      val progress = if (last == first) 1.0 else ((w - first).toDouble() / (last - first)).coerceIn(0.0, 1.0)
      val contour = when { rising -> 0.4 + 0.6 * progress; falling -> 1.0 - 0.6 * progress; else -> 1.0 }
      val level = if (energy[w] < peak * 0.005) 0 else
        (200 + sqrt(energy[w] / peak) * 55 * contour).roundToInt().coerceIn(200, 255)
      strength.put(JSONArray().put(ms).put(level))
      val nextOn = level > 0
      if (nextOn == on) pattern[pattern.lastIndex] += ms else { pattern += ms; on = nextOn }
    }
    val result = JSONObject().put("pattern", JSONArray(pattern)).put("strength", strength)
    // Rise/fall primitives can feel faint even at full scale. Use the strong amplitude
    // waveform for those sounds, keeping their envelope and pitch contour.
    val effect = if (duration <= 80 && active.size == windows) "click" else null
    if (effect != null) result.put("effects", JSONArray().put(JSONArray().put(effect).put(255).put(first * duration / windows)))
    return result
  }
}
