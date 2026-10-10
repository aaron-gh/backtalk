package com.google.android.accessibility.talkback.soundthemes

import com.google.android.accessibility.utils.output.AudioDecoder
import kotlin.math.PI
import kotlin.math.sin
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class SoundVibrationGeneratorTest {
  private fun audio(ms: Int, amplitude: (Double) -> Double = { 0.6 }, frequency: (Double) -> Double = { 300.0 }): AudioDecoder.Decoded {
    val rate = 8000
    var phase = 0.0
    val samples = FloatArray(ms * rate / 1000) { i ->
      val progress = i.toDouble() / (ms * rate / 1000)
      phase += 2 * PI * frequency(progress) / rate
      (sin(phase) * amplitude(progress)).toFloat()
    }
    return AudioDecoder.Decoded(samples, 1, rate)
  }
  private fun generate(audio: AudioDecoder.Decoded) = SoundVibrationGenerator.generate(audio) as JSONObject

  @Test fun shortClickProducesStrongClick() {
    val result = generate(audio(40))
    assertEquals("click", result.getJSONArray("effects").getJSONArray(0).getString(0))
    assertEquals(40, SoundThemeManifest.toPatternOrThrow(result).takeWhile { it >= 0 }.sum())
  }
  @Test fun quietAudioStillProducesStrongVibration() {
    val result = generate(audio(40, amplitude = { 0.0000001 }))
    assertEquals(255, result.getJSONArray("effects").getJSONArray(0).getInt(1))
    assertTrue(result.getJSONArray("strength").getJSONArray(0).getInt(1) >= 200)
  }
  @Test fun risingPitchProducesRisingStrengthAndEffect() {
    val result = generate(audio(400, frequency = { 150 + it * 1200 }))
    val strength = result.getJSONArray("strength")
    assertTrue(strength.getJSONArray(0).getInt(1) < strength.getJSONArray(strength.length() - 1).getInt(1))
    assertFalse(result.has("effects"))
    SoundThemeManifest.toPatternOrThrow(result)
  }
  @Test fun fallingPitchProducesFallingEffect() {
    val result = generate(audio(400, frequency = { 1400 - it * 1200 }))
    assertFalse(result.has("effects"))
    SoundThemeManifest.toPatternOrThrow(result)
  }
  @Test fun fadeFollowsTheLoudnessEnvelope() {
    val result = generate(audio(400, amplitude = { 0.8 * (1 - it) }))
    val strength = result.getJSONArray("strength")
    assertTrue(strength.getJSONArray(0).getInt(1) > strength.getJSONArray(strength.length() - 2).getInt(1))
  }
  @Test fun normalSoundKeepsEveryAudiblePartFirm() {
    val result = generate(audio(400, amplitude = { if (it < 0.1) 0.8 else 0.08 }))
    val strength = result.getJSONArray("strength")
    for (i in 0 until strength.length()) assertTrue(strength.getJSONArray(i).getInt(1) >= 200)
    assertFalse(result.has("effects"))
  }
  @Test fun gapsStaySilentAndStereoDoesNotCancel() {
    val mono = audio(400, amplitude = { if (it in 0.3..0.7) 0.0 else 0.6 })
    val stereo = FloatArray(mono.samples.size * 2) { i -> mono.samples[i / 2] * if (i % 2 == 0) 1 else -1 }
    val result = generate(AudioDecoder.Decoded(stereo, 2, 8000))
    val strength = result.getJSONArray("strength")
    assertEquals(0, strength.getJSONArray(strength.length() / 2).getInt(1))
    assertTrue(strength.getJSONArray(0).getInt(1) > 0)
    SoundThemeManifest.toPatternOrThrow(result)
  }
  @Test fun silenceAndMalformedAudioDoNotCreateSpuriousPulses() {
    assertEquals("none", SoundVibrationGenerator.generate(audio(100, amplitude = { 0.0 })))
    assertNull(SoundVibrationGenerator.generate(AudioDecoder.Decoded(floatArrayOf(Float.NaN), 1, 8000)))
    assertNull(SoundVibrationGenerator.generate(AudioDecoder.Decoded(floatArrayOf(1f), 0, 0)))
  }
  @Test fun longSoundsHaveBoundedDurationAndValidFormat() {
    val result = generate(audio(10000))
    assertTrue(result.getJSONArray("strength").length() <= 32)
    val pattern = SoundThemeManifest.toPatternOrThrow(result)
    assertEquals(1000, pattern.takeWhile { it >= 0 }.sum())
  }
}
