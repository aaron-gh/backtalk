/* Copyright 2026 Backtalk contributors. Licensed under the Apache License, Version 2.0. */
package com.google.android.accessibility.utils.output;

import android.media.AudioAttributes;
import android.os.Build;
import android.os.VibrationAttributes;
import android.os.VibrationEffect;
import android.os.Vibrator;

/** Classifies screen-reader haptics as accessibility feedback, including while another app is open. */
public final class AccessibilityVibration {
  private AccessibilityVibration() {}

  @SuppressWarnings("deprecation")
  public static void play(Vibrator vibrator, VibrationEffect effect) {
    if (Build.VERSION.SDK_INT >= 33) {
      vibrator.vibrate(effect, new VibrationAttributes.Builder()
          .setUsage(VibrationAttributes.USAGE_ACCESSIBILITY).build());
    } else {
      vibrator.vibrate(effect, new AudioAttributes.Builder()
          .setUsage(AudioAttributes.USAGE_ASSISTANCE_ACCESSIBILITY)
          .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build());
    }
  }
}
