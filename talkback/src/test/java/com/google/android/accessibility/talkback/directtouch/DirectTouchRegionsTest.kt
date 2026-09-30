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

import android.view.accessibility.AccessibilityEvent
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DirectTouchRegionsTest {
  @Test
  fun windowEventsReassertTheRegion() {
    // Other services clear the shared region on these, like Narwhal does when a volume slider opens.
    assertTrue(DirectTouchRegions.reassertsRegion(AccessibilityEvent.TYPE_WINDOWS_CHANGED))
    assertTrue(DirectTouchRegions.reassertsRegion(AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED))
  }

  @Test
  fun otherEventsDoNot() {
    assertFalse(DirectTouchRegions.reassertsRegion(AccessibilityEvent.TYPE_VIEW_FOCUSED))
    assertFalse(DirectTouchRegions.reassertsRegion(AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED))
    assertFalse(DirectTouchRegions.reassertsRegion(AccessibilityEvent.TYPE_TOUCH_INTERACTION_START))
  }
}
