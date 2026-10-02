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

/** The numbers behind the download progress that the settings screen and notification show. */
object DownloadProgress {
  /** How much the speed estimate follows the latest reading, from 0 (never) to 1 (only it). */
  private const val SPEED_SMOOTHING = 0.3

  fun percent(bytes: Long, total: Long): Int =
    if (total <= 0) 0 else (bytes * 100 / total).coerceIn(0, 100).toInt()

  /** Smooths the speed so that the time left does not jump around with each reading. */
  fun smoothedSpeed(previous: Long, bytesDelta: Long, millisDelta: Long): Long {
    if (millisDelta <= 0) return previous
    val instant = bytesDelta * 1000 / millisDelta
    return if (previous <= 0) instant
    else (previous * (1 - SPEED_SMOOTHING) + instant * SPEED_SMOOTHING).toLong()
  }

  /** Seconds left at [bytesPerSecond], or null while the speed is not known yet. */
  fun secondsLeft(bytes: Long, total: Long, bytesPerSecond: Long): Long? =
    if (bytesPerSecond <= 0) null else ((total - bytes).coerceAtLeast(0) + bytesPerSecond - 1) / bytesPerSecond

  /** The 25% steps that [bytes] has reached, so that progress can be announced at each step. */
  fun milestone(bytes: Long, total: Long): Int = percent(bytes, total) / 25 * 25
}
