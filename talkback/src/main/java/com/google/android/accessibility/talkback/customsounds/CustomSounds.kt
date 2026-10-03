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

import android.content.Context
import android.content.SharedPreferences
import android.media.MediaExtractor
import android.media.MediaFormat
import androidx.core.content.ContextCompat
import com.google.android.accessibility.talkback.individualfeedback.FeedbackItem
import com.google.android.accessibility.talkback.individualfeedback.IndividualFeedbackSettings
import com.google.android.libraries.accessibility.utils.log.LogUtils
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.util.Locale
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/**
 * Sounds the user chose to play in place of Backtalk's own, one for each item in Individual sounds
 * and vibrations. The files are copied into the app's device protected storage, so they play before
 * the device is first unlocked too, and only the items with a custom sound are stored.
 *
 * A sound pack is a ZIP file of sounds named after the items they replace, such as focus.wav or
 * control_button.ogg. Loading one replaces the sounds it has and keeps the others.
 */
object CustomSounds {
  /** The custom sound files, named key.stamp.extension, so that each new sound has a new path. */
  const val PREF_FILES = "pref_custom_sound_files"

  /** Sounds are short, so a larger file is a mistake, and a pack cannot fill the storage. */
  const val MAX_SOUND_BYTES = 5L * 1024 * 1024

  private const val MAX_PACK_ENTRIES = 500
  private const val DIRECTORY = "custom_sounds"
  private const val TAG = "CustomSounds"

  /** The formats Android can play, as file extensions. */
  val EXTENSIONS = setOf("wav", "ogg", "oga", "opus", "mp3", "flac", "m4a", "aac")

  private val ITEMS: Map<String, FeedbackItem>
    get() = IndividualFeedbackSettings.SOUNDS.associateBy { it.key }

  enum class Result {
    OK,
    TOO_LARGE,
    NOT_AUDIO,
    FAILED,
  }

  /** What loading a sound pack did. */
  data class PackResult(
    /** The items whose sounds the pack replaced. */
    val loaded: List<FeedbackItem>,
    /** Sound files in the pack that replace nothing, or cannot be played, by name. */
    val skipped: List<String>,
  )

  /** The custom sound file name of each item that has one, by item key. */
  @JvmStatic
  fun fileNames(prefs: SharedPreferences): Map<String, String> =
    (prefs.getStringSet(PREF_FILES, null) ?: emptySet<String>())
      .mapNotNull { name -> keyOfFile(name)?.let { it to name } }
      .toMap()

  @JvmStatic fun hasAny(prefs: SharedPreferences): Boolean = fileNames(prefs).isNotEmpty()

  /** The custom sound of [item], or null if it plays its own sound. */
  @JvmStatic
  fun file(context: Context, prefs: SharedPreferences, item: FeedbackItem): File? =
    fileNames(prefs)[item.key]?.let { File(directory(context), it) }?.takeIf { it.isFile }

  /**
   * The paths of the custom sounds, by the resource names of the sounds they replace, for the
   * feedback controller. An item with several sounds, like the circle menu, plays its custom sound
   * for all of them.
   */
  @JvmStatic
  fun pathsByResourceName(context: Context, prefs: SharedPreferences): Map<String, String> {
    val items = ITEMS
    val directory = directory(context)
    val paths = HashMap<String, String>()
    for ((key, name) in fileNames(prefs)) {
      val item = items[key] ?: continue
      val file = File(directory, name)
      if (!file.isFile) continue
      for (resourceName in item.resourceNames) {
        paths[resourceName] = file.path
      }
    }
    return paths
  }

  /** Makes [input], a file whose name ends in [extension], the sound of [item]. */
  fun set(
    context: Context,
    prefs: SharedPreferences,
    item: FeedbackItem,
    input: InputStream,
    extension: String,
  ): Result {
    val directory = directory(context)
    val file = File(directory, fileName(item.key, extension, System.currentTimeMillis()))
    val result = copySound(input, file)
    if (result != Result.OK) return result
    store(context, prefs, fileNames(prefs) + (item.key to file.name))
    return Result.OK
  }

  /** Makes [item] play its own sound again. */
  fun remove(context: Context, prefs: SharedPreferences, item: FeedbackItem) {
    store(context, prefs, fileNames(prefs) - item.key)
  }

  /** Makes every item play its own sound again. */
  fun removeAll(context: Context, prefs: SharedPreferences) {
    store(context, prefs, emptyMap())
  }

