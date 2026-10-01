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

import com.google.android.accessibility.talkback.actor.gemini.local.OnDeviceAiSettings.Support
import com.google.android.accessibility.talkback.directtouch.FakeSharedPreferences
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OnDeviceAiSettingsTest {
  private val gib = 1024L * 1024L * 1024L
  private val arm64 = listOf("arm64-v8a", "armeabi-v7a")

  @Test
  fun cloudIsTheDefaultProvider() {
    assertFalse(OnDeviceAiSettings.isOnDevice(FakeSharedPreferences()))
  }

  @Test
  fun providerModelAndGpuAreRemembered() {
    val prefs = FakeSharedPreferences()
    OnDeviceAiSettings.setOnDevice(prefs, true)
    OnDeviceAiSettings.setPreferredModel(prefs, LocalModel.E4B)
    OnDeviceAiSettings.setUseGpu(prefs, true)
    assertTrue(OnDeviceAiSettings.isOnDevice(prefs))
    assertEquals(LocalModel.E4B, OnDeviceAiSettings.preferredModel(prefs))
    assertTrue(OnDeviceAiSettings.useGpu(prefs))
    OnDeviceAiSettings.setOnDevice(prefs, false)
    assertFalse(OnDeviceAiSettings.isOnDevice(prefs))
  }

  @Test
  fun smallerModelIsTheDefault() {
    assertEquals(LocalModel.E2B, OnDeviceAiSettings.preferredModel(FakeSharedPreferences()))
  }

  @Test
  fun a32BitPhoneIsUnsupported() {
    assertEquals(
      Support.UNSUPPORTED_CPU,
      OnDeviceAiSettings.support(LocalModel.E2B, 12 * gib, listOf("armeabi-v7a")),
    )
  }

  @Test
  fun anEightGigPhoneThatReportsLessRamStillRunsBothModels() {
    // An 8 GB phone reports about 7.3 GB to Android.
    val reported = (7.3 * gib).toLong()
    assertEquals(Support.OK, OnDeviceAiSettings.support(LocalModel.E2B, reported, arm64))
    assertEquals(Support.OK, OnDeviceAiSettings.support(LocalModel.E4B, reported, arm64))
  }

  @Test
  fun aFourGigPhoneIsTooSmall() {
    assertEquals(
      Support.NOT_ENOUGH_RAM,
      OnDeviceAiSettings.support(LocalModel.E2B, (3.7 * gib).toLong(), arm64),
    )
  }

  @Test
  fun aSixGigPhoneRunsTheSmallModelButNotTheLargeOne() {
    val reported = (5.5 * gib).toLong()
    assertEquals(Support.OK, OnDeviceAiSettings.support(LocalModel.E2B, reported, arm64))
    assertEquals(Support.NOT_ENOUGH_RAM, OnDeviceAiSettings.support(LocalModel.E4B, reported, arm64))
  }

  @Test
  fun timeoutDefaultsToThreeMinutesAndKeepsAValidChoice() {
    val prefs = FakeSharedPreferences()
    assertEquals(180, OnDeviceAiSettings.timeoutSeconds(prefs))
    OnDeviceAiSettings.setTimeoutSeconds(prefs, 300)
    assertEquals(300, OnDeviceAiSettings.timeoutSeconds(prefs))
  }

  @Test
  fun anUnknownTimeoutFallsBackToTheDefault() {
    val prefs = FakeSharedPreferences()
    OnDeviceAiSettings.setTimeoutSeconds(prefs, 7)
    assertEquals(180, OnDeviceAiSettings.timeoutSeconds(prefs))
  }

  @Test
  fun shortPromptsAreOffUntilTheUserTurnsThemOn() {
    val prefs = FakeSharedPreferences()
    assertFalse(OnDeviceAiSettings.shortPrompts(prefs))
    OnDeviceAiSettings.setShortPrompts(prefs, true)
    assertTrue(OnDeviceAiSettings.shortPrompts(prefs))
  }
}
