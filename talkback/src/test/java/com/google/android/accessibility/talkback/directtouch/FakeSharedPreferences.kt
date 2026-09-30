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

import android.content.SharedPreferences

/** An in-memory [SharedPreferences] for unit tests. */
class FakeSharedPreferences : SharedPreferences {
  private val values = mutableMapOf<String, Any?>()

  override fun getAll(): MutableMap<String, *> = values.toMutableMap()

  override fun getString(key: String, defValue: String?): String? =
    values[key] as? String ?: defValue

  @Suppress("UNCHECKED_CAST")
  override fun getStringSet(key: String, defValues: MutableSet<String>?): MutableSet<String>? =
    (values[key] as? Set<String>)?.toMutableSet() ?: defValues

  override fun getInt(key: String, defValue: Int): Int = values[key] as? Int ?: defValue

  override fun getLong(key: String, defValue: Long): Long = values[key] as? Long ?: defValue

  override fun getFloat(key: String, defValue: Float): Float = values[key] as? Float ?: defValue

  override fun getBoolean(key: String, defValue: Boolean): Boolean =
    values[key] as? Boolean ?: defValue

  override fun contains(key: String): Boolean = values.containsKey(key)

  override fun edit(): SharedPreferences.Editor = Editor()

  override fun registerOnSharedPreferenceChangeListener(
    listener: SharedPreferences.OnSharedPreferenceChangeListener
  ) {}

  override fun unregisterOnSharedPreferenceChangeListener(
    listener: SharedPreferences.OnSharedPreferenceChangeListener
  ) {}

  private inner class Editor : SharedPreferences.Editor {
    private val pending = mutableMapOf<String, Any?>()
    private val removed = mutableSetOf<String>()
    private var cleared = false

    override fun putString(key: String, value: String?) = apply { pending[key] = value }

    override fun putStringSet(key: String, values: MutableSet<String>?) = apply {
      pending[key] = values?.toSet()
    }

    override fun putInt(key: String, value: Int) = apply { pending[key] = value }

    override fun putLong(key: String, value: Long) = apply { pending[key] = value }

    override fun putFloat(key: String, value: Float) = apply { pending[key] = value }

    override fun putBoolean(key: String, value: Boolean) = apply { pending[key] = value }

    override fun remove(key: String) = apply { removed += key }

    override fun clear() = apply { cleared = true }

    override fun commit(): Boolean {
      if (cleared) values.clear()
      removed.forEach { values.remove(it) }
      values.putAll(pending)
      return true
    }

    override fun apply() {
      commit()
    }
  }
}
