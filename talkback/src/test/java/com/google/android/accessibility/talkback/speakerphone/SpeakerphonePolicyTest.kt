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

import com.google.android.accessibility.talkback.speakerphone.SpeakerphonePolicy.Route
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SpeakerphonePolicyTest {
  private val policy = SpeakerphonePolicy()

  @Test
  fun takingThePhoneFromTheEarSwitchesToTheSpeakerAndBack() {
    assertNull(policy.onProximity(nearEar = true, Route.EARPIECE))
    assertEquals(Route.SPEAKER, policy.onProximity(nearEar = false, Route.EARPIECE))
    policy.onRouteChanged(Route.SPEAKER)
    assertEquals(Route.EARPIECE, policy.onProximity(nearEar = true, Route.SPEAKER))
  }

  @Test
  fun aCallThatStartsAwayFromTheEarGoesToTheSpeaker() {
    assertEquals(Route.SPEAKER, policy.onProximity(nearEar = false, Route.EARPIECE))
  }

  @Test
  fun aReadingThatDoesNotChangeDoesNothing() {
    policy.onProximity(nearEar = true, Route.EARPIECE)
    assertNull(policy.onProximity(nearEar = true, Route.EARPIECE))
    policy.onProximity(nearEar = false, Route.EARPIECE)
    policy.onRouteChanged(Route.SPEAKER)
    assertNull(policy.onProximity(nearEar = false, Route.SPEAKER))
  }

  @Test
  fun aSpeakerTheUserTurnedOnStaysOnAtTheEar() {
    policy.onProximity(nearEar = true, Route.EARPIECE)
    policy.onRouteChanged(Route.SPEAKER)
    assertNull(policy.onProximity(nearEar = false, Route.SPEAKER))
    assertNull(policy.onProximity(nearEar = true, Route.SPEAKER))
  }

  @Test
  fun turningTheSpeakerOffAwayFromTheEarIsKeptUntilThePhoneMoves() {
    policy.onProximity(nearEar = false, Route.EARPIECE)
    policy.onRouteChanged(Route.SPEAKER)
    // The user turns the speaker off in the Phone app.
    policy.onRouteChanged(Route.EARPIECE)
    assertNull(policy.onProximity(nearEar = true, Route.EARPIECE))
    assertEquals(Route.SPEAKER, policy.onProximity(nearEar = false, Route.EARPIECE))
  }

  @Test
  fun headsetsAreLeftAlone() {
    assertNull(policy.onProximity(nearEar = false, Route.OTHER))
    assertNull(policy.onProximity(nearEar = true, Route.OTHER))
  }

  @Test
  fun aHeadsetConnectedOnTheSpeakerIsNotSwitchedBack() {
    policy.onProximity(nearEar = false, Route.EARPIECE)
    policy.onRouteChanged(Route.SPEAKER)
    policy.onRouteChanged(Route.OTHER)
    assertNull(policy.onProximity(nearEar = true, Route.OTHER))
  }

  @Test
  fun resetStartsOver() {
    policy.onProximity(nearEar = false, Route.EARPIECE)
    policy.reset()
    assertEquals(Route.SPEAKER, policy.onProximity(nearEar = false, Route.EARPIECE))
  }
}
