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

package com.google.android.accessibility.talkback.pause

/**
 * Detects three quick presses of a key. Each press must come within [maxGapMs] of the one before,
 * so three presses take at most twice that.
 */
class TriplePressDetector(private val maxGapMs: Long = MAX_GAP_MS) {
  private var count = 0
  private var lastPressTime = 0L

  /** Records a key press at [time], in milliseconds. Returns true on the third quick press. */
  fun onPress(time: Long): Boolean {
    count = if (count > 0 && time - lastPressTime in 0..maxGapMs) count + 1 else 1
    lastPressTime = time
    if (count >= PRESSES) {
      count = 0
      return true
    }
    return false
  }

  fun reset() {
    count = 0
  }

  private companion object {
    const val PRESSES = 3
    const val MAX_GAP_MS = 600L
  }
}
