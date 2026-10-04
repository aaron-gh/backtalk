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

package com.google.android.accessibility.talkback

import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.accessibility.AccessibilityEvent
import com.google.android.accessibility.talkback.ScrollTickCounter.Companion.UNKNOWN
import com.google.android.accessibility.utils.AccessibilityEventUtils
import com.google.android.accessibility.utils.Performance.EventId
import com.google.android.accessibility.utils.input.ScrollEventInterpreter.ScrollEventHandler
import com.google.android.accessibility.utils.input.ScrollEventInterpreter.ScrollEventInterpretation
import com.google.android.accessibility.utils.output.FeedbackController
import com.google.android.accessibility.utils.output.ScrollActionRecord
import kotlin.math.abs
import kotlin.math.min
import kotlin.math.pow

/**
 * Plays the scroll sound and a light tick once for each item that scrolls past while the user
 * scrolls a list, so a slow drag ticks slowly and a fast one quickly, and the movement can be heard
 * and felt as it happens. The sound's pitch rises from the top of the list to the bottom.
 *
 * A tick is also felt as soon as a two-finger drag is passed to the app, before the app reports
 * that it scrolled.
 */
class ScrollTicks(private val feedbackController: FeedbackController, density: Float) :
  ScrollEventHandler {

  private val counter = ScrollTickCounter(ITEM_DP * density)
  private val handler = Handler(Looper.getMainLooper())
  private var dragStartTime = -1L

  /** Called on any thread when a two-finger drag is passed to the app. */
  fun onDragStarted() {
    handler.post {
      dragStartTime = SystemClock.uptimeMillis()
      feedbackController.playHaptic(R.array.scroll_item_pattern, /* eventId= */ null)
    }
  }

  override fun onScrollEvent(
    event: AccessibilityEvent,
    interpretation: ScrollEventInterpretation,
    eventId: EventId?,
  ) {
    if (
      event.eventType != AccessibilityEvent.TYPE_VIEW_SCROLLED ||
        interpretation.userAction != ScrollActionRecord.ACTION_MANUAL_SCROLL ||
        !interpretation.isFromScrollable ||
        interpretation.isMediaPlayerAutoScroll
    ) {
      return
    }
    var deltaX = UNKNOWN
    var deltaY = UNKNOWN
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
      deltaX = event.scrollDeltaX
      deltaY = event.scrollDeltaY
    }
    val now = SystemClock.uptimeMillis()
    val ticks =
      counter.onScroll(
        now,
        event.windowId,
        event.className,
        if (event.itemCount > 0) event.fromIndex else UNKNOWN,
        event.scrollX,
        event.scrollY,
        deltaX,
        deltaY,
      )
    if (ticks == 0) {
      return
    }
    // The same pitch and volume as the scroll sound for other scrolls.
    val rate = 2f.pow(AccessibilityEventUtils.getScrollPercent(event, 50f) / 50f - 1)
    // The drag already ticked when it started.
    val feltAlready = dragStartTime >= 0 && now - dragStartTime < DRAG_TICK_COVER_MS
    dragStartTime = -1L
    // Spread the ticks over the time until the next scroll event.
    handler.removeCallbacksAndMessages(null)
    val spacing = EVENT_INTERVAL_MS / ticks
    for (i in 0 until ticks) {
      val vibrate = i > 0 || !feltAlready
      handler.postDelayed({ tick(rate, vibrate, eventId) }, i * spacing)
    }
  }

  private fun tick(rate: Float, vibrate: Boolean, eventId: EventId?) {
    feedbackController.playAuditoryWithoutHaptic(R.raw.scroll_tone, rate, rate, eventId)
    if (vibrate) {
      feedbackController.playHaptic(R.array.scroll_item_pattern, eventId)
    }
  }

  private companion object {
    // About the height of one list row.
    const val ITEM_DP = 48f
    // Android sends scroll events at most this often.
    const val EVENT_INTERVAL_MS = 100L
    // How long after a drag starts its tick stands for the first scroll event's tick. Apps report
    // the first scroll about 100 ms after it starts.
    const val DRAG_TICK_COVER_MS = 300L
  }
}

/** Counts the items that scrolled past since the last scroll event of the same scroll. */
class ScrollTickCounter(private val itemPx: Float) {
  private var lastTime = -1L
  private var lastWindowId = UNKNOWN
  private var lastClassName: CharSequence? = null
  private var lastFromIndex = UNKNOWN
  private var lastScrollX = UNKNOWN
  private var lastScrollY = UNKNOWN
  // Distance scrolled that has not made up a whole item yet.
  private var pixels = 0f

  /**
   * Returns how many ticks to play for a scroll event: one for the first event of a scroll, then
   * one for each item scrolled past, at most [MAX_TICKS]. Pass [UNKNOWN] for values the event does
   * not have.
   */
  fun onScroll(
    time: Long,
    windowId: Int,
    className: CharSequence?,
    fromIndex: Int,
    scrollX: Int,
    scrollY: Int,
    deltaX: Int,
    deltaY: Int,
  ): Int {
    val sameScroll =
      lastTime >= 0 &&
        time - lastTime <= SCROLL_GAP_MS &&
        windowId == lastWindowId &&
        className == lastClassName
    val previousFromIndex = lastFromIndex
    val previousScrollX = lastScrollX
    val previousScrollY = lastScrollY
    lastTime = time
    lastWindowId = windowId
    lastClassName = className
    lastFromIndex = fromIndex
    lastScrollX = scrollX
    lastScrollY = scrollY
    if (!sameScroll) {
      // One tick to say the scroll started, however far it went.
      pixels = 0f
      return 1
    }

    val items: Int
    if (fromIndex >= 0 && previousFromIndex >= 0) {
      // The list says which item is first on screen.
      items = abs(fromIndex - previousFromIndex)
    } else {
      pixels +=
        if (deltaX != UNKNOWN || deltaY != UNKNOWN) {
          abs(known(deltaX)) + abs(known(deltaY)).toFloat()
        } else {
          abs(change(previousScrollX, scrollX)) + abs(change(previousScrollY, scrollY)).toFloat()
        }
      items = (pixels / itemPx).toInt()
      pixels -= items * itemPx
    }
    return min(items, MAX_TICKS)
  }

  private fun known(value: Int) = if (value == UNKNOWN) 0 else value

  private fun change(from: Int, to: Int) = if (from < 0 || to < 0) 0 else to - from

  companion object {
    const val UNKNOWN = -1

    /** The most ticks for one scroll event, so a fling still ticks as separate taps. */
    const val MAX_TICKS = 4

    /** Scroll events further apart than this belong to different scrolls. */
    const val SCROLL_GAP_MS = 250L
  }
}
