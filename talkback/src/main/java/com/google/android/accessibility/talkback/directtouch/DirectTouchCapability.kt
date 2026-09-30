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

package com.google.android.accessibility.talkback.directtouch

import android.content.pm.PackageManager
import android.os.Bundle

object DirectTouchCapability {
  /** Whether the app, or its launch activity, sets [DirectTouchSettings.CAPABILITY_KEY] to true. */
  fun declaresDirectTouch(pm: PackageManager, packageName: String): Boolean {
    try {
      val appInfo = pm.getApplicationInfo(packageName, PackageManager.GET_META_DATA)
      if (declares(appInfo.metaData)) {
        return true
      }
      val component = pm.getLaunchIntentForPackage(packageName)?.component ?: return false
      return declares(pm.getActivityInfo(component, PackageManager.GET_META_DATA).metaData)
    } catch (_: Exception) {
      return false
    }
  }

  private fun declares(metaData: Bundle?): Boolean =
    metaData?.getBoolean(DirectTouchSettings.CAPABILITY_KEY, false) == true
}
