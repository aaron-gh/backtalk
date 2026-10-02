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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import org.junit.Test;

public class SpeechChunkerTest {
  private static final String SENTENCE =
      "Same price, current silicon, current design, and full support ahead of it. ";

  @Test
  public void shortTextIsOnePiece() {
    String text = "x".repeat(2 * SpeechChunker.MIN_CHUNK - 1);
    assertEquals(Collections.singletonList(0), SpeechChunker.chunkStarts(text, Locale.US));
  }

  @Test
  public void shortClausesStayWhole() {
    String text = "Wi-Fi, On, Connected to Home network, Button";
    assertEquals(Collections.singletonList(0), SpeechChunker.chunkStarts(text, Locale.US));
  }

  @Test
  public void splitsAtEverySentence() {
    String first = "The first sentence is long enough. ";
    String second = "The second sentence is long enough. ";
    String third = "The third sentence is long enough.";
    List<Integer> starts = SpeechChunker.chunkStarts(first + second + third, Locale.US);
    assertEquals(
        Arrays.asList(0, first.length(), first.length() + second.length()), starts);
  }

  @Test
  public void splitsAfterClausePunctuation() {
    List<Integer> starts = SpeechChunker.chunkStarts(SENTENCE, Locale.US);
    // "Same price," is too short a piece, so the first cut is after "current silicon,".
    assertEquals(Arrays.asList(0, "Same price, current silicon,".length()), starts);
  }

  @Test
  public void doesNotSplitNumbers() {
    String text = "The population grew from 1,000,000 to 2,500,000 over the following decade.";
    assertEquals(Collections.singletonList(0), SpeechChunker.chunkStarts(text, Locale.US));
  }

  @Test
  public void shortSentenceJoinsNextPiece() {
    String text = "Dr. William Moon invented an embossed alphabet in 1845.";
    assertEquals(Collections.singletonList(0), SpeechChunker.chunkStarts(text, Locale.US));
  }

  @Test
  public void splitsLongClauseAtSpace() {
    String text = "word ".repeat(40);
    List<Integer> starts = SpeechChunker.chunkStarts(text, Locale.US);
    assertEquals(2, starts.size());
    assertEquals(' ', text.charAt(starts.get(1) - 1));
  }

  @Test
  public void splitsUnbrokenTextAtLimit() {
    String text = "x".repeat(SpeechChunker.MAX_CHUNK * 2 + 1);
    assertEquals(
        Arrays.asList(0, SpeechChunker.MAX_CHUNK, 2 * SpeechChunker.MAX_CHUNK),
        SpeechChunker.chunkStarts(text, Locale.US));
  }

  @Test
  public void piecesCoverTextInOrderWithinLimit() {
    String text = SENTENCE.repeat(3) + "word ".repeat(70) + SENTENCE;
    List<Integer> starts = SpeechChunker.chunkStarts(text, Locale.US);
    assertEquals(0, (int) starts.get(0));
    for (int i = 0; i < starts.size(); i++) {
      int end = i + 1 < starts.size() ? starts.get(i + 1) : text.length();
      assertTrue(end > starts.get(i));
      assertTrue(end - starts.get(i) <= SpeechChunker.MAX_CHUNK);
    }
  }
}
