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

class PendingRequestsTest {
  @Test
  fun processStoppingEndsTheRequestsSentToIt() {
    val pending = PendingRequests<String>()
    val reply = pending.add(1)
    assertTrue(pending.sent(1, 1))
    pending.died(1, "stopped")
    assertEquals("stopped", reply.getNow(null))
  }

  @Test
  fun requestNotYetSentIsLeftAlone() {
    val pending = PendingRequests<String>()
    val reply = pending.add(1)
    pending.died(1, "stopped")
    assertFalse(reply.isDone)
    // It then goes to a new process.
    assertTrue(pending.sent(1, 2))
    assertFalse(reply.isDone)
  }

  @Test
  fun requestSentToTheNewProcessIsLeftAlone() {
    val pending = PendingRequests<String>()
    val old = pending.add(1)
    pending.sent(1, 1)
    val new = pending.add(2)
    pending.sent(2, 2)
    pending.died(1, "stopped")
    assertEquals("stopped", old.getNow(null))
    assertFalse(new.isDone)
    pending.complete(2, "answer")
    assertEquals("answer", new.getNow(null))
  }

  @Test
  fun requestForAProcessThatAlreadyStoppedIsRefused() {
    val pending = PendingRequests<String>()
    pending.add(1)
    pending.died(1, "stopped")
    assertFalse(pending.sent(1, 1))
  }

  @Test
  fun firstReplyWins() {
    val pending = PendingRequests<String>()
    val reply = pending.add(1)
    pending.sent(1, 1)
    pending.complete(1, "cancelled")
    pending.died(1, "stopped")
    assertEquals("cancelled", reply.getNow(null))
  }

  @Test
  fun removedRequestIsNotAnswered() {
    val pending = PendingRequests<String>()
    val reply = pending.add(1)
    pending.remove(1)
    pending.complete(1, "answer")
    assertFalse(reply.isDone)
  }
}
