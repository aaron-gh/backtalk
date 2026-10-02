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

package com.google.android.accessibility.brailleime.input

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test

class DotNumberOrderTest {
  /** Which dot takes the place of each dot when left and right are swapped. */
  private val leftRight = mapOf(1 to 4, 2 to 5, 3 to 6, 4 to 1, 5 to 2, 6 to 3, 7 to 8, 8 to 7)

  /** Which dot takes the place of each dot when top and bottom are swapped. */
  private val topBottom = mapOf(1 to 3, 2 to 2, 3 to 1, 4 to 6, 5 to 5, 6 to 4)
  private val topBottomEightDot =
    mapOf(1 to 7, 2 to 3, 3 to 2, 7 to 1, 4 to 8, 5 to 6, 6 to 5, 8 to 4)

  @Test
  fun order_withoutSwapsIsTheDefault() {
    assertArrayEquals(intArrayOf(1, 2, 3, 4, 5, 6), DotNumberOrder.order(6, false, false, false))
    assertArrayEquals(intArrayOf(3, 2, 1, 4, 5, 6), DotNumberOrder.order(6, true, false, false))
    assertArrayEquals(
      intArrayOf(1, 2, 3, 7, 4, 5, 6, 8),
      DotNumberOrder.order(8, false, false, false),
    )
    assertArrayEquals(
      intArrayOf(7, 3, 2, 1, 4, 5, 6, 8),
      DotNumberOrder.order(8, true, false, false),
    )
  }

  @Test
  fun swaps_moveTheDotsTheSameWayAtEveryTurn() {
    for (dotCount in listOf(6, 8)) {
      for (tabletop in listOf(false, true)) {
        for (turns in 0..3) {
          val plain = screenPositions(dotCount, tabletop, false, false, turns)
          for (reverse in listOf(false, true)) {
            for (flip in listOf(false, true)) {
              val swapped = screenPositions(dotCount, tabletop, reverse, flip, turns)
              val message =
                "dots $dotCount tabletop $tabletop turns $turns reverse $reverse flip $flip"
              assertEquals(message, plain.keys, swapped.keys)
              for (dot in plain.keys) {
                // The swapped dot is where its partner would be, turned just as far: the swap and
                // the turn neither cancel out nor apply twice.
                val partner = swapPartner(dot, dotCount, reverse, flip)
                assertEquals("$message dot $dot", plain.getValue(partner), swapped.getValue(dot))
              }
            }
          }
        }
      }
    }
  }

  private fun swapPartner(dot: Int, dotCount: Int, reverse: Boolean, flip: Boolean): Int {
    var partner = dot
    if (reverse) {
      partner = leftRight.getValue(partner)
    }
    if (flip) {
      partner = (if (dotCount == 8) topBottomEightDot else topBottom).getValue(partner)
    }
    return partner
  }

  /**
   * Where each dot is drawn and touched on a 100 by 200 screen, for dot centers already sorted as
   * [BrailleInputPlane.sortDotCenters] sorts them, with the layout turned this many quarter turns.
   */
  private fun screenPositions(
    dotCount: Int,
    tabletop: Boolean,
    reverse: Boolean,
    flip: Boolean,
    turns: Int,
  ): Map<Int, Pair<Float, Float>> {
    val m = DotsOrientation.layoutToScreen(turns, 100f, 200f)
    val order = DotNumberOrder.order(dotCount, tabletop, reverse, flip)
    return order.indices.associate { i ->
      val x = 10f + 10f * i
      val y = 5f + 3f * i * i
      order[i] to Pair(m[0] * x + m[1] * y + m[2], m[3] * x + m[4] * y + m[5])
    }
  }
}
