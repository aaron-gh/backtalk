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

/**
 * Decides where call audio goes as the phone moves away from the user's ear and back. It acts only
 * when the phone moves, and only moves the call between the earpiece and the speaker. It leaves
 * Bluetooth and wired headsets alone, and only turns off a speaker that it turned on, so a choice
 * the user makes in the Phone app stays until the phone moves again.
 */
class SpeakerphonePolicy {
  /** Where call audio is going. */
  enum class Route {
    EARPIECE,
    SPEAKER,
    /** Bluetooth, a wired headset, or anything else. */
    OTHER,
  }

  /** Whether the phone was at the user's ear at the last reading, or null before the first. */
  private var lastNearEar: Boolean? = null

  /** Whether the speaker is on because this policy turned it on. */
  private var switchedToSpeaker = false

  /**
   * Takes a proximity reading during a call. Returns the route to switch the call to, or null to
   * leave it. The first reading counts as a move, so a call that starts with the phone away from
   * the ear goes to the speaker.
   */
  fun onProximity(nearEar: Boolean, route: Route): Route? {
    val moved = nearEar != lastNearEar
    lastNearEar = nearEar
    if (!moved) return null
    return when {
      !nearEar && route == Route.EARPIECE -> {
        switchedToSpeaker = true
        Route.SPEAKER
      }
      nearEar && route == Route.SPEAKER && switchedToSpeaker -> {
        switchedToSpeaker = false
        Route.EARPIECE
      }
      else -> null
    }
  }

  /** The call moved to [route], by this policy, the user or another app. */
  fun onRouteChanged(route: Route) {
    if (route != Route.SPEAKER) switchedToSpeaker = false
  }

  /** Forgets everything, when there is no call to manage. */
  fun reset() {
    lastNearEar = null
    switchedToSpeaker = false
  }
}
