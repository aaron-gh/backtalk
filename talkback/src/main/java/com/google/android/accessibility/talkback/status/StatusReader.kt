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

package com.google.android.accessibility.talkback.status

import android.Manifest
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioManager
import android.net.wifi.WifiManager
import android.os.Build
import android.provider.Settings
import android.telephony.TelephonyCallback
import android.telephony.TelephonyDisplayInfo
import android.telephony.TelephonyManager
import androidx.annotation.RequiresApi
import com.google.android.accessibility.talkback.R
import com.google.android.accessibility.talkback.monitor.BatteryMonitor
import com.google.android.accessibility.talkback.permission.PermissionUtils
import com.google.android.accessibility.talkback.utils.DateTimeUtils
import com.google.android.accessibility.utils.SharedPreferencesUtils
import com.google.android.libraries.accessibility.utils.log.LogUtils

/** A part of the status readout. */
enum class StatusItem {
  TIME,
  BATTERY,
  WIFI,
  MOBILE,
  RINGER,
  AIRPLANE_MODE,
}

/**
 * Describes what the status bar shows, such as the time, battery, and network state. Items that are
 * in their usual state, such as the ringer when it rings, are left out.
 */
class StatusReader(private val context: Context, private val batteryMonitor: BatteryMonitor) {
  private val wifiManager = context.getSystemService(WifiManager::class.java)
  private val telephonyManager = context.getSystemService(TelephonyManager::class.java)
  private val audioManager = context.getSystemService(AudioManager::class.java)
  private val notificationManager = context.getSystemService(NotificationManager::class.java)

  // The network type the status bar shows, which says 5G where the data network type says LTE.
  private var overrideNetworkType = TelephonyDisplayInfo.OVERRIDE_NETWORK_TYPE_NONE
  private var displayInfoCallback: TelephonyCallback? = null

