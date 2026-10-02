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

import org.junit.Assert.assertEquals
import org.junit.Test

class FlatTurnCounterTest {
  private fun phone() = FlatTurnCounter()

  private fun tablet() = FlatTurnCounter().apply { setQuarterTurns(true) }

  /** Feeds headings in order, and returns the quarter turns each one reported. */
  private fun FlatTurnCounter.feed(vararg headings: Float) = headings.map { onHeading(it) }

  @Test
  fun firstHeadingIsTheStart() {
    assertEquals(listOf(0), phone().feed(170f))
  }

  @Test
  fun phone_nudgesDoNotCount() {
    assertEquals(listOf(0, 0, 0, 0), phone().feed(0f, 40f, 119f, -100f))
  }

  @Test
  fun phone_aHalfTurnCountsTwoThirdsOfTheWay() {
    assertEquals(listOf(0, 0, 2), phone().feed(0f, 100f, 121f))
    assertEquals(listOf(0, -2), phone().feed(0f, -125f))
  }

  @Test
  fun phone_turnedOnlyOncePerHalfTurn() {
    assertEquals(listOf(0, 2, 0, 0), phone().feed(0f, 125f, 170f, 185f))
  }

  @Test
  fun phone_turningBackCountsFromTheTurnedDirection() {
    // Turned around, then back to roughly where it started: both count, though the first was only
    // noticed at 130 degrees.
    assertEquals(listOf(0, 2, -2), phone().feed(0f, 130f, 10f))
  }

  @Test
  fun phone_acrossTheSouthernWrap() {
    // From 170 to -60 degrees is 130 degrees clockwise, through south.
    assertEquals(listOf(0, 2), phone().feed(170f, -60f))
    assertEquals(listOf(0, -2), phone().feed(-170f, 60f))
  }

  @Test
  fun tablet_countsQuarterTurns() {
    assertEquals(listOf(0, 0, 1), tablet().feed(0f, 50f, 61f))
    assertEquals(listOf(0, -1), tablet().feed(0f, -65f))
  }

  @Test
  fun tablet_aFullTurnIsFourQuarters() {
    val turns = tablet().feed(0f, 90f, 180f, -90f, 0f)
    assertEquals(listOf(0, 1, 1, 1, 1), turns)
  }

  @Test
  fun tablet_turningBackCounts() {
    assertEquals(listOf(0, 1, -1), tablet().feed(0f, 65f, 25f))
  }

  @Test
  fun reset_takesTheNextHeadingAsTheStart() {
    val counter = phone()
    counter.feed(0f, 100f)
    counter.reset()
    assertEquals(listOf(0, 0), counter.feed(100f, 200f))
  }

  @Test
  fun switchingStepStartsAgain() {
    val counter = phone()
    counter.feed(0f)
    counter.setQuarterTurns(true)
    assertEquals(listOf(0, 1), counter.feed(80f, 150f))
  }

  @Test
  fun angleBetween_isSignedAndWraps() {
    assertEquals(10f, FlatTurnCounter.angleBetween(10f, 0f), 0.001f)
    assertEquals(-10f, FlatTurnCounter.angleBetween(-10f, 0f), 0.001f)
    assertEquals(20f, FlatTurnCounter.angleBetween(-170f, 170f), 0.001f)
    assertEquals(-20f, FlatTurnCounter.angleBetween(170f, -170f), 0.001f)
  }
}
