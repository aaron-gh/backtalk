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

package com.google.android.accessibility.talkback.focusmanagement;

import android.graphics.Rect;
import android.view.accessibility.AccessibilityEvent;
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat;
import com.google.android.accessibility.utils.AccessibilityEventUtils;
import com.google.android.accessibility.utils.output.ScrollActionRecord.AutoScrollSuccessChecker;
import com.google.android.libraries.accessibility.utils.log.LogUtils;

/**
 * Ends the wait after ACTION_SHOW_ON_SCREEN as soon as the target is wholly on screen.
 * Without a checker, focus waits 110 ms after each scroll event in case more arrive, which made
 * every swipe that scrolls a list item into view about 300 ms slow.
 *
 * <p>Screen bounds are clipped to the visible area, and the scroll leaves the target flush with
 * the list's edge, so its position cannot tell a whole target from a cut-off one. Bounds in parent
 * are not clipped, so the target counts as on screen once its visible size on the axis the list
 * scrolled matches its full size. Otherwise the usual wait runs, for example while an animated
 * scroll is still moving, or when the app does not report bounds in parent.
 */
public class EnsureOnScreenSuccessChecker implements AutoScrollSuccessChecker {
  private static final String TAG = "EnsureOnScreenChecker";

  private final AccessibilityNodeInfoCompat target;

  public EnsureOnScreenSuccessChecker(AccessibilityNodeInfoCompat target) {
    // Copied, because refresh() would change the node the caller is about to focus.
    this.target = AccessibilityNodeInfoCompat.obtain(target);
  }

  @Override
  @SuppressWarnings("deprecation") // getBoundsInParent is the only unclipped size a node reports.
  public boolean isAutoScrollSuccess(
      AccessibilityNodeInfoCompat scrolledNodeInRecord, AccessibilityEvent scrolledEvent) {
    int deltaX = AccessibilityEventUtils.getScrollDeltaX(scrolledEvent);
    int deltaY = AccessibilityEventUtils.getScrollDeltaY(scrolledEvent);
    if ((deltaX == 0 && deltaY == 0) || !target.refresh()) {
      return false;
    }
    Rect visible = new Rect();
    target.getBoundsInScreen(visible);
    Rect full = new Rect();
    target.getBoundsInParent(full);
    if (visible.isEmpty() || full.isEmpty()) {
      return false;
    }
    boolean onScreen =
        (deltaY == 0 || visible.height() >= full.height())
            && (deltaX == 0 || visible.width() >= full.width());
    LogUtils.d(TAG, "visible=%s full=%s onScreen=%s", visible, full, onScreen);
    return onScreen;
  }
}
