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

package com.google.android.accessibility.brailleime;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.PowerManager;
import android.os.SystemClock;
import android.view.OrientationEventListener;
import com.google.android.accessibility.brailleime.OrientationMonitor.Orientation;
import com.google.android.accessibility.brailleime.input.DotsOrientation;

/**
 * Remembers how the phone was last held up, the whole time the screen is on, like auto-rotate
 * does. With auto-rotate on, a phone laid flat keeps the screen rotation it was last held in, and
 * the braille keyboard follows it. With auto-rotate off, this gives the keyboard the same memory,
 * even of how the phone was held before the keyboard opened.
 */
public final class HeldOrientationTracker {
  private static HeldOrientationTracker instance;

  private final OrientationEventListener listener;
  private final HeldOrientationSettler settler = new HeldOrientationSettler();

  /** Starts tracking while the screen is on. Calling it again does nothing. */
  public static void start(Context context) {
    if (instance != null) {
      return;
    }
    Context appContext = context.getApplicationContext();
    instance = new HeldOrientationTracker(appContext);
    if (appContext.getSystemService(PowerManager.class).isInteractive()) {
      instance.listener.enable();
    }
    IntentFilter filter = new IntentFilter(Intent.ACTION_SCREEN_ON);
    filter.addAction(Intent.ACTION_SCREEN_OFF);
    appContext.registerReceiver(
        new BroadcastReceiver() {
          @Override
          public void onReceive(Context context, Intent intent) {
            if (Intent.ACTION_SCREEN_ON.equals(intent.getAction())) {
              instance.listener.enable();
            } else {
              instance.listener.disable();
              instance.settler.interrupt();
            }
          }
        },
        filter);
  }

  /**
   * How the phone was last held up, or UNKNOWN if it has not been held up since tracking started.
   */
  public static Orientation getLastHeld() {
    return instance == null ? Orientation.UNKNOWN : instance.settler.getLastHeld();
  }

  /**
   * The screen rotation, one of the {@link android.view.Surface} rotations, that auto-rotate would
   * give for how the device was last held up, or -1 if it has not been held up. Unlike auto-rotate,
   * it can be upside down.
   */
  public static int getLastHeldRotation() {
    return instance == null ? -1 : instance.settler.getLastHeldRotation();
  }

  /**
   * When the phone was last seen held up, in {@link SystemClock#uptimeMillis} time, or 0 if never.
   */
  public static long getLastHeldSeenMs() {
    return instance == null ? 0 : instance.settler.getLastHeldSeenMs();
  }

  private HeldOrientationTracker(Context context) {
    listener =
        new OrientationEventListener(context) {
          @Override
          public void onOrientationChanged(int degree) {
            if (degree == ORIENTATION_UNKNOWN) {
              // Lying flat, which keeps the last orientation, as auto-rotate does.
              settler.update(Orientation.UNKNOWN, -1, SystemClock.uptimeMillis());
            } else {
              settler.update(
                  OrientationMonitor.toOrientation(context, degree),
                  DotsOrientation.rotationForDegrees(degree),
                  SystemClock.uptimeMillis());
            }
          }
        };
  }
}
