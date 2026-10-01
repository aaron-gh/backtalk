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

package com.google.android.accessibility.talkback.actor.gemini.local

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DownloadProgressTest {
  @Test
  fun percentIsClampedAndSafeForAnUnknownTotal() {
    assertEquals(0, DownloadProgress.percent(5, 0))
    assertEquals(34, DownloadProgress.percent(340, 1000))
    assertEquals(100, DownloadProgress.percent(2000, 1000))
  }

  @Test
  fun theFirstSpeedReadingIsUsedAsIs() {
    assertEquals(1000, DownloadProgress.smoothedSpeed(0, 500, 500))
  }

  @Test
  fun laterReadingsMoveTheSpeedGradually() {
    // Previous 1000 B/s, latest reading 2000 B/s: 0.7 * 1000 + 0.3 * 2000 = 1300.
    assertEquals(1300, DownloadProgress.smoothedSpeed(1000, 1000, 500))
  }

  @Test
  fun aZeroIntervalKeepsThePreviousSpeed() {
    assertEquals(1000, DownloadProgress.smoothedSpeed(1000, 500, 0))
  }

  @Test
  fun secondsLeftRoundsUpAndNeedsASpeed() {
    assertNull(DownloadProgress.secondsLeft(0, 1000, 0))
    assertEquals(3L, DownloadProgress.secondsLeft(100, 1000, 300))
    assertEquals(0L, DownloadProgress.secondsLeft(1000, 1000, 300))
  }

  @Test
  fun milestonesAreQuarters() {
    assertEquals(0, DownloadProgress.milestone(10, 100))
    assertEquals(25, DownloadProgress.milestone(49, 100))
    assertEquals(50, DownloadProgress.milestone(50, 100))
    assertEquals(100, DownloadProgress.milestone(100, 100))
  }
}
