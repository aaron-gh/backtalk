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
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HeldOrientationSettlerTest {
  private val settle = HeldOrientationSettler.SETTLE_MS

  @Test
  fun nothingIsHeldAtFirst() {
    val settler = HeldOrientationSettler()
    assertEquals(Orientation.UNKNOWN, settler.lastHeld)
    assertEquals(-1, settler.lastHeldRotation)
    assertEquals(0L, settler.lastHeldSeenMs)
  }

  @Test
  fun anOrientationCountsOnceItHolds() {
    val settler = HeldOrientationSettler()
    settler.update(Orientation.LANDSCAPE, 3, 1000)
    assertEquals(Orientation.UNKNOWN, settler.lastHeld)
    settler.update(Orientation.LANDSCAPE, 3, 1000 + settle)
    assertEquals(Orientation.LANDSCAPE, settler.lastHeld)
    assertEquals(3, settler.lastHeldRotation)
    assertEquals(1000 + settle, settler.lastHeldSeenMs)
  }

  @Test
  fun update_saysOnlyWhenTheSettledOrientationChanges() {
    val settler = HeldOrientationSettler()
    assertFalse(settler.update(Orientation.LANDSCAPE, 3, 0))
    assertTrue(settler.update(Orientation.LANDSCAPE, 3, settle))
    // Still held the same way, so nothing new to say.
    assertFalse(settler.update(Orientation.LANDSCAPE, 3, 2 * settle))
    // Flickering across 45 degrees for less than the settle time says nothing.
    assertFalse(settler.update(Orientation.PORTRAIT, 0, 2 * settle + 10))
    assertFalse(settler.update(Orientation.LANDSCAPE, 3, 2 * settle + 20))
    assertFalse(settler.update(Orientation.LANDSCAPE, 3, 3 * settle))
    assertFalse(settler.update(Orientation.UNKNOWN, -1, 4 * settle))
    assertFalse(settler.update(Orientation.PORTRAIT, 0, 5 * settle))
    assertTrue(settler.update(Orientation.PORTRAIT, 0, 6 * settle))
  }

  @Test
  fun aMomentaryReadingOnTheWayDownDoesNotCount() {
    val settler = HeldOrientationSettler()
    settler.update(Orientation.LANDSCAPE, 3, 0)
    settler.update(Orientation.LANDSCAPE, 3, settle)
    // Tipped flat, it reads as the other landscape for a moment.
    settler.update(Orientation.REVERSE_LANDSCAPE, 1, settle + 50)
    settler.update(Orientation.UNKNOWN, -1, settle + 100)
    assertEquals(Orientation.LANDSCAPE, settler.lastHeld)
    assertEquals(3, settler.lastHeldRotation)
  }

  @Test
  fun lyingFlatKeepsTheLastOrientation() {
    val settler = HeldOrientationSettler()
    settler.update(Orientation.REVERSE_LANDSCAPE, 1, 0)
    settler.update(Orientation.REVERSE_LANDSCAPE, 1, settle)
    settler.update(Orientation.UNKNOWN, -1, 10_000)
    assertEquals(Orientation.REVERSE_LANDSCAPE, settler.lastHeld)
    assertEquals(settle, settler.lastHeldSeenMs)
  }

  @Test
  fun lyingFlatStartsTheWaitAgain() {
    val settler = HeldOrientationSettler()
    settler.update(Orientation.PORTRAIT, 0, 0)
    settler.update(Orientation.UNKNOWN, -1, 200)
    settler.update(Orientation.PORTRAIT, 0, 250)
    settler.update(Orientation.PORTRAIT, 0, 400)
    assertEquals(Orientation.UNKNOWN, settler.lastHeld)
  }

  @Test
  fun interruptStartsTheWaitAgain() {
    val settler = HeldOrientationSettler()
    settler.update(Orientation.PORTRAIT, 0, 0)
    settler.interrupt()
    settler.update(Orientation.PORTRAIT, 0, settle)
    assertEquals(Orientation.UNKNOWN, settler.lastHeld)
  }

  @Test
  fun stillHeldKeepsUpdatingWhenItWasSeen() {
    val settler = HeldOrientationSettler()
    settler.update(Orientation.PORTRAIT, 0, 0)
    settler.update(Orientation.PORTRAIT, 0, settle)
    settler.update(Orientation.PORTRAIT, 0, 5000)
    assertEquals(5000L, settler.lastHeldSeenMs)
  }
}
