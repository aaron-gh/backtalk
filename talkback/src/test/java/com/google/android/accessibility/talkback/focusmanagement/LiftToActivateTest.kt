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

package com.google.android.accessibility.talkback.focusmanagement

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LiftToActivateTest {
  @Test
  fun disabledNeverActivates() {
    assertFalse(LiftToActivate.shouldActivate(LiftToActivateMode.DISABLED, true))
    assertFalse(LiftToActivate.shouldActivate(LiftToActivateMode.DISABLED, false))
  }

  @Test
  fun navigationBarModeActivatesOnlyNavigationBarButtons() {
    assertTrue(LiftToActivate.shouldActivate(LiftToActivateMode.NAVIGATION_BAR, true))
    assertFalse(LiftToActivate.shouldActivate(LiftToActivateMode.NAVIGATION_BAR, false))
  }

  @Test
  fun entireScreenModeActivatesEverything() {
    assertTrue(LiftToActivate.shouldActivate(LiftToActivateMode.ENTIRE_SCREEN, true))
    assertTrue(LiftToActivate.shouldActivate(LiftToActivateMode.ENTIRE_SCREEN, false))
  }

  @Test
  fun recognisesSystemNavigationBarButtons() {
    assertTrue(LiftToActivate.isNavigationBarButton("com.android.systemui", "com.android.systemui:id/back"))
    assertTrue(LiftToActivate.isNavigationBarButton("com.android.systemui", "com.android.systemui:id/home"))
    assertTrue(
      LiftToActivate.isNavigationBarButton("com.android.systemui", "com.android.systemui:id/recent_apps")
    )
  }

  @Test
  fun rejectsOtherNodes() {
    assertFalse(LiftToActivate.isNavigationBarButton("com.android.systemui", "com.android.systemui:id/clock"))
    assertFalse(LiftToActivate.isNavigationBarButton("com.example.app", "com.example.app:id/back"))
    assertFalse(LiftToActivate.isNavigationBarButton("com.android.systemui", null))
    assertFalse(LiftToActivate.isNavigationBarButton(null, "com.android.systemui:id/back"))
  }

  @Test
  fun cyclesThroughModesInBothDirections() {
    assertEquals(LiftToActivateMode.NAVIGATION_BAR, LiftToActivateMode.DISABLED.next())
    assertEquals(LiftToActivateMode.ENTIRE_SCREEN, LiftToActivateMode.NAVIGATION_BAR.next())
    assertEquals(LiftToActivateMode.DISABLED, LiftToActivateMode.ENTIRE_SCREEN.next())
    assertEquals(LiftToActivateMode.ENTIRE_SCREEN, LiftToActivateMode.DISABLED.previous())
    assertEquals(LiftToActivateMode.DISABLED, LiftToActivateMode.NAVIGATION_BAR.previous())
  }

  @Test
  fun readsStoredValuesAndFallsBackToDisabled() {
    assertEquals(LiftToActivateMode.NAVIGATION_BAR, LiftToActivateMode.fromPrefValue("1"))
    assertEquals(LiftToActivateMode.ENTIRE_SCREEN, LiftToActivateMode.fromPrefValue("2"))
    assertEquals(LiftToActivateMode.DISABLED, LiftToActivateMode.fromPrefValue(null))
    assertEquals(LiftToActivateMode.DISABLED, LiftToActivateMode.fromPrefValue("nonsense"))
  }
}
