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

package com.google.android.accessibility.talkback.individualfeedback

import android.content.SharedPreferences
import com.google.android.accessibility.talkback.R

/**
 * One sound or vibration the user can turn off on its own. [resourceNames] are resource entry names
 * rather than IDs, because IDs change between builds and the braille sounds live in a module
 * talkback cannot see. An item can cover more than one resource, like the eight radial menu notes.
 */
data class FeedbackItem(val key: String, val title: Int, val resourceNames: List<String>) {
  constructor(key: String, title: Int) : this(key, title, listOf(key))
}

/**
 * Settings for turning single sounds and vibrations off while sound feedback and vibration feedback
 * stay on. Everything is on by default, and only the items the user turned off are stored.
 */
object IndividualFeedbackSettings {
  const val PREF_MUTED_SOUNDS = "pref_individual_muted_sounds"
  const val PREF_MUTED_VIBRATIONS = "pref_individual_muted_vibrations"

  val SOUNDS =
    listOf(
      FeedbackItem("focus", R.string.individual_sound_focus),
      FeedbackItem("focus_actionable", R.string.individual_sound_focus_actionable),
      FeedbackItem("view_entered", R.string.individual_sound_view_entered),
      FeedbackItem("tick", R.string.individual_sound_tick),
      FeedbackItem("long_clicked", R.string.individual_sound_long_clicked),
      FeedbackItem("scroll_tone", R.string.individual_sound_scroll_tone),
      FeedbackItem("chime_up", R.string.individual_sound_chime_up),
      FeedbackItem("chime_down", R.string.individual_sound_chime_down),
      FeedbackItem("complete", R.string.individual_sound_complete),
      FeedbackItem("window_state", R.string.individual_sound_window_state),
      FeedbackItem("gesture_begin", R.string.individual_sound_gesture_begin),
      FeedbackItem("gesture_end", R.string.individual_sound_gesture_end),
      FeedbackItem("typo", R.string.individual_sound_typo),
      FeedbackItem("hyperlink", R.string.individual_sound_hyperlink),
      FeedbackItem("formatting", R.string.individual_sound_formatting),
      FeedbackItem("volume_beep", R.string.individual_sound_volume_beep),
      FeedbackItem("loading", R.string.individual_sound_loading),
      FeedbackItem("browse_mode_on_v4_2", R.string.individual_sound_browse_mode_on),
      FeedbackItem("browse_mode_off_v4_2", R.string.individual_sound_browse_mode_off),
      FeedbackItem(
        "radial_menu",
        R.string.individual_sound_radial_menu,
        (1..8).map { "radial_menu_$it" },
      ),
      FeedbackItem("display_connected", R.string.individual_sound_display_connected),
      FeedbackItem("display_disconnected", R.string.individual_sound_display_disconnected),
      FeedbackItem("double_beep", R.string.individual_sound_double_beep),
      FeedbackItem("turn_on", R.string.individual_sound_turn_on),
      FeedbackItem("turn_off", R.string.individual_sound_turn_off),
      FeedbackItem("calibration_done", R.string.individual_sound_calibration_done),
      FeedbackItem("control_button", R.string.individual_sound_control_button),
      FeedbackItem("control_checkbox", R.string.individual_sound_control_checkbox),
      FeedbackItem("control_radio_button", R.string.individual_sound_control_radio_button),
      FeedbackItem("control_edit_text", R.string.individual_sound_control_edit_text),
      FeedbackItem("control_combo_box", R.string.individual_sound_control_combo_box),
      FeedbackItem("control_slider", R.string.individual_sound_control_slider),
      FeedbackItem("control_link", R.string.individual_sound_control_link),
      FeedbackItem("control_image", R.string.individual_sound_control_image),
      FeedbackItem("control_clock", R.string.individual_sound_control_clock),
      FeedbackItem("control_tab", R.string.individual_sound_control_tab),
      FeedbackItem("control_menu_item", R.string.individual_sound_control_menu_item),
      FeedbackItem("control_list_item", R.string.individual_sound_control_list_item),
      FeedbackItem("control_tree_item", R.string.individual_sound_control_tree_item),
    )

  val VIBRATIONS =
    listOf(
      FeedbackItem("view_hovered_pattern", R.string.individual_vibration_focus),
      FeedbackItem("view_actionable_pattern", R.string.individual_vibration_focus_actionable),
      FeedbackItem("view_focused_or_selected_pattern", R.string.individual_vibration_selected),
      FeedbackItem("view_clicked_pattern", R.string.individual_vibration_clicked),
      FeedbackItem("view_long_clicked_pattern", R.string.individual_vibration_long_clicked),
      FeedbackItem("window_state_pattern", R.string.individual_vibration_window_state),
      FeedbackItem("gesture_detection_repeated_pattern", R.string.individual_vibration_gesture),
      FeedbackItem("typo_pattern", R.string.individual_vibration_typo),
      FeedbackItem("notification_pattern", R.string.individual_vibration_notification),
    )

  fun isSoundOn(prefs: SharedPreferences, item: FeedbackItem): Boolean =
    item.key !in stringSet(prefs, PREF_MUTED_SOUNDS)

  fun setSoundOn(prefs: SharedPreferences, item: FeedbackItem, on: Boolean) =
    setOn(prefs, PREF_MUTED_SOUNDS, item, on)

  fun isVibrationOn(prefs: SharedPreferences, item: FeedbackItem): Boolean =
    item.key !in stringSet(prefs, PREF_MUTED_VIBRATIONS)

  fun setVibrationOn(prefs: SharedPreferences, item: FeedbackItem, on: Boolean) =
    setOn(prefs, PREF_MUTED_VIBRATIONS, item, on)

  /** Resource names of the sounds that are turned off, for the feedback controller. */
  fun mutedSoundResources(prefs: SharedPreferences): Set<String> =
    mutedResources(prefs, PREF_MUTED_SOUNDS, SOUNDS)

  /** Resource names of the vibration patterns that are turned off, for the feedback controller. */
  fun mutedVibrationResources(prefs: SharedPreferences): Set<String> =
    mutedResources(prefs, PREF_MUTED_VIBRATIONS, VIBRATIONS)

  private fun setOn(prefs: SharedPreferences, pref: String, item: FeedbackItem, on: Boolean) {
    val muted = stringSet(prefs, pref)
    prefs.edit().putStringSet(pref, if (on) muted - item.key else muted + item.key).apply()
  }

  private fun mutedResources(
    prefs: SharedPreferences,
    pref: String,
    items: List<FeedbackItem>,
  ): Set<String> {
    val muted = stringSet(prefs, pref)
    return items.filter { it.key in muted }.flatMap { it.resourceNames }.toSet()
  }

  // Copied, because the set the preferences return must not be changed.
  private fun stringSet(prefs: SharedPreferences, pref: String): Set<String> =
    prefs.getStringSet(pref, null)?.toSet() ?: emptySet()
}
