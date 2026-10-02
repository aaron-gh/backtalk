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

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RequestCancellationTest {
  @Test
  fun cancelBeforeStartStopsTheRequest() {
    val requests = RequestCancellation()
    assertFalse(requests.cancel(1))
    assertFalse(requests.start(1))
    assertEquals(0, requests.runningId)
  }

  @Test
  fun cancelAfterStartStopsTheModel() {
    val requests = RequestCancellation()
    assertTrue(requests.start(1))
    assertTrue(requests.cancel(1))
    assertTrue(requests.isCancelled(1))
  }

  @Test
  fun cancelWhileTheModelLoadsIsStillSeen() {
    // The model has no answer to stop yet, so the cancel only counts if generation checks it.
    val requests = RequestCancellation()
    assertTrue(requests.start(1))
    requests.cancel(1)
    assertTrue(requests.isCancelled(1))
    requests.finish()
    assertTrue(requests.isCancelled(1))
  }

  @Test
  fun cancelCoversEarlierRequestsOnly() {
    val requests = RequestCancellation()
    assertTrue(requests.start(1))
    assertTrue(requests.cancel(2))
    assertTrue(requests.isCancelled(1))
    assertTrue(requests.isCancelled(2))
    assertFalse(requests.isCancelled(3))
    requests.finish()
    assertTrue(requests.start(3))
    assertFalse(requests.cancel(2))
  }

  @Test
  fun cancelOfAnotherRequestLeavesTheRunningOneAlone() {
    val requests = RequestCancellation()
    assertTrue(requests.start(5))
    assertFalse(requests.cancel(4))
    assertFalse(requests.isCancelled(5))
  }

  @Test
  fun cancelWhenNothingRunsDoesNotStopTheModel() {
    val requests = RequestCancellation()
    assertFalse(requests.cancel(1))
    requests.start(2)
    requests.finish()
    assertFalse(requests.cancel(2))
  }
}
