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

package com.google.android.accessibility.talkback.status

import android.content.SharedPreferences

/**
 * Which status items are spoken, and in what order. The order holds every item, including turned
 * off ones, so that an item keeps its place when it is turned back on. Items added in later
 * versions go at the end and are on.
 */
object StatusSettings {
  private const val PREF_ORDER = "pref_status_item_order"
  private const val PREF_DISABLED = "pref_status_items_disabled"
  private const val SEPARATOR = ","

  fun order(prefs: SharedPreferences): List<StatusItem> {
    val saved = parse(prefs.getString(PREF_ORDER, null))
    return saved + StatusReader.DEFAULT_ITEMS.filterNot { it in saved }
  }

  fun enabledItems(prefs: SharedPreferences): List<StatusItem> {
    val disabled = parse(prefs.getString(PREF_DISABLED, null))
    return order(prefs).filterNot { it in disabled }
  }

  fun isEnabled(prefs: SharedPreferences, item: StatusItem): Boolean =
    item !in parse(prefs.getString(PREF_DISABLED, null))

  fun setEnabled(prefs: SharedPreferences, item: StatusItem, enabled: Boolean) {
    val disabled = parse(prefs.getString(PREF_DISABLED, null)).toMutableList()
    disabled.remove(item)
    if (!enabled) {
      disabled += item
    }
    prefs.edit().putString(PREF_DISABLED, format(disabled)).apply()
  }

  /** Moves an item by [offset] places. Returns its new index, or null if it cannot move. */
  fun move(prefs: SharedPreferences, item: StatusItem, offset: Int): Int? {
    val order = order(prefs).toMutableList()
    val newIndex = order.indexOf(item) + offset
    if (newIndex !in order.indices) {
      return null
    }
    order.remove(item)
    order.add(newIndex, item)
    prefs.edit().putString(PREF_ORDER, format(order)).apply()
    return newIndex
  }

  private fun parse(value: String?): List<StatusItem> =
    value
      .orEmpty()
      .split(SEPARATOR)
      .mapNotNull { name -> StatusItem.entries.firstOrNull { it.name == name } }
      .distinct()

  private fun format(items: List<StatusItem>): String = items.joinToString(SEPARATOR) { it.name }
}
