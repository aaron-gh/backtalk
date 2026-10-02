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

/**
 * Edits the colon-separated lists of components that Android keeps in secure settings, such as the
 * enabled accessibility services, the accessibility shortcut targets and the enabled input
 * methods.
 *
 * An entry is a flattened component name, "package/class" or "package/.class". Input method
 * entries also have subtypes after the name, as "package/class;subtype;subtype", which are kept.
 */
object ComponentLists {
  private const val SEPARATOR = ':'
  private const val SUBTYPE_SEPARATOR = ';'

  /** Expands "package/.class" to "package/package.class", so names compare equal. */
  fun normalize(name: String): String {
    val slash = name.indexOf('/')
    if (slash < 0) {
      return name
    }
    val packageName = name.substring(0, slash)
    val className = name.substring(slash + 1)
    return if (className.startsWith(".")) "$packageName/$packageName$className" else name
  }

  private fun entries(value: String?): List<String> =
    value.orEmpty().split(SEPARATOR).filter { it.isNotEmpty() }

  private fun nameOf(entry: String): String = normalize(entry.substringBefore(SUBTYPE_SEPARATOR))

  /** Returns true if the list has the [component]. */
  fun contains(value: String?, component: String): Boolean {
    val target = normalize(component)
    return entries(value).any { nameOf(it) == target }
  }

  /** Returns the list without the [component], or null if it does not have it. */
  fun remove(value: String?, component: String): String? {
    val target = normalize(component)
    val entries = entries(value)
    val kept = entries.filter { nameOf(it) != target }
    return if (kept.size == entries.size) null else kept.joinToString(SEPARATOR.toString())
  }

  /**
   * Returns the list with [oldComponent] replaced by [newComponent] in the same place, keeping any
   * subtypes, or null if the list does not have [oldComponent]. If the list already has
   * [newComponent], the old entry is only removed.
   */
  fun replace(value: String?, oldComponent: String, newComponent: String): String? {
    val oldName = normalize(oldComponent)
    val newName = normalize(newComponent)
    val entries = entries(value)
    if (entries.none { nameOf(it) == oldName }) {
      return null
    }
    val result = mutableListOf<String>()
    val seen = mutableSetOf<String>()
    for (entry in entries) {
      val name = nameOf(entry)
      val replaced =
        if (name == oldName) {
          newComponent + entry.substring(entry.substringBefore(SUBTYPE_SEPARATOR).length)
        } else {
          entry
        }
      if (seen.add(nameOf(replaced))) {
        result.add(replaced)
      }
    }
    return result.joinToString(SEPARATOR.toString())
  }
}
