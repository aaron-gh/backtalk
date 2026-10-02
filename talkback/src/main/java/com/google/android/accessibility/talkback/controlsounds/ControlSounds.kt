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

import android.content.Context
import android.graphics.Rect
import android.text.TextUtils
import androidx.annotation.RawRes
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat
import androidx.core.view.accessibility.AccessibilityWindowInfoCompat
import com.google.android.accessibility.talkback.R
import com.google.android.accessibility.utils.AccessibilityNodeInfoUtils
import com.google.android.accessibility.utils.Role
import com.google.android.accessibility.utils.traversal.ReorderedChildrenIterator
import com.google.android.libraries.accessibility.utils.device.ScreenUtils

/**
 * Control sounds, from the Unspoken add-on for NVDA: each kind of control has its own sound, played
 * from where the control is on the screen, in place of the focus sound.
 */
object ControlSounds {
  /** The sound for each kind of control, by resource entry name for individual sound switches. */
  @JvmField
  val SOUNDS: Map<String, Int> =
    linkedMapOf(
      "control_button" to R.raw.control_button,
      "control_checkbox" to R.raw.control_checkbox,
      "control_switch" to R.raw.control_switch,
      "control_radio_button" to R.raw.control_radio_button,
      "control_edit_text" to R.raw.control_edit_text,
      "control_combo_box" to R.raw.control_combo_box,
      "control_slider" to R.raw.control_slider,
      "control_image" to R.raw.control_image,
      "control_link" to R.raw.control_link,
    )

  /** Children looked at when a focused item stands for the controls inside it. */
  private const val MAX_CHILDREN_SEARCHED = 40

  /** Returns the sound for the kind of control [node] is, or 0 if it has none. */
  @JvmStatic
  @RawRes
  fun soundForRole(node: AccessibilityNodeInfoCompat?): Int {
    if (node == null) return 0
    if (AccessibilityNodeInfoUtils.isChromeRoleLink(node)) return R.raw.control_link
    return when (Role.getRole(node)) {
      Role.ROLE_BUTTON,
      Role.ROLE_IMAGE_BUTTON,
      Role.ROLE_FLOATING_ACTION_BUTTON,
      Role.ROLE_VOICE_DICTATION_BUTTON -> R.raw.control_button
      Role.ROLE_CHECK_BOX -> R.raw.control_checkbox
      Role.ROLE_SWITCH,
      Role.ROLE_TOGGLE_BUTTON -> R.raw.control_switch
      Role.ROLE_RADIO_BUTTON,
      Role.ROLE_CHECKED_TEXT_VIEW -> R.raw.control_radio_button
      Role.ROLE_EDIT_TEXT -> R.raw.control_edit_text
      Role.ROLE_DROP_DOWN_LIST -> R.raw.control_combo_box
      Role.ROLE_SEEK_CONTROL,
      Role.ROLE_NUMBER_PICKER -> R.raw.control_slider
      Role.ROLE_IMAGE -> R.raw.control_image
      else -> 0
    }
  }

  /**
   * Returns the sound for focusing [node], or 0 if it has none. An item that is not a control
   * itself, like a row in Settings, gets the sound of the first control inside it that is read as
   * part of it, like the row's switch. Keys on the keyboard keep the usual focus sound.
   */
  @JvmStatic
  @RawRes
  fun soundForFocus(node: AccessibilityNodeInfoCompat?): Int {
    if (node == null) return 0
    if (AccessibilityNodeInfoUtils.getWindowType(node) ==
      AccessibilityWindowInfoCompat.TYPE_INPUT_METHOD
    ) {
      return 0
    }
    val own = soundForRole(node)
    if (own != 0) return own
    return soundOfChildren(node, intArrayOf(MAX_CHILDREN_SEARCHED))
  }

  /**
   * Searches the children that are read as part of [node], in reading order. The descriptions
   * only include children when the node has no content description of its own, and never inside a
   * web view. Images are skipped, because rows often start with an icon before their control.
   */
  private fun soundOfChildren(node: AccessibilityNodeInfoCompat, budget: IntArray): Int {
    if (Role.getRole(node) == Role.ROLE_WEB_VIEW ||
      !TextUtils.isEmpty(node.contentDescription)
    ) {
      return 0
    }
    val children = ReorderedChildrenIterator.createAscendingIterator(node)
    while (children.hasNext() && budget[0] > 0) {
      val child = children.next() ?: continue
      budget[0]--
      if (!AccessibilityNodeInfoUtils.isVisible(child) ||
        AccessibilityNodeInfoUtils.isAccessibilityFocusable(child)
      ) {
        continue
      }
      val own = soundForRole(child)
      if (own != 0 && own != R.raw.control_image) return own
      val sound = soundOfChildren(child, budget)
      if (sound != 0) return sound
    }
    return 0
  }

  /**
   * Returns where the middle of [node] is, as fractions of the screen from its left and top
   * edges, or null if the node has no place on the screen.
   */
  @JvmStatic
  fun screenPosition(node: AccessibilityNodeInfoCompat, context: Context): FloatArray? {
    val bounds = Rect()
    node.getBoundsInScreen(bounds)
    val screen = ScreenUtils.getRealScreenSize(context)
    if (bounds.isEmpty || screen.x <= 0 || screen.y <= 0) return null
    return floatArrayOf(
      (bounds.exactCenterX() / screen.x).coerceIn(0f, 1f),
      (bounds.exactCenterY() / screen.y).coerceIn(0f, 1f),
    )
  }
}
