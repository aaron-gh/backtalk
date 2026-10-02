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

import android.content.Context

/**
 * The vibration that goes with each sound. Every sound has its own vibration, and it plays even
 * when sound feedback is off, so each action can be told apart by touch alone. The patterns are in
 * vibration_backtalk.xml.
 */
object SoundVibrations {
  /** Vibration pattern resource names, by sound resource names. */
  val PATTERNS: Map<String, String> =
    mapOf(
      "focus" to "view_hovered_pattern",
      "focus_actionable" to "view_actionable_pattern",
      "view_entered" to "view_entered_pattern",
      "tick" to "view_clicked_pattern",
      "long_clicked" to "view_long_clicked_pattern",
      "scroll_tone" to "scroll_pattern",
      "chime_up" to "list_entered_pattern",
      "chime_down" to "list_exited_pattern",
      "complete" to "complete_pattern",
      "window_state" to "window_state_pattern",
      "gesture_begin" to "gesture_detection_repeated_pattern",
      "gesture_end" to "gesture_end_pattern",
      "typo" to "typo_pattern",
      "hyperlink" to "hyperlink_pattern",
      "formatting" to "formatting_pattern",
      "volume_beep" to "volume_pattern",
      "loading" to "loading_pattern",
      "browse_mode_on_v4_2" to "browse_mode_on_pattern",
      "browse_mode_off_v4_2" to "browse_mode_off_pattern",
      "display_connected" to "braille_display_connected_pattern",
      "display_disconnected" to "braille_display_disconnected_pattern",
      "double_beep" to "braille_command_failed_pattern",
      "turn_on" to "braille_auto_scroll_on_pattern",
      "turn_off" to "braille_auto_scroll_off_pattern",
      "calibration_done" to "braille_calibrated_pattern",
    ) +
      (1..8).associate { "radial_menu_$it" to "radial_menu_${it}_pattern" } +
      // Control sounds stand in for the focus sounds, so they keep the focus vibrations: things
      // that can be activated feel actionable, and images and clocks feel like any other item.
      listOf(
          "control_button",
          "control_checkbox",
          "control_radio_button",
          "control_edit_text",
          "control_combo_box",
          "control_slider",
          "control_link",
          "control_tab",
          "control_menu_item",
          "control_list_item",
          "control_tree_item",
        )
        .associateWith { "view_actionable_pattern" } +
      listOf("control_image", "control_clock").associateWith { "view_hovered_pattern" }

  /**
   * Vibration pattern resource IDs, by sound resource names, for the feedback controller. By name,
   * because the braille sounds are in a module whose R class talkback cannot see.
   */
  @JvmStatic
  fun patternIds(context: Context): Map<String, Int> =
    PATTERNS.mapValues { (_, pattern) ->
        context.resources.getIdentifier(pattern, "array", context.packageName)
      }
      .filterValues { it != 0 }
}
