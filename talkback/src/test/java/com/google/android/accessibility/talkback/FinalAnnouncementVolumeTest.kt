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

package com.google.android.accessibility.talkback

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FinalAnnouncementVolumeTest {
  @Test
  fun twentyDecibelsQuieterIsATenthOfTheVolume() {
    assertEquals(0.1f, FinalAnnouncementVolume.gainFromDecibels(-40f, -20f), 1e-4f)
  }

  @Test
  fun neverLouderThanTheMediaVolume() {
    assertEquals(1f, FinalAnnouncementVolume.gainFromDecibels(-10f, -20f), 0f)
    assertEquals(1f, FinalAnnouncementVolume.gainFromDecibels(-20f, -20f), 0f)
  }

  @Test
  fun quietestAccessibilityStepsAreMuchQuieterThanTheSliderGuess() {
    // A typical phone: the lowest accessibility step is about -50 dB and half media volume about
    // -20 dB, 30 dB apart, while the slider guess only lowers the speech about 22 dB.
    val measured = FinalAnnouncementVolume.gainFromDecibels(-50f, -20f)
    val guessed = FinalAnnouncementVolume.sliderGain(1f / 15f, 0.5f)
    assertEquals(0.0316f, measured, 1e-4f)
    assertTrue(measured < guessed / 2f)
  }

  @Test
  fun sliderGuessKeepsTalkBackBehavior() {
    assertEquals(1f, FinalAnnouncementVolume.sliderGain(0.6f, 0.4f), 0f)
    assertEquals(0.1f, FinalAnnouncementVolume.sliderGain(0.2f, 0.6f), 1e-4f)
  }
}
