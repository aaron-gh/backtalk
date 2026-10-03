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
  /**
   * Sounds that play without a vibration. The braille sounds leave vibration to the braille
   * keyboard, which has its own setting.
   */
  val WITHOUT_VIBRATION: Set<String> =
    setOf(
      "display_connected",
      "display_disconnected",
      "double_beep",
      "turn_on",
      "turn_off",
      "calibration_done",
    )

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
      // The loading tone repeats while waiting, such as for an image description, so its
      // vibration is a faint heartbeat that shows the work goes on even with sounds off.
      "loading" to "loading_pattern",
      "browse_mode_on_v4_2" to "browse_mode_on_pattern",
      "browse_mode_off_v4_2" to "browse_mode_off_pattern",
    ) + (1..8).associate { "radial_menu_$it" to "radial_menu_${it}_pattern" }

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
