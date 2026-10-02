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
 * Which files carry the settings between the old and the new app ID.
 *
 * Paths have the form "<directory>/<name>". Settings are the preference files in device-protected
 * storage, and custom labels are a database in credential-protected storage. On-device AI models
 * are not carried over, since they are large and the new app downloads them again.
 */
object MigrationFiles {
  /** Preference files in device-protected storage. */
  const val PREFS_DIR = "shared_prefs"

  /** The custom labels database in credential-protected storage. */
  const val DATABASES_DIR = "databases"

  /** The name of the custom labels database, from LabelProvider. */
  const val LABELS_DATABASE = "labelsDatabase.db"

  private const val PREFS_SUFFIX = ".xml"

  /** The custom labels database and its write-ahead log. */
  val DATABASE_FILES = listOf(LABELS_DATABASE, "$LABELS_DATABASE-wal")

  /** Returns true if [name] is a plain file name that cannot leave its directory. */
  fun isSafeName(name: String): Boolean =
    name.isNotEmpty() &&
      name != "." &&
      name != ".." &&
      name.none { it == '/' || it == '\\' || it == '\u0000' }

  /**
   * Splits an exported path into its directory and file name, or returns null if the path is not
   * one that may be exported.
   */
  fun parse(path: String): Pair<String, String>? {
    val parts = path.split('/')
    if (parts.size != 2) {
      return null
    }
    val (directory, name) = parts
    if (!isSafeName(name)) {
      return null
    }
    val allowed =
      when (directory) {
        PREFS_DIR -> name.endsWith(PREFS_SUFFIX) && name.length > PREFS_SUFFIX.length
        DATABASES_DIR -> name in DATABASE_FILES
        else -> false
      }
    return if (allowed) directory to name else null
  }

  /** Joins a directory and file name into an exported path. */
  fun path(directory: String, name: String): String = "$directory/$name"
}
