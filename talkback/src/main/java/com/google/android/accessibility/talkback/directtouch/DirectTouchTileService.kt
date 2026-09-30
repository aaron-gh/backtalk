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

import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import com.google.android.accessibility.utils.SharedPreferencesUtils

/** Quick settings tile that pauses and resumes direct touch without leaving the game. */
class DirectTouchTileService : TileService() {
  override fun onStartListening() {
    refresh()
  }

  override fun onClick() {
    val prefs = SharedPreferencesUtils.getSharedPreferences(this)
    DirectTouchSettings.setMasterEnabled(prefs, !DirectTouchSettings.isMasterEnabled(prefs))
    refresh()
  }

  private fun refresh() {
    val tile = qsTile ?: return
    val enabled = DirectTouchSettings.isMasterEnabled(SharedPreferencesUtils.getSharedPreferences(this))
    tile.state = if (enabled) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
    tile.updateTile()
  }
}
