package com.google.android.accessibility.talkback.soundthemes

import android.content.Context
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.util.zip.ZipInputStream
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class ThemeCreationTest {
  private val context: Context get() = RuntimeEnvironment.getApplication()

  @Test fun draftIsHiddenAndDoesNotChangeActiveThemeUntilSaved() {
    val prefs = context.getSharedPreferences("creation-test", Context.MODE_PRIVATE)
    val draft = SoundThemes.createDraft(context, SoundThemeManifest("My sounds", author = "Tester"))
    assertFalse(SoundThemes.installed(context).any { it.id == draft.id })
    assertEquals(SoundThemes.BACKTALK, SoundThemes.activeId(prefs))
    val saved = SoundThemes.saveDraft(context, draft.id)
    assertTrue(SoundThemes.installed(context).any { it.id == saved.id })
    SoundThemes.activate(context, prefs, saved.id)
    assertEquals(saved.id, SoundThemes.activeId(prefs))
    assertEquals("Tester", SoundThemes.active(context, prefs).manifest.author)
  }

  @Test fun duplicateNameDoesNotOverwriteAnExistingTheme() {
    val first = SoundThemes.saveDraft(context,
      SoundThemes.createDraft(context, SoundThemeManifest("Same", author = "First")).id)
    val second = SoundThemes.createDraft(context, SoundThemeManifest("Same", author = "Second"))
    assertThrows(IOException::class.java) { SoundThemes.saveDraft(context, second.id) }
    assertEquals("First", SoundThemes.theme(context, first.id)!!.manifest.author)
    assertNotNull(SoundThemes.theme(context, second.id))
  }

  @Test fun draftExportsMetadataAndChosenSoundsAsImportableZip() {
    val draft = SoundThemes.createDraft(context, SoundThemeManifest(
      "Exported", author = "Tester", description = "My theme", website = "https://example.com"))
    // Storage/export should preserve a selected sound byte-for-byte.
    val sound = byteArrayOf(1, 2, 3, 4)
    java.io.File(draft.directory, SoundThemes.fileName("focus", "wav", 1)).writeBytes(sound)
    val prefs = context.getSharedPreferences("export-test", Context.MODE_PRIVATE)
    val output = ByteArrayOutputStream()
    assertEquals(1, SoundThemes.export(context, prefs, draft.id, output))
    val entries = mutableMapOf<String, ByteArray>()
    ZipInputStream(output.toByteArray().inputStream()).use { zip ->
      while (true) {
        val entry = zip.nextEntry ?: break
        entries[entry.name] = zip.readBytes()
      }
    }
    val manifest = SoundThemeManifest.parse(String(entries.getValue("theme.json")), emptySet(), "")
    assertEquals("My theme", manifest.description)
    assertEquals("Tester", manifest.author)
    assertNull(manifest.license)
    assertArrayEquals(sound, entries.getValue("focus.wav"))
    assertFalse(SoundThemes.installed(context).any { it.id == draft.id })
  }
  @Test fun automaticVibrationsReplaceSoundHapticsAndKeepUnrelatedAuthoredVibrations() {
    val prefs = context.getSharedPreferences("haptic-test", Context.MODE_PRIVATE)
    val draft = SoundThemes.createDraft(context, SoundThemeManifest("Automatic", vibrations = mapOf(
      "focus" to "none", "announcement" to org.json.JSONObject().put("pattern", org.json.JSONArray(listOf(0, 30))))))
    java.io.File(draft.directory, SoundThemes.fileName("focus", "wav", 10)).writeBytes(byteArrayOf(1))
    val audio = com.google.android.accessibility.utils.output.AudioDecoder.Decoded(
      FloatArray(320) { if (it % 20 < 10) 0.6f else -0.6f }, 1, 8000)
    var calls = 0
    assertTrue(SoundThemes.generateVibrations(context, draft.id) { calls++; audio })
    assertFalse(SoundThemes.generateVibrations(context, draft.id) { calls++; audio })
    assertEquals(1, calls)
    val updated = SoundThemes.theme(context, draft.id)!!
    assertTrue(updated.manifest.vibrations["focus"] is org.json.JSONObject)
    assertEquals(30, updated.manifest.vibrationPatterns().getValue("announcement").sum())
    prefs.edit().putString(SoundThemes.PREF_ACTIVE, draft.id).apply()
    SoundThemes.removeSound(context, prefs, SoundThemes.SOUNDS.first { it.key == "focus" })
    val remaining = SoundThemes.theme(context, draft.id)!!.manifest.vibrations
    assertFalse(remaining.containsKey("focus"))
    assertTrue(remaining.containsKey("announcement"))
  }

}
