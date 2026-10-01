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
import android.text.TextUtils;
import androidx.annotation.Nullable;
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat;
import com.google.android.accessibility.utils.Role;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

/**
 * Finds content that is in the node tree but off screen, so a swipe can scroll just far enough to
 * show it. Scroll views lay out all of their content, so the next item exists before it is shown.
 * Lists and grids recycle their rows, and pagers hold whole pages off to the side, so those are
 * left to the usual page scroll.
 */
final class OffscreenContent {
  // Large scroll views are rare, and walking a huge tree on every edge swipe would cost more than
  // the page scroll it saves.
  private static final int MAX_NODES = 1500;

  private OffscreenContent() {}

  /** Whether {@code scrollable} keeps all of its content in the tree, as scroll views do. */
  static boolean keepsAllContent(AccessibilityNodeInfoCompat scrollable) {
    return scrollable.isVisibleToUser()
        && scrollable.getCollectionInfo() == null
        && Role.getRole(scrollable) != Role.ROLE_PAGER;
  }

  /**
   * Returns the first off-screen node after {@code pivot} in {@code scrollable}, in tree order, that
   * has something to say or do. Collapsed nodes, with no size, are skipped, because they are hidden
   * rather than scrolled away.
   */
  static @Nullable AccessibilityNodeInfoCompat findNext(
      AccessibilityNodeInfoCompat scrollable, AccessibilityNodeInfoCompat pivot, boolean forward) {
    List<AccessibilityNodeInfoCompat> nodes = new ArrayList<>();
    List<Integer> subtreeEnds = new ArrayList<>();
    if (!collect(scrollable, nodes, subtreeEnds)) {
      return null;
    }
    int pivotIndex = nodes.indexOf(pivot);
    if (pivotIndex < 0) {
      return null;
    }
    if (forward) {
      // Skip the pivot's own descendants, which the pivot already speaks.
      for (int i = subtreeEnds.get(pivotIndex); i < nodes.size(); i++) {
        if (isCandidate(nodes.get(i))) {
          return nodes.get(i);
        }
      }
    } else {
      for (int i = pivotIndex - 1; i >= 0; i--) {
        // Ancestors of the pivot come before it in tree order, but they are on screen around it.
        if (subtreeEnds.get(i) > pivotIndex) {
          continue;
        }
        if (isCandidate(nodes.get(i))) {
          return nodes.get(i);
        }
      }
    }
    return null;
  }

  /**
   * Lists the subtree in pre-order, with the index just past each node's subtree. Returns {@code
   * false} if the subtree is too large.
   */
  private static boolean collect(
      AccessibilityNodeInfoCompat root,
      List<AccessibilityNodeInfoCompat> nodes,
      List<Integer> subtreeEnds) {
    // Each entry is a node to visit, or the index of a visited node whose subtree ends here.
    Deque<Object> stack = new ArrayDeque<>();
    stack.push(root);
    while (!stack.isEmpty()) {
      Object top = stack.pop();
      if (top instanceof Integer start) {
        subtreeEnds.set(start, nodes.size());
        continue;
      }
      AccessibilityNodeInfoCompat node = (AccessibilityNodeInfoCompat) top;
      if (nodes.size() >= MAX_NODES) {
        return false;
      }
      int index = nodes.size();
      nodes.add(node);
      subtreeEnds.add(index + 1);
      stack.push(index);
      for (int i = node.getChildCount() - 1; i >= 0; i--) {
        AccessibilityNodeInfoCompat child = node.getChild(i);
        if (child != null) {
          stack.push(child);
        }
      }
    }
    return true;
  }

  @SuppressWarnings("deprecation") // getBoundsInParent is the only unclipped size a node reports.
  private static boolean isCandidate(AccessibilityNodeInfoCompat node) {
    if (node.isVisibleToUser()) {
      return false;
    }
    Rect size = new Rect();
    node.getBoundsInParent(size);
    if (size.width() <= 0 || size.height() <= 0) {
      return false;
    }
    return !TextUtils.isEmpty(node.getText())
        || !TextUtils.isEmpty(node.getContentDescription())
        || node.isFocusable()
        || node.isClickable()
        || node.isScreenReaderFocusable();
  }
}
