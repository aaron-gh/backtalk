package com.google.android.accessibility.talkback.soundthemes

import com.google.android.accessibility.talkback.updatetasks.*
import org.junit.Assert.assertEquals
import org.junit.Test

class Build600VersionTest {
  @Test fun buildNumberDoesNotCrashMigration() {
    assertEquals(Version(600, 0, 0), convertToVersion("600"))
    assertEquals(Versions.VERSION_UNKNOWN, convertToVersion("TfPu_release_16_2-2026_10_10"))
  }
  @Test fun dottedVersionsAndInvalidNamesAreSafe() {
    assertEquals(Version(16, 2, 1), convertToVersion("16.2.1-debug"))
    assertEquals(Versions.VERSION_UNKNOWN, convertToVersion("16.2"))
    assertEquals(Versions.VERSION_UNKNOWN, convertToVersion("TfPu_release_16_2"))
    assertEquals(Versions.VERSION_FIRST_TIME_USER, convertToVersion(null))
  }
}