  /** Starts following the network type that the status bar shows. */
  fun start() {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S || displayInfoCallback != null) {
      return
    }
    if (!hasPermission(Manifest.permission.READ_PHONE_STATE)) {
      return
    }
    val callback = DisplayInfoCallback()
    try {
      telephonyManager?.registerTelephonyCallback(context.mainExecutor, callback)
      displayInfoCallback = callback
    } catch (e: SecurityException) {
      LogUtils.w(TAG, "Cannot follow the network type: %s", e)
    }
  }

  fun stop() {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
      displayInfoCallback?.let { telephonyManager?.unregisterTelephonyCallback(it) }
    }
    displayInfoCallback = null
    overrideNetworkType = TelephonyDisplayInfo.OVERRIDE_NETWORK_TYPE_NONE
  }

  @JvmOverloads
  fun describe(items: List<StatusItem> = DEFAULT_ITEMS): String =
    items.mapNotNull(::describe).filter { it.isNotBlank() }.joinToString(", ")

  private fun describe(item: StatusItem): String? =
    when (item) {
      StatusItem.TIME -> DateTimeUtils.getCurrentTime(context)
      StatusItem.BATTERY -> batteryMonitor.batteryStateDescription.trim()
      StatusItem.WIFI -> describeWifi()
      StatusItem.MOBILE -> describeMobile()
      StatusItem.RINGER -> describeRinger()
      StatusItem.AIRPLANE_MODE ->
        if (isAirplaneModeOn()) context.getString(R.string.status_airplane_mode) else null
    }

  @Suppress("DEPRECATION") // connectionInfo is the only way to get the name without a callback.
  private fun describeWifi(): String? {
    val wifiManager = wifiManager ?: return null
    if (!wifiManager.isWifiEnabled) {
      return context.getString(R.string.status_wifi_off)
    }
    val info = wifiManager.connectionInfo
    if (info == null || info.networkId == -1) {
      return context.getString(R.string.status_wifi_not_connected)
    }
    val bars = describeBars(wifiSignalLevel(wifiManager, info.rssi), wifiMaxSignalLevel(wifiManager))
    val name = info.ssid?.removeSurrounding("\"")?.takeUnless { it == WifiManager.UNKNOWN_SSID }
    if (name == null) {
      requestLocationPermissionOnce()
      return context.getString(R.string.template_status_wifi, bars)
    }
    return context.getString(R.string.template_status_wifi_named, name, bars)
  }

  @Suppress("DEPRECATION") // The instance methods need API 30.
  private fun wifiSignalLevel(wifiManager: WifiManager, rssi: Int): Int =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
      wifiManager.calculateSignalLevel(rssi)
    } else {
      WifiManager.calculateSignalLevel(rssi, LEGACY_WIFI_LEVELS)
    }

  private fun wifiMaxSignalLevel(wifiManager: WifiManager): Int =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
      wifiManager.maxSignalLevel
    } else {
      LEGACY_WIFI_LEVELS - 1
    }

  private fun describeMobile(): String? {
    val telephonyManager = telephonyManager ?: return null
    if (isAirplaneModeOn() || telephonyManager.simState != TelephonyManager.SIM_STATE_READY) {
      return null
    }
    val network =
      listOf(telephonyManager.networkOperatorName, networkTypeName(telephonyManager))
        .filterNot { it.isNullOrBlank() }
        .joinToString(" ")
    val level =
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) telephonyManager.signalStrength?.level
      else null
    if (level == null) {
      return network.ifEmpty { null }
    }
    if (level == 0 && network.isEmpty()) {
      return context.getString(R.string.status_mobile_no_service)
    }
    return context.getString(
      R.string.template_status_mobile,
      network.ifEmpty { context.getString(R.string.status_mobile) },
      describeBars(level, MOBILE_MAX_SIGNAL_LEVEL),
    )
  }

  private fun networkTypeName(telephonyManager: TelephonyManager): String? {
    when (overrideNetworkType) {
      TelephonyDisplayInfo.OVERRIDE_NETWORK_TYPE_NR_NSA,
      TelephonyDisplayInfo.OVERRIDE_NETWORK_TYPE_NR_ADVANCED -> return "5G"
      TelephonyDisplayInfo.OVERRIDE_NETWORK_TYPE_LTE_CA,
      TelephonyDisplayInfo.OVERRIDE_NETWORK_TYPE_LTE_ADVANCED_PRO -> return "LTE+"
    }
    val type =
      try {
        telephonyManager.dataNetworkType.takeUnless {
          it == TelephonyManager.NETWORK_TYPE_UNKNOWN
        } ?: telephonyManager.voiceNetworkType
      } catch (e: SecurityException) {
        return null
      }
    return when (type) {
      TelephonyManager.NETWORK_TYPE_NR -> "5G"
      TelephonyManager.NETWORK_TYPE_LTE,
      TelephonyManager.NETWORK_TYPE_IWLAN -> "LTE"
      TelephonyManager.NETWORK_TYPE_UMTS,
      TelephonyManager.NETWORK_TYPE_HSDPA,
      TelephonyManager.NETWORK_TYPE_HSUPA,
      TelephonyManager.NETWORK_TYPE_HSPA,
      TelephonyManager.NETWORK_TYPE_HSPAP,
      TelephonyManager.NETWORK_TYPE_EVDO_0,
      TelephonyManager.NETWORK_TYPE_EVDO_A,
      TelephonyManager.NETWORK_TYPE_EVDO_B,
      TelephonyManager.NETWORK_TYPE_TD_SCDMA -> "3G"
      TelephonyManager.NETWORK_TYPE_GPRS,
      TelephonyManager.NETWORK_TYPE_EDGE,
      TelephonyManager.NETWORK_TYPE_CDMA,
      TelephonyManager.NETWORK_TYPE_1xRTT,
      TelephonyManager.NETWORK_TYPE_GSM -> "2G"
      else -> null
    }
  }

  private fun describeRinger(): String? {
    val parts = mutableListOf<String>()
    val filter = notificationManager?.currentInterruptionFilter
    if (
      filter != null &&
        filter != NotificationManager.INTERRUPTION_FILTER_ALL &&
        filter != NotificationManager.INTERRUPTION_FILTER_UNKNOWN
    ) {
      parts += context.getString(R.string.status_do_not_disturb)
    }
    when (audioManager?.ringerMode) {
      AudioManager.RINGER_MODE_VIBRATE -> parts += context.getString(R.string.status_ringer_vibrate)
      AudioManager.RINGER_MODE_SILENT -> parts += context.getString(R.string.status_ringer_silent)
    }
    return parts.joinToString(", ").ifEmpty { null }
  }

  private fun describeBars(level: Int, maxLevel: Int): String =
    context.getString(R.string.template_status_bars, level, maxLevel)

  private fun isAirplaneModeOn(): Boolean =
    Settings.Global.getInt(context.contentResolver, Settings.Global.AIRPLANE_MODE_ON, 0) != 0

  private fun hasPermission(permission: String): Boolean =
    context.checkSelfPermission(permission) == PackageManager.PERMISSION_GRANTED

  /** Asks for location permission the first time the Wi-Fi name is hidden without it. */
  private fun requestLocationPermissionOnce() {
    if (hasPermission(Manifest.permission.ACCESS_FINE_LOCATION)) {
      return
    }
    val prefs = SharedPreferencesUtils.getSharedPreferences(context)
    if (prefs.getBoolean(PREF_LOCATION_REQUESTED, false)) {
      return
    }
    prefs.edit().putBoolean(PREF_LOCATION_REQUESTED, true).apply()
    PermissionUtils.requestPermissions(
      context,
      Manifest.permission.ACCESS_FINE_LOCATION,
      Manifest.permission.ACCESS_COARSE_LOCATION,
    )
  }

  @RequiresApi(Build.VERSION_CODES.S)
  private inner class DisplayInfoCallback :
    TelephonyCallback(), TelephonyCallback.DisplayInfoListener {
    override fun onDisplayInfoChanged(displayInfo: TelephonyDisplayInfo) {
      overrideNetworkType = displayInfo.overrideNetworkType
    }
  }

  companion object {
    private const val TAG = "StatusReader"
    private const val PREF_LOCATION_REQUESTED = "status_location_permission_requested"
    private const val LEGACY_WIFI_LEVELS = 5
    // SignalStrength levels run from 0 to 4.
    private const val MOBILE_MAX_SIGNAL_LEVEL = 4

    @JvmField
    val DEFAULT_ITEMS =
      listOf(
        StatusItem.TIME,
        StatusItem.BATTERY,
        StatusItem.WIFI,
        StatusItem.MOBILE,
        StatusItem.RINGER,
        StatusItem.AIRPLANE_MODE,
      )
  }
}