  /** Loads a sound pack, a ZIP file, from [input]. */
  fun importPack(context: Context, prefs: SharedPreferences, input: InputStream): PackResult {
    val items = ITEMS
    val directory = directory(context)
    val loaded = LinkedHashMap<String, String>()
    val skipped = ArrayList<String>()
    val stamp = System.currentTimeMillis()
    try {
      ZipInputStream(input).use { zip ->
        var entries = 0
        while (true) {
          val entry = zip.nextEntry ?: break
          if (++entries > MAX_PACK_ENTRIES) break
          if (entry.isDirectory) continue
          val name = entry.name.substringAfterLast('/')
          // Files such as a license or readme travel with the sounds, and are left out quietly.
          val parsed = parsePackEntry(entry.name) ?: continue
          val (key, extension) = parsed
          if (key !in items) {
            skipped += name
            continue
          }
          // Each entry gets its own name, in case the pack has two sounds for one item.
          val file = File(directory, fileName(key, extension, stamp + entries))
          if (copySound(zip, file) == Result.OK) {
            loaded[key]?.let { File(directory, it).delete() }
            loaded[key] = file.name
          } else {
            skipped += name
          }
        }
      }
    } catch (e: IOException) {
      LogUtils.w(TAG, "Cannot read sound pack: %s", e)
    }
    if (loaded.isNotEmpty()) {
      store(context, prefs, fileNames(prefs) + loaded)
    }
    val order = IndividualFeedbackSettings.SOUNDS
    return PackResult(order.filter { it.key in loaded }, skipped)
  }

  /** Saves the custom sounds to [output] as a sound pack, and returns how many it has. */
  @Throws(IOException::class)
  fun exportPack(context: Context, prefs: SharedPreferences, output: OutputStream): Int {
    val directory = directory(context)
    var count = 0
    ZipOutputStream(output).use { zip ->
      for (item in IndividualFeedbackSettings.SOUNDS) {
        val name = fileNames(prefs)[item.key] ?: continue
        val file = File(directory, name)
        if (!file.isFile) continue
        zip.putNextEntry(ZipEntry("${item.key}.${name.substringAfterLast('.')}"))
        file.inputStream().use { it.copyTo(zip) }
        zip.closeEntry()
        count++
      }
    }
    return count
  }

  /**
   * Returns the item key and extension of a sound in a pack, from its path in the ZIP file, or
   * null if it is not a sound, such as a license, a hidden file or one in a __MACOSX folder.
   */
  @JvmStatic
  fun parsePackEntry(path: String): Pair<String, String>? {
    if (path.split('/').any { it == "__MACOSX" }) return null
    val name = path.substringAfterLast('/')
    if (name.startsWith(".") || !name.contains('.')) return null
    val extension = name.substringAfterLast('.').lowercase(Locale.ROOT)
    if (extension !in EXTENSIONS) return null
    return name.substringBeforeLast('.').lowercase(Locale.ROOT) to extension
  }

  /** Returns the extension of a sound file called [name], or null if it is not a known format. */
  @JvmStatic
  fun extensionOf(name: String?): String? =
    name?.substringAfterLast('.', "")?.lowercase(Locale.ROOT)?.takeIf { it in EXTENSIONS }

  @JvmStatic
  fun fileName(key: String, extension: String, stamp: Long): String = "$key.$stamp.$extension"

  /** Returns the item key of a stored custom sound file, or null if the name is not one. */
  @JvmStatic
  fun keyOfFile(name: String): String? {
    val parts = name.split('.')
    return if (parts.size == 3 && parts[0].isNotEmpty()) parts[0] else null
  }

  /** Stores [files] as the custom sounds, and deletes the files no longer used. */
  private fun store(context: Context, prefs: SharedPreferences, files: Map<String, String>) {
    prefs.edit().putStringSet(PREF_FILES, files.values.toSet()).apply()
    val kept = files.values.toSet()
    directory(context).listFiles()?.forEach { if (it.name !in kept) it.delete() }
  }

  /** Copies a sound into [file], keeping it only if it is small enough and Android can play it. */
  private fun copySound(input: InputStream, file: File): Result {
    try {
      file.outputStream().use { output ->
        val buffer = ByteArray(64 * 1024)
        var total = 0L
        while (true) {
          val count = input.read(buffer)
          if (count < 0) break
          total += count
          if (total > MAX_SOUND_BYTES) {
            file.delete()
            return Result.TOO_LARGE
          }
          output.write(buffer, 0, count)
        }
      }
    } catch (e: IOException) {
      LogUtils.w(TAG, "Cannot copy sound: %s", e)
      file.delete()
      return Result.FAILED
    }
    if (!isAudio(file)) {
      file.delete()
      return Result.NOT_AUDIO
    }
    return Result.OK
  }

  private fun isAudio(file: File): Boolean {
    val extractor = MediaExtractor()
    return try {
      extractor.setDataSource(file.path)
      (0 until extractor.trackCount).any {
        extractor.getTrackFormat(it).getString(MediaFormat.KEY_MIME)?.startsWith("audio/") == true
      }
    } catch (e: Exception) {
      false
    } finally {
      extractor.release()
    }
  }

  private fun directory(context: Context): File {
    val storage = ContextCompat.createDeviceProtectedStorageContext(context) ?: context
    return File(storage.filesDir, DIRECTORY).apply { mkdirs() }
  }
}
