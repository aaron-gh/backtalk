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

package com.google.android.accessibility.talkback.migration

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.google.android.accessibility.talkback.TalkBackService
import com.google.android.libraries.accessibility.utils.log.LogUtils

/**
 * Turns this screen reader off when Backtalk under the new app ID says it is on, so two screen
 * readers never talk at once.
 *
 * Only apps signed with the same key can send it, since it requires [AppIdMove.PERMISSION].
 */
class HandOverReceiver : BroadcastReceiver() {
  override fun onReceive(context: Context, intent: Intent) {
    if (intent.action != AppIdMove.ACTION_HAND_OVER) {
      return
    }
    // The new app never hands over to anyone.
    if (context.packageName == AppIdMove.NEW_PACKAGE) {
      return
    }
    val service = TalkBackService.getInstance()
    if (service == null) {
      LogUtils.i(TAG, "The new Backtalk is on, and this one is already off")
      return
    }
    LogUtils.i(TAG, "The new Backtalk is on, so this one turns itself off")
    service.disableSelf()
  }

  private companion object {
    const val TAG = "HandOverReceiver"
  }
}
