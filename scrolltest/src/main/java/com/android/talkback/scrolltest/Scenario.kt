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

package com.android.talkback.scrolltest

/**
 * The screens to test with. Every item is numbered, so a skipped item is easy to hear. Open one
 * from adb with: adb shell am start -n com.android.talkback.scrolltest/.ScenarioActivity --es
 * scenario SCROLL_VIEW
 */
enum class Scenario(val title: String, val description: String) {
  SCROLL_VIEW("Scroll view", "A plain scroll view with 60 lines of text."),
  NESTED_SCROLL_VIEW("Nested scroll view", "An AndroidX nested scroll view with 60 lines."),
  SCROLL_VIEW_WITH_PAGER(
    "Scroll view with a pager",
    "Items 1 to 15, a pager of 3 pages whose next page is laid out off to the side, then items 16 to 40.",
  ),
  SCROLL_VIEW_WITH_COLLAPSED_SECTION(
    "Scroll view with a collapsed section",
    "Items 1 to 15, a collapsed section with 5 hidden lines, then items 16 to 40.",
  ),
  COMPOSE_SCROLL("Compose scroll", "A Compose column with verticalScroll, 60 lines, animated."),
  LIST("List", "A RecyclerView list of 100 items."),
  CHAT_LIST(
    "Chat list",
    "A RecyclerView with reverse layout, like a chat. Message 1 is the newest, at the bottom.",
  ),
}
