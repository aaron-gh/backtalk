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

package com.google.android.accessibility.talkback.customsounds

import com.google.android.accessibility.talkback.directtouch.FakeSharedPreferences
import com.google.android.accessibility.talkback.individualfeedback.IndividualFeedbackSettings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CustomSoundsTest {
  @Test
  fun packEntriesAreNamedAfterTheirSound() {
    assertEquals("focus" to "wav", CustomSounds.parsePackEntry("focus.wav"))
    assertEquals("control_button" to "ogg", CustomSounds.parsePackEntry("Unspoken/Control_Button.OGG"))
    assertEquals("tick" to "mp3", CustomSounds.parsePackEntry("a/b/tick.mp3"))
  }

  @Test
  fun packEntriesThatAreNotSoundsAreLeftOut() {
    assertNull(CustomSounds.parsePackEntry("COPYING"))
    assertNull(CustomSounds.parsePackEntry("README.md"))
    assertNull(CustomSounds.parsePackEntry(".focus.wav"))
    assertNull(CustomSounds.parsePackEntry("__MACOSX/._focus.wav"))
    assertNull(CustomSounds.parsePackEntry("__MACOSX/sounds/focus.wav"))
  }

  @Test
  fun storedFilesKeepTheirSound() {
    val name = CustomSounds.fileName("control_button", "ogg", 1234L)
    assertEquals("control_button", CustomSounds.keyOfFile(name))
    assertNull(CustomSounds.keyOfFile("control_button.ogg"))
  }

  @Test
  fun everySoundCanBeNamedInAPack() {
    for (item in IndividualFeedbackSettings.SOUNDS) {
      assertFalse(item.key, item.key.contains('.'))
      assertEquals(item.key, item.key.lowercase())
      assertEquals(item.key to "wav", CustomSounds.parsePackEntry("${item.key}.wav"))
    }
  }

  @Test
  fun noCustomSoundsByDefault() {
    val prefs = FakeSharedPreferences()
    assertFalse(CustomSounds.hasAny(prefs))
    prefs
      .edit()
      .putStringSet(CustomSounds.PREF_FILES, setOf(CustomSounds.fileName("tick", "wav", 1L)))
      .apply()
    assertTrue(CustomSounds.hasAny(prefs))
    assertEquals(mapOf("tick" to "tick.1.wav"), CustomSounds.fileNames(prefs))
  }

  @Test
  fun extensionsOfKnownFormatsOnly() {
    assertEquals("wav", CustomSounds.extensionOf("Click.WAV"))
    assertEquals("ogg", CustomSounds.extensionOf("my.sound.ogg"))
    assertNull(CustomSounds.extensionOf("notes.txt"))
    assertNull(CustomSounds.extensionOf("noextension"))
    assertNull(CustomSounds.extensionOf(null))
  }
}
