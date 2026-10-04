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

package com.google.android.accessibility.talkback.compositor

import android.os.SystemClock
import android.view.accessibility.AccessibilityEvent
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat
import com.google.android.accessibility.talkback.focusmanagement.TraversalTreeCache

/**
 * The announcements of the nodes that a swipe from the focused node would most likely reach,
 * worked out while the user listens, so that a swipe can speak one without working it out then.
 *
 * They hold for the focused node only, and are thrown away when the focus moves, when anything in
 * the window changes, even text, and after [MAX_AGE_MS].
 */
object PreparedFocusSpeech {
  /** How long a prepared announcement is kept, in case a change came without an event. */
  const val MAX_AGE_MS = 5_000L

  private const val NO_WINDOW_ID = -1

  /** An announcement of focusing [node], which was read from the app to prepare it. */
  class Prepared(val node: AccessibilityNodeInfoCompat, val feedback: EventFeedback) {
    internal val time = SystemClock.uptimeMillis()

    internal fun isFor(other: AccessibilityNodeInfoCompat): Boolean = node == other
  }

  private val prepared = ArrayList<Prepared>(2)
  private var windowId = NO_WINDOW_ID

  /** Keeps [feedback] as the announcement of focusing [node]. */
  @JvmStatic
  fun put(node: AccessibilityNodeInfoCompat, feedback: EventFeedback) {
    if (node.windowId != windowId) {
      clear()
      windowId = node.windowId
    }
    prepared.removeAll { it.isFor(node) }
    prepared += Prepared(node, feedback)
  }

  /** Returns whether an announcement of focusing [node] is kept. */
  @JvmStatic
  fun has(node: AccessibilityNodeInfoCompat): Boolean = find(node) != null

  /**
   * Returns the announcement of focusing [node], or null if there is none, and forgets them all,
   * since the focus is moving.
   */
  @JvmStatic
  fun take(node: AccessibilityNodeInfoCompat): Prepared? {
    val found = find(node)
    clear()
    return found
  }

  /** Forgets all announcements. */
  @JvmStatic
  fun clear() {
    prepared.clear()
    windowId = NO_WINDOW_ID
  }

  /** Forgets all announcements if [event] can change what a node in their window says. */
  @JvmStatic
  fun onAccessibilityEvent(event: AccessibilityEvent) {
    if (prepared.isNotEmpty() && canChange(event.eventType, event.windowId, windowId)) clear()
  }

  /**
   * Whether an event of [type] from the window [eventWindowId] can change what a node in the window
   * [preparedWindowId] says. Unlike the saved reading order, any content change counts, even of
   * text or state alone.
   */
  @JvmStatic
  fun canChange(type: Int, eventWindowId: Int, preparedWindowId: Int): Boolean =
    type and TraversalTreeCache.IGNORED_EVENT_TYPES == 0 &&
      (type == AccessibilityEvent.TYPE_WINDOWS_CHANGED ||
        type == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED ||
        eventWindowId == NO_WINDOW_ID ||
        eventWindowId == preparedWindowId)

  /** Forgets all announcements before Backtalk performs [actionId], unless it only moves focus. */
  @JvmStatic
  fun onNodeAction(actionId: Int) {
    if (actionId !in TraversalTreeCache.FOCUS_ACTIONS) clear()
  }

  private fun find(node: AccessibilityNodeInfoCompat): Prepared? {
    val now = SystemClock.uptimeMillis()
    prepared.removeAll { now - it.time > MAX_AGE_MS }
    return prepared.firstOrNull { it.isFor(node) }
  }
}
