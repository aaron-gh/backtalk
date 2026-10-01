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

package com.google.android.accessibility.utils.output;

import java.text.BreakIterator;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Splits long speech into pieces of about a sentence. Some engines only notice a stop request
 * between the blocks they synthesize, and treat a whole utterance as one block, so stopping a long
 * paragraph took up to 850 ms and held back the next item's speech. A piece of at most {@link
 * #MAX_CHUNK} characters stops quickly, and is queued right behind the one before it, so the
 * paragraph still sounds continuous.
 */
public final class SpeechChunker {
  /** Longest piece, in characters. Text up to this length is not split. */
  static final int MAX_CHUNK = 150;

  /** A piece is not cut at a comma or space before this many characters. */
  private static final int MIN_SOFT_CHUNK = 40;

  private SpeechChunker() {}

  /**
   * Returns the start offset of each piece of {@code text}, beginning with 0. Pieces end at
   * sentence boundaries where they can, then at a comma, semicolon, colon or space, and only cut a
   * word when there is none of those.
   */
  public static List<Integer> chunkStarts(CharSequence text, Locale locale) {
    List<Integer> starts = new ArrayList<>();
    starts.add(0);
    int length = text.length();
    if (length <= MAX_CHUNK) {
      return starts;
    }
    String string = text.toString();
    BreakIterator sentences = BreakIterator.getSentenceInstance(locale);
    sentences.setText(string);

    int start = 0;
    while (length - start > MAX_CHUNK) {
      int limit = start + MAX_CHUNK;
      int end = sentences.preceding(limit + 1);
      if (end <= start || end == BreakIterator.DONE) {
        end = softBreak(string, start, limit);
      }
      starts.add(end);
      start = end;
    }
    return starts;
  }

  /** Returns where to cut a sentence longer than a piece, just after a pause or a space. */
  private static int softBreak(String text, int start, int limit) {
    for (int i = limit - 1; i >= start + MIN_SOFT_CHUNK; i--) {
      char c = text.charAt(i);
      if (c == ',' || c == ';' || c == ':') {
        return i + 1;
      }
    }
    for (int i = limit - 1; i >= start + MIN_SOFT_CHUNK; i--) {
      if (Character.isWhitespace(text.charAt(i))) {
        return i + 1;
      }
    }
    return limit;
  }
}
