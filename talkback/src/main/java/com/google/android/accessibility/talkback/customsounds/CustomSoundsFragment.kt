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

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.SharedPreferences
import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import android.webkit.MimeTypeMap
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.core.view.ViewCompat
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat.AccessibilityActionCompat
import androidx.core.view.accessibility.AccessibilityViewCommand
import androidx.preference.Preference
import androidx.preference.PreferenceCategory
import androidx.preference.PreferenceViewHolder
import com.google.android.accessibility.material.preference.AccessibilitySuitePreference
import com.google.android.accessibility.talkback.R
import com.google.android.accessibility.talkback.controlsounds.ControlSounds
import com.google.android.accessibility.talkback.individualfeedback.FeedbackItem
import com.google.android.accessibility.talkback.individualfeedback.IndividualFeedbackSettings
import com.google.android.accessibility.talkback.individualfeedback.SoundPreview
import com.google.android.accessibility.talkback.preference.base.TalkbackBaseFragment
import com.google.android.accessibility.utils.SharedPreferencesUtils
import java.io.IOException
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

/**
 * A row for each of Backtalk's sounds, where the user can choose a sound file to play in its place,
 * and entries to load and save sound packs. Files are copied in on a background thread.
 */
class CustomSoundsFragment : TalkbackBaseFragment() {
  private lateinit var prefs: SharedPreferences
  private val soundPreview = SoundPreview()
  private val executor: ExecutorService = Executors.newSingleThreadExecutor()
  private val rows = ArrayList<SoundRow>()
  private var savePackPreference: Preference? = null
  private var resetPreference: Preference? = null

  // The item a sound file is being chosen for, kept in case the screen is recreated meanwhile.
  private var choosingFor: String? = null

