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

package com.google.android.accessibility.talkback.individualfeedback

import java.io.File
import javax.xml.parsers.DocumentBuilderFactory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.w3c.dom.Element

class SoundVibrationsTest {
  private val patterns: Map<String, Pattern> = loadPatterns()
  private val vibrationNames = IndividualFeedbackSettings.VIBRATIONS.flatMap { it.resourceNames }

  @Test
  fun everySoundHasAVibrationUnlessItShouldNot() {
    val sounds = IndividualFeedbackSettings.SOUNDS.flatMap { it.resourceNames }.toSet()
    assertEquals(sounds, SoundVibrations.PATTERNS.keys + SoundVibrations.WITHOUT_VIBRATION)
    assertTrue(SoundVibrations.PATTERNS.keys.none { it in SoundVibrations.WITHOUT_VIBRATION })
  }

  @Test
  fun brailleSoundsDoNotVibrate() {
    for (sound in listOf("display_connected", "double_beep", "calibration_done")) {
      assertTrue(sound, sound !in SoundVibrations.PATTERNS)
    }
  }

  @Test
  fun theRepeatingLoadingVibrationIsFaintAndShort() {
    val loading = patterns.getValue(SoundVibrations.PATTERNS.getValue("loading"))
    val onTimes = loading.onOff.filterIndexed { i, _ -> i % 2 == 1 }
    assertTrue("too long", onTimes.sum() <= 20)
    val amplitudes = loading.amplitudes.filterIndexed { i, _ -> i % 2 == 1 }
    assertTrue("too strong", amplitudes.all { it <= 100 })
    val scales = loading.premium.filterIndexed { i, _ -> i % 3 == 1 }
    assertTrue("too strong", scales.all { it <= 100 })
  }

  @Test
  fun everySoundVibrationHasASwitch() {
    SoundVibrations.PATTERNS.values.forEach {
      assertTrue("$it has no switch", it in vibrationNames)
    }
  }

  @Test
  fun everySwitchHasAWellFormedPattern() {
    vibrationNames.forEach { name ->
      val pattern = patterns[name]
      assertNotNull("$name is not defined", pattern)
      pattern!!
      assertTrue("$name has no on and off times", pattern.onOff.size >= 2)
      assertEquals("$name must start with an off time", 0, pattern.onOff[0])
      assertTrue("$name has a negative time", pattern.onOff.all { it >= 0 })

      assertTrue("$name has no amplitudes", pattern.amplitudes.isNotEmpty())
      assertEquals("$name amplitudes are not pairs", 0, pattern.amplitudes.size % 2)
      pattern.amplitudes.chunked(2).forEach { (ms, amplitude) ->
        assertTrue("$name has a bad amplitude time", ms > 0)
        assertTrue("$name has a bad amplitude", amplitude in 0..255)
      }

      assertTrue("$name has no primitives", pattern.premium.isNotEmpty())
      assertEquals("$name primitives are not triples", 0, pattern.premium.size % 3)
      pattern.premium.chunked(3).forEach { (primitive, scale, delay) ->
        assertTrue("$name uses primitive $primitive", primitive in SAFE_PRIMITIVES)
        assertTrue("$name has a bad scale", scale in 1..255)
        assertTrue("$name has a bad delay", delay >= 0)
      }
    }
  }

  @Test
  fun noTwoActionsFeelTheSame() {
    val used = vibrationNames.associateWith { patterns.getValue(it) }
    assertUnique("on and off times", used.mapValues { it.value.onOff })
    assertUnique("amplitudes", used.mapValues { it.value.amplitudes })
    assertUnique("primitives", used.mapValues { it.value.premium })
  }

  private fun assertUnique(tier: String, byName: Map<String, List<Int>>) {
    byName.entries
      .groupBy({ it.value }, { it.key })
      .values
      .forEach { names -> assertEquals("These share $tier: $names", 1, names.size) }
  }

  private data class Pattern(val onOff: List<Int>, val amplitudes: List<Int>, val premium: List<Int>)

  private companion object {
    const val AMPLITUDE_SEPARATOR = -9998
    const val SENTINEL_SEPARATOR = -9999

    /** CLICK, QUICK_RISE, SLOW_RISE, QUICK_FALL and TICK. THUD and SPIN are missing on some phones. */
    val SAFE_PRIMITIVES = setOf(1, 4, 5, 6, 7)

    // Unit tests run in the module directory.
    val VALUE_DIRS = listOf("src/main/res/values", "../utils/src/main/res/values")

    fun loadPatterns(): Map<String, Pattern> {
      val builder = DocumentBuilderFactory.newInstance().newDocumentBuilder()
      val result = mutableMapOf<String, Pattern>()
      VALUE_DIRS.flatMap { File(it).listFiles { file -> file.extension == "xml" }!!.toList() }
        .forEach { file ->
          val arrays = builder.parse(file).getElementsByTagName("integer-array")
          for (i in 0 until arrays.length) {
            val array = arrays.item(i) as Element
            val items = array.getElementsByTagName("item")
            val values = (0 until items.length).map { items.item(it).textContent.trim().toInt() }
            result[array.getAttribute("name")] = split(values)
          }
        }
      return result
    }

    fun split(values: List<Int>): Pattern {
      val sentinel = values.indexOf(SENTINEL_SEPARATOR).let { if (it < 0) values.size else it }
      val amplitude = values.indexOf(AMPLITUDE_SEPARATOR).let { if (it < 0) sentinel else it }
      return Pattern(
        onOff = values.subList(0, amplitude),
        amplitudes = if (amplitude < sentinel) values.subList(amplitude + 1, sentinel) else listOf(),
        premium = if (sentinel < values.size) values.subList(sentinel + 1, values.size) else listOf(),
      )
    }
  }
}
