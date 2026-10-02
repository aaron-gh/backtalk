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

import android.content.SharedPreferences
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.telecom.Call
import android.telecom.CallAudioState
import android.telecom.InCallService
import android.util.Log
import androidx.annotation.RequiresApi
import com.google.android.accessibility.talkback.TalkBackService
import com.google.android.accessibility.talkback.speakerphone.SpeakerphonePolicy.Route
import com.google.android.accessibility.utils.SharedPreferencesUtils

/**
 * Switches a call to the speaker when the phone is away from the user's ear, and back to the
 * earpiece when it is held up again, like VoiceOver on iPhone.
 *
 * Android binds this service during calls only when Backtalk holds MANAGE_ONGOING_CALLS, which the
 * user grants with adb or Shizuku. It has no call screen of its own. It watches the proximity
 * sensor only while a call can be heard, Backtalk is on, and the setting is on.
 */
@RequiresApi(Build.VERSION_CODES.S)
class SpeakerphoneInCallService : InCallService() {
  private val main = Handler(Looper.getMainLooper())
  private val policy = SpeakerphonePolicy()
  private lateinit var sensorManager: SensorManager
  private var sensor: Sensor? = null
  private var farValue = 0f
  private var listening = false
  private var pendingNearEar: Boolean? = null

  private val prefsListener =
    SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
      if (key == SpeakerphoneSettings.PREF_ENABLED) update()
    }

  private val callCallback =
    object : Call.Callback() {
      override fun onStateChanged(call: Call, state: Int) {
        update()
      }
    }

  private val sensorListener =
    object : SensorEventListener {
      override fun onSensorChanged(event: SensorEvent) {
        val nearEar = event.values[0] < farValue
        if (nearEar == pendingNearEar) return
        // Wait until the reading holds, so a hand passing over the sensor does not switch.
        pendingNearEar = nearEar
        main.removeCallbacks(applyReading)
        main.postDelayed(applyReading, SETTLE_MS)
      }

      override fun onAccuracyChanged(sensor: Sensor, accuracy: Int) {}
    }

  private val applyReading = Runnable {
    val nearEar = pendingNearEar ?: return@Runnable
    val target = policy.onProximity(nearEar, currentRoute()) ?: return@Runnable
    Log.i(TAG, "Phone ${if (nearEar) "at" else "away from"} the ear, switching to $target")
    setAudioRoute(
      if (target == Route.SPEAKER) CallAudioState.ROUTE_SPEAKER else CallAudioState.ROUTE_EARPIECE
    )
  }

  override fun onCreate() {
    super.onCreate()
    sensorManager = getSystemService(SensorManager::class.java)
    sensor =
      sensorManager.getDefaultSensor(Sensor.TYPE_PROXIMITY, /* wakeUp= */ true)
        ?: sensorManager.getDefaultSensor(Sensor.TYPE_PROXIMITY)
    farValue = sensor?.let { minOf(it.maximumRange, NEAR_CM) } ?: 0f
    SharedPreferencesUtils.getSharedPreferences(this)
      .registerOnSharedPreferenceChangeListener(prefsListener)
  }

  override fun onDestroy() {
    SharedPreferencesUtils.getSharedPreferences(this)
      .unregisterOnSharedPreferenceChangeListener(prefsListener)
    stopListening()
    super.onDestroy()
  }

  override fun onCallAdded(call: Call) {
    call.registerCallback(callCallback, main)
    update()
  }

  override fun onCallRemoved(call: Call) {
    call.unregisterCallback(callCallback)
    update()
  }

  override fun onCallAudioStateChanged(audioState: CallAudioState) {
    policy.onRouteChanged(currentRoute())
  }

  /** Starts or stops watching the sensor, as calls, Backtalk and the setting change. */
  private fun update() {
    val prefs = SharedPreferencesUtils.getSharedPreferences(this)
    val active =
      calls.any { it.details.state in AUDIBLE_STATES } &&
        SpeakerphoneSettings.isEnabled(prefs) &&
        TalkBackService.isServiceActive()
    if (active) startListening() else stopListening()
  }

  private fun startListening() {
    val sensor = sensor ?: return
    if (listening) return
    listening = true
    // The sensor reports its current state as soon as it is registered.
    sensorManager.registerListener(sensorListener, sensor, SensorManager.SENSOR_DELAY_NORMAL, main)
  }

  private fun stopListening() {
    if (!listening) return
    listening = false
    sensorManager.unregisterListener(sensorListener)
    main.removeCallbacks(applyReading)
    pendingNearEar = null
    policy.reset()
  }

  private fun currentRoute(): Route =
    when (callAudioState?.route) {
      CallAudioState.ROUTE_EARPIECE -> Route.EARPIECE
      CallAudioState.ROUTE_SPEAKER -> Route.SPEAKER
      else -> Route.OTHER
    }

  companion object {
    private const val TAG = "SpeakerphoneInCall"

    /** How long a reading must hold before the call switches. */
    private const val SETTLE_MS = 300L

    /** Closer than this, in centimetres, counts as at the ear. */
    private const val NEAR_CM = 5f

    /** Call states where the user can hear the call: dialing, connecting and talking. */
    private val AUDIBLE_STATES =
      setOf(
        Call.STATE_DIALING,
        Call.STATE_CONNECTING,
        Call.STATE_ACTIVE,
        Call.STATE_PULLING_CALL,
      )
  }
}
