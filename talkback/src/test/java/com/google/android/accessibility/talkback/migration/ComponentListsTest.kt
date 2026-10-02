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

package com.google.android.accessibility.talkback.migration

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ComponentListsTest {
  private val old = "com.android.talkback/com.google.android.marvin.talkback.TalkBackService"
  private val new = "fyi.quin.backtalk/com.google.android.marvin.talkback.TalkBackService"
  private val other = "com.example/.Reader"

  @Test
  fun shortNamesCompareEqual() {
    assertEquals("com.example/com.example.Reader", ComponentLists.normalize(other))
    assertTrue(ComponentLists.contains("com.example/com.example.Reader", other))
  }

  @Test
  fun containsFindsTheService() {
    assertTrue(ComponentLists.contains("$other:$old", old))
    assertFalse(ComponentLists.contains(other, old))
    assertFalse(ComponentLists.contains(null, old))
    assertFalse(ComponentLists.contains("", old))
  }

  @Test
  fun removeKeepsTheOthers() {
    assertEquals("$other:$new", ComponentLists.remove("$other:$old:$new", old))
    assertEquals("", ComponentLists.remove(old, old))
    assertNull(ComponentLists.remove("$other:$new", old))
  }

  @Test
  fun replaceKeepsThePlace() {
    assertEquals("$other:$new", ComponentLists.replace("$other:$old", old, new))
    assertEquals(new, ComponentLists.replace(old, old, new))
    assertNull(ComponentLists.replace(other, old, new))
    assertNull(ComponentLists.replace(null, old, new))
  }

  @Test
  fun replaceDoesNotListTheNewServiceTwice() {
    assertEquals("$new:$other", ComponentLists.replace("$new:$other:$old", old, new))
  }

  @Test
  fun replaceKeepsInputMethodSubtypes() {
    val oldIme = "com.android.talkback/com.google.android.accessibility.brailleime.BrailleIme"
    val newIme = "fyi.quin.backtalk/com.google.android.accessibility.brailleime.BrailleIme"
    val latin = "com.android.inputmethod.latin/.LatinIME;123;456"
    assertEquals(
      "$latin:$newIme;-789",
      ComponentLists.replace("$latin:$oldIme;-789", oldIme, newIme),
    )
  }
}
