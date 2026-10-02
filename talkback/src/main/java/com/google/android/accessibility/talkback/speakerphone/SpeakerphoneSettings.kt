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

package com.google.android.accessibility.talkback.speakerphone

import android.app.AppOpsManager
import android.content.Context
import android.content.SharedPreferences
import android.os.Build
import android.os.Process

/**
 * Settings for switching calls to the speaker when the phone is away from the user's ear. This
 * needs the MANAGE_ONGOING_CALLS permission, which Android only grants to apps like smartwatch
 * companions, so the user grants it with adb or Shizuku.
 */
object SpeakerphoneSettings {
  const val PREF_ENABLED = "pref_speakerphone_away_from_ear"

  /** The app op behind MANAGE_ONGOING_CALLS, which `adb shell appops set` changes. */
  private const val OPSTR_MANAGE_ONGOING_CALLS = "android:manage_ongoing_calls"

  fun isEnabled(prefs: SharedPreferences): Boolean = prefs.getBoolean(PREF_ENABLED, false)

  /** The command that grants the permission, for this build's package name. */
  fun grantCommand(context: Context): String =
    "adb shell appops set ${context.packageName} MANAGE_ONGOING_CALLS allow"

  /**
   * Whether Android lets Backtalk change where call audio goes. The permission exists from Android
   * 12, and Android only binds [SpeakerphoneInCallService] while it is granted.
   */
  fun canControlCalls(context: Context): Boolean {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return false
    val appOps = context.getSystemService(AppOpsManager::class.java) ?: return false
    return try {
      appOps.unsafeCheckOpNoThrow(
        OPSTR_MANAGE_ONGOING_CALLS,
        Process.myUid(),
        context.packageName,
      ) == AppOpsManager.MODE_ALLOWED
    } catch (_: IllegalArgumentException) {
      false
    }
  }
}
