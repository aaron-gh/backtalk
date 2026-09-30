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

import org.junit.Assert.assertEquals
import org.junit.Test

class DirectTouchAppsTest {
  private fun app(pkg: String, label: String, enabled: Boolean = false) =
    AppEntry(pkg, label, enabled)

  @Test
  fun enabledAppsComeFirst() {
    val sorted = DirectTouchApps.sort(listOf(app("a", "Alpha"), app("z", "Zed", enabled = true)))
    assertEquals(listOf("z", "a"), sorted.map { it.packageName })
  }

  @Test
  fun labelsSortIgnoringCase() {
    val sorted = DirectTouchApps.sort(listOf(app("b", "banana"), app("a", "Apple"), app("c", "Cherry")))
    assertEquals(listOf("a", "b", "c"), sorted.map { it.packageName })
  }

  @Test
  fun equalLabelsSortByPackage() {
    val sorted = DirectTouchApps.sort(listOf(app("b.app", "Game"), app("a.app", "Game")))
    assertEquals(listOf("a.app", "b.app"), sorted.map { it.packageName })
  }
}
