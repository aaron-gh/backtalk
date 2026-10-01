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

package com.google.android.accessibility.talkback.actor.gemini.local

import android.content.SharedPreferences

/** The settings for running Describe image and Describe screen on the phone instead of the cloud. */
object OnDeviceAiSettings {
  const val PREF_PROVIDER = "pref_on_device_ai_provider"
  const val PREF_MODEL = "pref_on_device_ai_model"
  const val PREF_GPU = "pref_on_device_ai_gpu"
  const val PREF_SHORT_PROMPTS = "pref_on_device_ai_short_prompts"
  const val PREF_TIMEOUT = "pref_on_device_ai_timeout_seconds"

  const val PROVIDER_CLOUD = "cloud"
  const val PROVIDER_DEVICE = "device"

  /** Android reports a little less RAM than the phone's label says, so allow for that. */
  private const val RAM_SLACK = 0.85

  fun isOnDevice(prefs: SharedPreferences): Boolean =
    prefs.getString(PREF_PROVIDER, PROVIDER_CLOUD) == PROVIDER_DEVICE

  fun setOnDevice(prefs: SharedPreferences, onDevice: Boolean) {
    prefs.edit().putString(PREF_PROVIDER, if (onDevice) PROVIDER_DEVICE else PROVIDER_CLOUD).apply()
  }

  fun preferredModel(prefs: SharedPreferences): LocalModel =
    LocalModel.fromId(prefs.getString(PREF_MODEL, null)) ?: LocalModel.E2B

  fun setPreferredModel(prefs: SharedPreferences, model: LocalModel) {
    prefs.edit().putString(PREF_MODEL, model.id).apply()
  }

  fun useGpu(prefs: SharedPreferences): Boolean = prefs.getBoolean(PREF_GPU, false)

  fun setUseGpu(prefs: SharedPreferences, useGpu: Boolean) {
    prefs.edit().putBoolean(PREF_GPU, useGpu).apply()
  }

  /** Whether Describe screen asks for less detail, to answer sooner. Off keeps the full prompt. */
  fun shortPrompts(prefs: SharedPreferences): Boolean = prefs.getBoolean(PREF_SHORT_PROMPTS, false)

  fun setShortPrompts(prefs: SharedPreferences, short: Boolean) {
    prefs.edit().putBoolean(PREF_SHORT_PROMPTS, short).apply()
  }

  /** How long to wait for an answer, in seconds, that the user can pick from. */
  val TIMEOUT_CHOICES_SECONDS = listOf(60, 120, 180, 300, 600)

  /** The cloud gives up after 35 seconds, which a model on the phone often cannot meet. */
  const val DEFAULT_TIMEOUT_SECONDS = 180

  fun timeoutSeconds(prefs: SharedPreferences): Int =
    prefs.getInt(PREF_TIMEOUT, DEFAULT_TIMEOUT_SECONDS).takeIf { it in TIMEOUT_CHOICES_SECONDS }
      ?: DEFAULT_TIMEOUT_SECONDS

  fun setTimeoutSeconds(prefs: SharedPreferences, seconds: Int) {
    prefs.edit().putInt(PREF_TIMEOUT, seconds).apply()
  }

  /** Why a phone can or cannot run on-device AI. */
  enum class Support {
    OK,
    /** The model library only ships for 64-bit ARM. */
    UNSUPPORTED_CPU,
    NOT_ENOUGH_RAM,
  }

  fun support(model: LocalModel, totalRamBytes: Long, supportedAbis: List<String>): Support =
    when {
      "arm64-v8a" !in supportedAbis -> Support.UNSUPPORTED_CPU
      totalRamBytes < model.minTotalRamBytes * RAM_SLACK -> Support.NOT_ENOUGH_RAM
      else -> Support.OK
    }
}
