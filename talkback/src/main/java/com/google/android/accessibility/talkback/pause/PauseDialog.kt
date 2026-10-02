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

package com.google.android.accessibility.talkback.pause

import android.content.Context
import android.content.DialogInterface
import com.google.android.accessibility.talkback.R
import com.google.android.accessibility.talkback.dialog.FirstTimeUseDialog

/**
 * Asks before pausing Backtalk and says how to resume. It has an "Always show this warning" check
 * box, like the dialog before hiding the screen.
 */
class PauseDialog(context: Context, private val onConfirm: () -> Unit) :
  FirstTimeUseDialog(
    context,
    /* showDialogPreference= */ R.string.pref_show_pause_confirmation_dialog,
    /* dialogTitleResId= */ R.string.dialog_title_pause_backtalk,
    /* dialogMainMessageResId= */ R.string.dialog_message_pause_backtalk,
    /* checkboxTextResId= */ R.string.always_show_warning_checkbox,
  ) {

  init {
    setPositiveButtonStringRes(R.string.dialog_button_pause_backtalk)
  }

  override fun handleDialogClick(buttonClicked: Int) {
    super.handleDialogClick(buttonClicked)
    if (buttonClicked == DialogInterface.BUTTON_POSITIVE) {
      onConfirm()
    }
  }

  /** Shows the dialog, ending with [howToResume]. */
  fun show(howToResume: String) {
    setMainMessage(context.getString(R.string.dialog_message_pause_backtalk) + "\n\n" + howToResume)
    showDialog()
  }
}
