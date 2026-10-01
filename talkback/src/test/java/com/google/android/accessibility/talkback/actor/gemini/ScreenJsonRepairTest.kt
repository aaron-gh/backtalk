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

package com.google.android.accessibility.talkback.actor.gemini

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ScreenJsonRepairTest {
  private fun repaired(json: String) = JSONObject(ScreenJsonRepair.repair(json))

  @Test
  fun bareNodeNumbersBecomeObjects() {
    // The shape that Gemma 4 returned on a phone, which failed to parse.
    val result =
      repaired(
        """{"summary":"s","top_images":[],"top_ui_elements":[
          {"label":"Back","type":"button","potentiallyMatchingNodes":[2, 7]}]}"""
      )
    val nodes = result.getJSONArray("top_ui_elements").getJSONObject(0)
      .getJSONArray("potentiallyMatchingNodes")
    assertEquals(2, nodes.getJSONObject(0).getInt("uniqueId"))
    assertEquals(7, nodes.getJSONObject(1).getInt("uniqueId"))
  }

  @Test
  fun aSingleNumberAndNumericStringsAreAccepted() {
    val result =
      repaired(
        """{"ui_elements":[{"potentiallyMatchingNodes":5},{"potentiallyMatchingNodes":["9","x"]}]}"""
      )
    val elements = result.getJSONArray("ui_elements")
    assertEquals(
      5,
      elements.getJSONObject(0).getJSONArray("potentiallyMatchingNodes").getJSONObject(0).getInt("uniqueId"),
    )
    val second = elements.getJSONObject(1).getJSONArray("potentiallyMatchingNodes")
    assertEquals(1, second.length())
    assertEquals(9, second.getJSONObject(0).getInt("uniqueId"))
  }

  @Test
  fun imageObjectsBecomeTheirDescription() {
    val result = repaired("""{"top_images":["plain",{"description":"a dog"},{"other":1}]}""")
    val images = result.getJSONArray("top_images")
    assertEquals(2, images.length())
    assertEquals("plain", images.getString(0))
    assertEquals("a dog", images.getString(1))
  }

  @Test
  fun correctAnswersKeepTheirNodes() {
    val result =
      repaired("""{"top_ui_elements":[{"label":"x","potentiallyMatchingNodes":[{"uniqueId":12}]}]}""")
    val node = result.getJSONArray("top_ui_elements").getJSONObject(0)
      .getJSONArray("potentiallyMatchingNodes").getJSONObject(0)
    assertEquals(12, node.getInt("uniqueId"))
  }

  @Test
  fun elementsThatAreNotObjectsAreDropped() {
    val result = repaired("""{"top_ui_elements":["Back",{"label":"ok"}]}""")
    assertEquals(1, result.getJSONArray("top_ui_elements").length())
  }

  @Test
  fun textThatIsNotJsonComesBackUnchanged() {
    assertEquals("not json", ScreenJsonRepair.repair("not json"))
    assertTrue(ScreenJsonRepair.repair("{\"answer\": \"hi\"}").contains("hi"))
  }

  @Test
  fun aListThatWasNeverClosedIsClosed() {
    // What Qwen3.5 0.8B wrote on a phone: the list is closed with a brace, and nothing follows.
    val json =
      """
      {
      "summary": "A screen.",
      "top_images": [
      "Image 1: a menu."
      }
      """
        .trimIndent()
    val result = repaired(json)
    assertEquals("A screen.", result.getString("summary"))
    assertEquals("Image 1: a menu.", result.getJSONArray("top_images").getString(0))
  }

  @Test
  fun anAnswerThatStopsInTheMiddleIsClosed() {
    val result = repaired("{\"summary\": \"Half a sent")
    assertEquals("Half a sent", result.getString("summary"))
  }

  @Test
  fun trailingCommasAndStrayClosersAreRemoved() {
    assertEquals(
      listOf("a", "b"),
      repaired("{\"top_images\": [\"a\", \"b\",]}]}").getJSONArray("top_images").let { arr ->
        (0 until arr.length()).map { arr.getString(it) }
      },
    )
  }

  @Test
  fun aKeyWithNoValueIsGivenNull() {
    val result = repaired("{\"summary\": \"x\", \"top_images\":")
    assertEquals("x", result.getString("summary"))
  }

  @Test
  fun bracketsInsideTextAreLeftAlone() {
    val result = repaired("{\"summary\": \"Press [OK] or {Cancel}\"}")
    assertEquals("Press [OK] or {Cancel}", result.getString("summary"))
  }

  @Test
  fun fallbackTextPrefersTheValueTheModelWrote() {
    val text = "{\"summary\": \"A chat with \\\"Sam\\\".\", \"top_images\": [oops"
    assertEquals("A chat with \"Sam\".", ScreenJsonRepair.fallbackText(text, "summary"))
  }

  @Test
  fun fallbackTextStripsJsonPunctuationWhenThereIsNoKey() {
    assertEquals("Just some words", ScreenJsonRepair.fallbackText("{ \"Just some words\" ]", "answer"))
  }
}
