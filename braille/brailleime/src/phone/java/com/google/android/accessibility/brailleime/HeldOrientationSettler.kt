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

package com.google.android.accessibility.brailleime

import com.google.android.accessibility.brailleime.OrientationMonitor.Orientation

/**
 * Remembers how a device was last held up, once it has held still for [SETTLE_MS], from readings of
 * its orientation. A device lying flat reads as unknown, which keeps the last orientation, as
 * auto-rotate does.
 */
class HeldOrientationSettler {
  private var candidate = Orientation.UNKNOWN
  private var candidateSinceMs = 0L

  /** How the device was last held up, or UNKNOWN if it has not been. */
  var lastHeld = Orientation.UNKNOWN
    private set

  /** The screen rotation auto-rotate would give for [lastHeld], or -1 if it has not been held up. */
  var lastHeldRotation = -1
    private set

  /** When the device was last seen held up, or 0 if never. */
  var lastHeldSeenMs = 0L
    private set

  /** Takes a reading of how the device is held at this time, with its screen rotation. */
  fun update(orientation: Orientation, rotation: Int, nowMs: Long) {
    if (orientation == Orientation.UNKNOWN) {
      candidate = Orientation.UNKNOWN
      return
    }
    if (orientation != candidate) {
      candidate = orientation
      candidateSinceMs = nowMs
    } else if (nowMs - candidateSinceMs >= SETTLE_MS) {
      lastHeld = orientation
      lastHeldRotation = rotation
      lastHeldSeenMs = nowMs
    }
  }

  /** Forgets a reading that has not settled yet, as when the screen turns off. */
  fun interrupt() {
    candidate = Orientation.UNKNOWN
  }

  companion object {
    /**
     * How long an orientation must hold before it counts. A device being tipped flat can read as
     * another orientation for a moment on the way down.
     */
    const val SETTLE_MS = 300L
  }
}