  private val chooseSound: ActivityResultLauncher<Array<String>> =
    registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
      val item = IndividualFeedbackSettings.SOUNDS.firstOrNull { it.key == choosingFor }
      choosingFor = null
      if (uri != null && item != null) setSound(item, uri)
    }

  private val loadPack: ActivityResultLauncher<Array<String>> =
    registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
      if (uri != null) loadPack(uri)
    }

  private val savePack: ActivityResultLauncher<String> =
    registerForActivityResult(ActivityResultContracts.CreateDocument(MIME_ZIP)) { uri ->
      if (uri != null) savePack(uri)
    }

  public override fun getTitle(): CharSequence = getText(R.string.title_pref_custom_sounds)

  override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
    val context = requireContext()
    prefs = SharedPreferencesUtils.getSharedPreferences(context)
    choosingFor = savedInstanceState?.getString(STATE_CHOOSING_FOR)
    val screen = preferenceManager.createPreferenceScreen(context)
    preferenceScreen = screen

    screen.addPreference(
      action(
        context,
        R.string.title_pref_custom_sounds_load_pack,
        R.string.summary_pref_custom_sounds_load_pack,
      ) {
        launch(loadPack, PACK_TYPES)
      }
    )
    savePackPreference =
      action(
          context,
          R.string.title_pref_custom_sounds_save_pack,
          R.string.summary_pref_custom_sounds_save_pack,
        ) {
          launch(savePack, getString(R.string.custom_sounds_pack_file_name))
        }
        .also { screen.addPreference(it) }
    resetPreference =
      action(context, R.string.title_pref_custom_sounds_reset, 0) { confirmReset() }
        .also { screen.addPreference(it) }

    val sounds =
      PreferenceCategory(context).apply {
        setTitle(R.string.custom_sounds_category)
        isIconSpaceReserved = false
      }
    screen.addPreference(sounds)
    rows.clear()
    for (item in IndividualFeedbackSettings.SOUNDS) {
      val row = SoundRow(context, item, preview = { soundPreview.play(context, prefs, item) })
      row.setOnPreferenceClickListener {
        showChoices(item)
        true
      }
      sounds.addPreference(row)
      rows += row
    }
    refresh()
  }

  override fun onSaveInstanceState(outState: Bundle) {
    super.onSaveInstanceState(outState)
    outState.putString(STATE_CHOOSING_FOR, choosingFor)
  }

  override fun onPause() {
    super.onPause()
    soundPreview.stop()
  }

  override fun onDestroy() {
    super.onDestroy()
    executor.shutdown()
  }

  private fun action(context: Context, title: Int, summary: Int, onClick: () -> Unit) =
    AccessibilitySuitePreference(context).apply {
      setTitle(title)
      if (summary != 0) setSummary(summary)
      isPersistent = false
      isIconSpaceReserved = false
      setOnPreferenceClickListener {
        onClick()
        true
      }
    }

  /** Shows which sounds are the user's, and offers saving and resetting only when some are. */
  private fun refresh() {
    val context = context ?: return
    val custom = CustomSounds.fileNames(prefs)
    for (row in rows) {
      row.summary =
        context.getString(
          when {
            row.item.key in custom -> R.string.custom_sound_summary_custom
            isControlSound(row.item) -> R.string.custom_sound_summary_none
            else -> R.string.custom_sound_summary_default
          }
        )
    }
    savePackPreference?.isEnabled = custom.isNotEmpty()
    resetPreference?.isEnabled = custom.isNotEmpty()
  }

  private fun showChoices(item: FeedbackItem) {
    val context = requireContext()
    val hasCustom = CustomSounds.file(context, prefs, item) != null
    val choices = ArrayList<Pair<Int, () -> Unit>>()
    choices +=
      R.string.custom_sound_choose to
        {
          choosingFor = item.key
          launch(chooseSound, SOUND_TYPES)
        }
    if (hasCustom || !isControlSound(item)) {
      choices += R.string.custom_sound_preview to { soundPreview.play(context, prefs, item) }
    }
    if (hasCustom) {
      val label =
        if (isControlSound(item)) R.string.custom_sound_remove
        else R.string.custom_sound_use_default
      choices +=
        label to
          {
            CustomSounds.remove(context, prefs, item)
            refresh()
          }
    }
    AlertDialog.Builder(context)
      .setTitle(getString(R.string.custom_sound_dialog_title, getString(item.title), item.key))
      .setItems(choices.map { getString(it.first) }.toTypedArray()) { _, which ->
        choices[which].second()
      }
      .setNegativeButton(android.R.string.cancel, null)
      .show()
  }

  private fun confirmReset() {
    val context = requireContext()
    AlertDialog.Builder(context)
      .setMessage(R.string.custom_sounds_reset_confirm)
      .setPositiveButton(R.string.custom_sounds_reset_button) { _, _ ->
        CustomSounds.removeAll(context, prefs)
        refresh()
      }
      .setNegativeButton(android.R.string.cancel, null)
      .show()
  }

  private fun <I> launch(launcher: ActivityResultLauncher<I>, input: I) {
    try {
      launcher.launch(input)
    } catch (e: ActivityNotFoundException) {
      // Watches have no document picker.
      choosingFor = null
      showMessage(getString(R.string.custom_sound_no_picker))
    }
  }

  private fun setSound(item: FeedbackItem, uri: Uri) {
    val context = requireContext().applicationContext
    val extension = extensionOf(context, uri)
    inBackground(
      work = {
        try {
          context.contentResolver.openInputStream(uri)?.use {
            CustomSounds.set(context, prefs, item, it, extension)
          } ?: CustomSounds.Result.FAILED
        } catch (e: IOException) {
          CustomSounds.Result.FAILED
        } catch (e: SecurityException) {
          CustomSounds.Result.FAILED
        }
      },
      done = { result ->
        when (result) {
          CustomSounds.Result.OK -> soundPreview.play(context, prefs, item)
          CustomSounds.Result.TOO_LARGE -> showMessage(getString(R.string.custom_sound_too_large))
          CustomSounds.Result.NOT_AUDIO -> showMessage(getString(R.string.custom_sound_not_audio))
          CustomSounds.Result.FAILED -> showMessage(getString(R.string.custom_sound_failed))
        }
      },
    )
  }

  private fun loadPack(uri: Uri) {
    val context = requireContext().applicationContext
    inBackground(
      work = {
        try {
          context.contentResolver.openInputStream(uri)?.use {
            CustomSounds.importPack(context, prefs, it)
          }
        } catch (e: IOException) {
          null
        } catch (e: SecurityException) {
          null
        }
      },
      done = { result ->
        if (result == null) {
          showMessage(getString(R.string.custom_sound_failed))
        } else if (result.loaded.isEmpty()) {
          showMessage(getString(R.string.custom_sounds_pack_empty))
        } else {
          val loaded = result.loaded.size
          var message =
            resources.getQuantityString(R.plurals.custom_sounds_pack_loaded, loaded, loaded)
          if (result.skipped.isNotEmpty()) {
            message +=
              "\n\n" +
                getString(R.string.custom_sounds_pack_skipped, result.skipped.joinToString(", "))
          }
          showMessage(message)
        }
      },
    )
  }

  private fun savePack(uri: Uri) {
    val context = requireContext().applicationContext
    inBackground(
      work = {
        try {
          context.contentResolver.openOutputStream(uri)?.use {
            CustomSounds.exportPack(context, prefs, it)
          }
        } catch (e: IOException) {
          null
        } catch (e: SecurityException) {
          null
        }
      },
      done = { count ->
        showMessage(
          if (count == null) getString(R.string.custom_sounds_pack_save_failed)
          else resources.getQuantityString(R.plurals.custom_sounds_pack_saved, count, count)
        )
      },
    )
  }

  /** Runs [work] off the main thread, then [done] with its result if the screen is still open. */
  private fun <T> inBackground(work: () -> T, done: (T) -> Unit) {
    val activity = requireActivity()
    executor.execute {
      val result = work()
      activity.runOnUiThread {
        if (isAdded) {
          refresh()
          done(result)
        }
      }
    }
  }

  private fun showMessage(message: CharSequence) {
    AlertDialog.Builder(requireContext())
      .setMessage(message)
      .setPositiveButton(android.R.string.ok, null)
      .show()
  }

  /** Returns the extension of a chosen file, from its name, or else from its type. */
  private fun extensionOf(context: Context, uri: Uri): String {
    val name =
      try {
        context.contentResolver
          .query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
          ?.use { if (it.moveToFirst()) it.getString(0) else null }
      } catch (e: RuntimeException) {
        null
      }
    CustomSounds.extensionOf(name)?.let {
      return it
    }
    val type = context.contentResolver.getType(uri)
    val fromType = type?.let { MimeTypeMap.getSingleton().getExtensionFromMimeType(it) }
    return CustomSounds.extensionOf("sound.$fromType") ?: DEFAULT_EXTENSION
  }

  /** A row for one sound, with a Preview action that plays it. */
  private class SoundRow(context: Context, val item: FeedbackItem, private val preview: () -> Unit) :
    AccessibilitySuitePreference(context) {
    init {
      key = "pref_custom_sound_${item.key}"
      isPersistent = false
      isIconSpaceReserved = false
      setTitle(item.title)
    }

    override fun onBindViewHolder(holder: PreferenceViewHolder) {
      super.onBindViewHolder(holder)
      // Rows are reused for other sounds, so every bind replaces the action.
      val label = context.getString(R.string.custom_sound_preview)
      ViewCompat.replaceAccessibilityAction(
        holder.itemView,
        AccessibilityActionCompat(ACTION_PREVIEW, label),
        label,
        AccessibilityViewCommand { _, _ ->
          preview()
          true
        },
      )
    }
  }

  private companion object {
    val ACTION_PREVIEW = R.id.accessibility_custom_action_0
    const val STATE_CHOOSING_FOR = "choosing_for"
    const val MIME_ZIP = "application/zip"
    const val DEFAULT_EXTENSION = "wav"
    val SOUND_TYPES = arrayOf("audio/*", "application/ogg")
    // File managers do not all call ZIP files by the same type.
    val PACK_TYPES =
      arrayOf(MIME_ZIP, "application/x-zip-compressed", "application/octet-stream")

    fun isControlSound(item: FeedbackItem): Boolean = item.key in ControlSounds.SOUNDS
  }
}
