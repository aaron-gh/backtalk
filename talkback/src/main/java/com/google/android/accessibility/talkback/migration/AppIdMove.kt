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

/**
 * The move from the app ID com.android.talkback to fyi.quin.backtalk.
 *
 * Some phones ship TalkBack as a system app named com.android.talkback, so Backtalk could not
 * install there. The old app installs the new one, shares its settings with it through
 * [SettingsExportProvider], and turns itself off when the new one tells it through
 * [HandOverReceiver]. Both apps are signed with the same key, so a signature permission guards
 * both.
 */
object AppIdMove {
  /** The app ID that Backtalk used first. */
  const val OLD_PACKAGE = "com.android.talkback"

  /** The app ID that Backtalk uses now. */
  const val NEW_PACKAGE = "fyi.quin.backtalk"

  /** Guards the settings export and the hand-over. Both apps declare and hold it. */
  const val PERMISSION = "fyi.quin.backtalk.permission.MIGRATE"

  /** Sent by the new app to the old one when the new screen reader is on. */
  const val ACTION_HAND_OVER = "fyi.quin.backtalk.action.HAND_OVER"

  /** The class of the screen reader service, the same in both apps. */
  const val SERVICE_CLASS = "com.google.android.marvin.talkback.TalkBackService"

  /** Lists the exported files, with the columns [COLUMN_PATH] and [COLUMN_SIZE]. */
  const val PATH_FILES = "files"

  /** Opens one exported file: content://<authority>/file/<directory>/<name>. */
  const val PATH_FILE = "file"

  const val COLUMN_PATH = "path"
  const val COLUMN_SIZE = "size"

  /** The authority of the settings export in the app with the given app ID. */
  fun authority(packageName: String): String = "$packageName.migration"

  /** The flattened name of the screen reader service in the app with the given app ID. */
  fun serviceName(packageName: String): String = "$packageName/$SERVICE_CLASS"

  /** What to do with a downloaded APK. */
  enum class InstallKind {
    /** The APK is a newer build of this app. */
    UPDATE,
    /** The APK is Backtalk under the new app ID, which installs next to this app. */
    MOVE,
    /** The APK is something else. */
    REFUSE,
  }

  /**
   * Decides how to install an APK whose app ID is [apkPackage] while this app is [ownPackage].
   * [sameSigner] is false only when the APK is known to be signed with a different key.
   */
  fun installKind(apkPackage: String?, ownPackage: String, sameSigner: Boolean): InstallKind =
    when {
      apkPackage.isNullOrEmpty() -> InstallKind.REFUSE
      apkPackage == ownPackage -> InstallKind.UPDATE
      apkPackage == NEW_PACKAGE && sameSigner -> InstallKind.MOVE
      else -> InstallKind.REFUSE
    }
}
