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

/**
 * Numbers the dots of the layout, apart from the views so it can be tested. The dot centers are
 * always sorted by where they are, then numbered in this order, so the user's settings to swap the
 * dots only change the numbering. Turning the layout to face the user is done on top of it, by
 * [DotsOrientation.layoutToScreen].
 */
object DotNumberOrder {
  private val SCREEN_AWAY = intArrayOf(1, 2, 3, 4, 5, 6)
  private val SCREEN_AWAY_EIGHT_DOT = intArrayOf(1, 2, 3, 7, 4, 5, 6, 8)
  private val TABLETOP = intArrayOf(3, 2, 1, 4, 5, 6)
  private val TABLETOP_EIGHT_DOT = intArrayOf(7, 3, 2, 1, 4, 5, 6, 8)

  /**
   * The dot numbers for the sorted dot centers, with the user's settings to swap left and right
   * ([reverseDots]) and top and bottom ([flipDotsVertically]) applied.
   */
  @JvmStatic
  fun order(
    dotCount: Int,
    tabletop: Boolean,
    reverseDots: Boolean,
    flipDotsVertically: Boolean,
  ): IntArray {
    var order =
      when (dotCount) {
        8 -> if (tabletop) TABLETOP_EIGHT_DOT else SCREEN_AWAY_EIGHT_DOT
        6 -> if (tabletop) TABLETOP else SCREEN_AWAY
        else -> throw IllegalArgumentException("dotCount should be either 6 or 8.")
      }.copyOf()
    if (reverseDots) {
      order = reverse(order, tabletop)
    }
    if (flipDotsVertically) {
      order = flipVertically(order)
    }
    return order
  }

  /** Swaps the hands, so that dot 1 trades places with dot 4. */
  private fun reverse(order: IntArray, tabletop: Boolean): IntArray {
    val half = order.size / 2
    return IntArray(order.size) { i ->
      if (tabletop) order[order.size - 1 - i] else order[(i + half) % order.size]
    }
  }

  /**
   * Reverses the order within each hand, so that the top and bottom dots of each column trade
   * places. For example, dot 1 trades places with dot 3.
   */
  private fun flipVertically(order: IntArray): IntArray {
    val half = order.size / 2
    return IntArray(order.size) { i ->
      if (i < half) order[half - 1 - i] else order[order.size - 1 - (i - half)]
    }
  }
}
