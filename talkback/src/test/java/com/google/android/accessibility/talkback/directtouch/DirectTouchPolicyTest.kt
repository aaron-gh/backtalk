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

package com.google.android.accessibility.talkback.directtouch

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DirectTouchPolicyTest {
  private fun base() =
    DirectTouchInput(
      masterEnabled = true,
      appEnabled = true,
      screenInteractive = true,
      foreignAppWindow = false,
      systemUiCoversHalf = false,
      dialogShowing = false,
      textFieldFocused = false,
    )

  @Test
  fun activeWhenNothingIsInTheWay() {
    assertTrue(DirectTouchPolicy.shouldBeActive(base()))
  }

  @Test
  fun eachHandBackConditionTurnsItOff() {
    assertFalse(DirectTouchPolicy.shouldBeActive(base().copy(foreignAppWindow = true)))
    assertFalse(DirectTouchPolicy.shouldBeActive(base().copy(systemUiCoversHalf = true)))
    assertFalse(DirectTouchPolicy.shouldBeActive(base().copy(dialogShowing = true)))
    assertFalse(DirectTouchPolicy.shouldBeActive(base().copy(textFieldFocused = true)))
    assertFalse(DirectTouchPolicy.shouldBeActive(base().copy(screenInteractive = false)))
    assertFalse(DirectTouchPolicy.shouldBeActive(base().copy(masterEnabled = false)))
    assertFalse(DirectTouchPolicy.shouldBeActive(base().copy(appEnabled = false)))
  }

  @Test
  fun inactiveSystemBarIsNotTheShade() {
    // A landscape navigation bar is 730 px tall on a 1080 px display, but inactive until touched.
    assertFalse(DirectTouchPolicy.coversHalfScreen(active = false, windowHeight = 730, displayHeight = 1080))
  }

  @Test
  fun activeTallSystemWindowIsTheShade() {
    assertTrue(DirectTouchPolicy.coversHalfScreen(active = true, windowHeight = 700, displayHeight = 1080))
  }

  @Test
  fun activeShortSystemWindowIsNotTheShade() {
    assertFalse(DirectTouchPolicy.coversHalfScreen(active = true, windowHeight = 120, displayHeight = 1080))
  }
}
