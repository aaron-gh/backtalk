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

class MigrationFilesTest {
  @Test
  fun preferenceFilesAreAllowed() {
    assertEquals(
      "shared_prefs" to "com.android.talkback_preferences.xml",
      MigrationFiles.parse("shared_prefs/com.android.talkback_preferences.xml"),
    )
    assertEquals(
      "shared_prefs" to "braille_keyboard.xml",
      MigrationFiles.parse("shared_prefs/braille_keyboard.xml"),
    )
  }

  @Test
  fun onlyTheLabelsDatabaseIsAllowed() {
    assertEquals(
      "databases" to "labelsDatabase.db",
      MigrationFiles.parse("databases/labelsDatabase.db"),
    )
    assertEquals(
      "databases" to "labelsDatabase.db-wal",
      MigrationFiles.parse("databases/labelsDatabase.db-wal"),
    )
    assertNull(MigrationFiles.parse("databases/labelsDatabase.db-shm"))
    assertNull(MigrationFiles.parse("databases/other.db"))
  }

  @Test
  fun otherDirectoriesAndFilesAreRejected() {
    assertNull(MigrationFiles.parse("files/models/model.bin"))
    assertNull(MigrationFiles.parse("files/debug.properties"))
    assertNull(MigrationFiles.parse("shared_prefs/notes.txt"))
    assertNull(MigrationFiles.parse("shared_prefs/.xml"))
    assertNull(MigrationFiles.parse("shared_prefs/prefs.xml.bak"))
    assertNull(MigrationFiles.parse("labelsDatabase.db"))
  }

  @Test
  fun pathTraversalIsRejected() {
    assertNull(MigrationFiles.parse("shared_prefs/../databases/labelsDatabase.db"))
    assertNull(MigrationFiles.parse("../shared_prefs/a.xml"))
    assertNull(MigrationFiles.parse("shared_prefs/.."))
    assertNull(MigrationFiles.parse("shared_prefs/a\\..\\b.xml"))
    assertNull(MigrationFiles.parse("shared_prefs/a\u0000.xml"))
    assertNull(MigrationFiles.parse("/shared_prefs/a.xml"))
    assertNull(MigrationFiles.parse(""))
  }

  @Test
  fun safeNames() {
    assertTrue(MigrationFiles.isSafeName("a.xml"))
    assertFalse(MigrationFiles.isSafeName(""))
    assertFalse(MigrationFiles.isSafeName("."))
    assertFalse(MigrationFiles.isSafeName(".."))
    assertFalse(MigrationFiles.isSafeName("a/b"))
  }
}
