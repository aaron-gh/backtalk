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

package com.google.android.accessibility.talkback.controlsounds

import android.content.SharedPreferences
import com.google.android.accessibility.utils.output.FeedbackController

/** Stored settings for control sounds. They are off by default. */
object ControlSoundsSettings {
  const val PREF_ON = "pref_control_sounds"
  const val PREF_SPEAK_ROLES = "pref_control_sounds_speak_roles"
  const val PREF_3D = "pref_control_sounds_3d"

  const val VALUE_3D_WITH_HEADPHONES = "headphones"
  const val VALUE_3D_ALWAYS = "always"
  const val VALUE_3D_NEVER = "never"

  @JvmStatic fun isOn(prefs: SharedPreferences): Boolean = prefs.getBoolean(PREF_ON, false)

  /** Whether to still say "button" and the like when a control's sound plays. */
  @JvmStatic
  fun speakRoles(prefs: SharedPreferences): Boolean = prefs.getBoolean(PREF_SPEAK_ROLES, false)

  /** How control sounds are placed, as a [FeedbackController] spatial mode. */
  @JvmStatic
  fun spatialMode(prefs: SharedPreferences): Int =
    when (prefs.getString(PREF_3D, VALUE_3D_WITH_HEADPHONES)) {
      VALUE_3D_ALWAYS -> FeedbackController.SPATIAL_3D
      VALUE_3D_NEVER -> FeedbackController.SPATIAL_STEREO
      else -> FeedbackController.SPATIAL_3D_WITH_HEADPHONES
    }

  /**
   * The control sounds that play, which are none while control sounds or sound feedback are off,
   * and leave out the ones turned off in Individual sounds and vibrations. A control whose sound
   * does not play has its kind spoken.
   */
  @JvmStatic
  fun playingSounds(
    prefs: SharedPreferences,
    soundFeedbackOn: Boolean,
    mutedSounds: Set<String>,
  ): Set<Int> {
    if (!soundFeedbackOn || !isOn(prefs)) return emptySet()
    return ControlSounds.SOUNDS.filterKeys { it !in mutedSounds }.values.toSet()
  }
}
