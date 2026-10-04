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
import com.google.android.accessibility.utils.AccessibilityEventUtils

/**
 * Remembers the nodes whose accessibility focus was spoken as soon as Backtalk focused them, so
 * that the app's focus events for them, which come later, are not spoken again.
 *
 * Each node is remembered for one focus event, for at most [MAX_WAIT_MS].
 */
class EarlyFocusSpeech(clock: () -> Long = SystemClock::uptimeMillis) {
  private val spoken = RecentItems<AccessibilityNodeInfoCompat>(MAX_NODES, MAX_WAIT_MS, clock)

  /** Remembers that [node]'s focus has been spoken. */
  fun add(node: AccessibilityNodeInfoCompat) = spoken.add(node)

  /** Whether [event] is the focus event for a node whose focus has been spoken. */
  fun isSpoken(event: AccessibilityEvent): Boolean = source(event)?.let(spoken::contains) ?: false

  /**
   * Returns whether [event] is the focus event for a node whose focus has been spoken, and forgets
   * the node if so.
   */
  fun consume(event: AccessibilityEvent): Boolean = source(event)?.let(spoken::remove) ?: false

  private fun source(event: AccessibilityEvent): AccessibilityNodeInfoCompat? =
    if (
      event.eventType != AccessibilityEvent.TYPE_VIEW_ACCESSIBILITY_FOCUSED || spoken.isEmpty()
    ) {
      null
    } else {
      AccessibilityEventUtils.sourceCompat(event)
    }

  companion object {
    /** How long the app's focus event may take to arrive. */
    const val MAX_WAIT_MS = 2_000L

    /** How many nodes are remembered at once, for swipes faster than the app's events. */
    private const val MAX_NODES = 4
  }
}

/**
 * Items remembered for at most [maxAgeMs] each, and at most [maxItems] at once, the oldest
 * forgotten first.
 */
class RecentItems<T>(
  private val maxItems: Int,
  private val maxAgeMs: Long,
  private val clock: () -> Long,
) {
  private class Entry<T>(val item: T, val time: Long)

  private val entries = ArrayDeque<Entry<T>>()

  fun add(item: T) {
    removeExpired()
    entries.addLast(Entry(item, clock()))
    while (entries.size > maxItems) entries.removeFirst()
  }

  fun isEmpty(): Boolean {
    removeExpired()
    return entries.isEmpty()
  }

  fun contains(item: T): Boolean {
    removeExpired()
    return entries.any { it.item == item }
  }

  /** Forgets the oldest entry equal to [item], and returns whether there was one. */
  fun remove(item: T): Boolean {
    removeExpired()
    val index = entries.indexOfFirst { it.item == item }
    if (index < 0) return false
    entries.removeAt(index)
    return true
  }

  private fun removeExpired() {
    val now = clock()
    while (entries.isNotEmpty() && now - entries.first().time > maxAgeMs) entries.removeFirst()
  }
}
