package com.google.android.accessibility.utils.output

import android.media.AudioAttributes
import android.os.VibrationAttributes
import android.os.VibrationEffect
import android.os.Vibrator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
class AccessibilityVibrationTest {
  @Test @Config(sdk = [35])
  fun modernAndroidReceivesAccessibilityUsage() {
    val vibrator = RuntimeEnvironment.getApplication().getSystemService(Vibrator::class.java)
    AccessibilityVibration.play(vibrator, VibrationEffect.createOneShot(30, 200))
    val shadow = shadowOf(vibrator)
    assertTrue(shadow.isVibrating)
    assertEquals(VibrationAttributes.USAGE_ACCESSIBILITY,
      (shadow.vibrationAttributesFromLastVibration as VibrationAttributes).usage)
  }

  @Test @Config(sdk = [26])
  fun olderAndroidReceivesAccessibilityAudioAttributes() {
    val vibrator = RuntimeEnvironment.getApplication().getSystemService(Vibrator::class.java)
    AccessibilityVibration.play(vibrator, VibrationEffect.createOneShot(30, 200))
    val shadow = shadowOf(vibrator)
    assertTrue(shadow.isVibrating)
    assertEquals(AudioAttributes.USAGE_ASSISTANCE_ACCESSIBILITY,
      shadow.audioAttributesFromLastVibration!!.usage)
  }
}
