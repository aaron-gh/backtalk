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

package com.google.android.accessibility.talkback.actor.gemini.screenqa

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ScreenTreeFormatTest {
  private val bounds = listOf(10, 20, 30, 40)

  private fun line(
    label: String?,
    traits: List<String> = emptyList(),
    compact: Boolean,
  ): String? = ScreenTree.formatLine(7, "Button", label, traits, bounds, compact)

  @Test
  fun fullLinesKeepTheCoordinatesAndTraits() {
    assertEquals(
      "7: Button \"Send\" (clickable) [10,20,30,40]",
      line("Send", listOf("clickable"), compact = false),
    )
  }

  @Test
  fun fullFormatKeepsUnlabelledNodesThatCanBeActedOn() {
    assertEquals("7: Button (clickable) [10,20,30,40]", line(null, listOf("clickable"), compact = false))
    assertNull(line(null, emptyList(), compact = false))
  }

  @Test
  fun compactLinesHaveNoCoordinates() {
    assertEquals("7: Button \"Send\" (clickable)", line("Send", listOf("clickable"), compact = true))
  }

  @Test
  fun compactSkipsNodesWithoutALabelAndTheScrollableTrait() {
    assertNull(line(null, listOf("clickable"), compact = true))
    assertNull(line("  ", listOf("clickable"), compact = true))
    assertEquals("7: Button \"List\"", line("List", listOf("scrollable"), compact = true))
  }

  @Test
  fun compactCutsLongLabelsSoonerThanTheFullFormat() {
    val label = "x".repeat(200)
    assertEquals(1 + 60, line(label, compact = true)!!.substringAfter('"').substringBeforeLast('"').length + 1)
    assertEquals(120, line(label, compact = false)!!.substringAfter('"').substringBefore('"').length)
  }
}
