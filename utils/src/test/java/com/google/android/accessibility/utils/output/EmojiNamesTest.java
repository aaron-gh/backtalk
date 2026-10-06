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
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.google.android.accessibility.utils.output.EmojiNames.Match;
import java.io.BufferedReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import org.junit.BeforeClass;
import org.junit.Test;

public class EmojiNamesTest {
  private static EmojiNames english;

  @BeforeClass
  public static void readEnglish() throws IOException {
    english = read("en");
  }

  private static EmojiNames read(String... files) throws IOException {
    EmojiNames names = new EmojiNames();
    for (String file : files) {
      try (BufferedReader reader =
          Files.newBufferedReader(
              Paths.get("src/main/assets", EmojiNames.ASSET_FOLDER, file + ".txt"),
              StandardCharsets.UTF_8)) {
        names.read(reader);
      }
    }
    return names;
  }

  /** Returns the names of the emoji in {@code text}, with the text they cover. */
  private static List<String> found(EmojiNames names, String text) {
    List<String> found = new ArrayList<>();
    for (Match match : names.findAll(text)) {
      found.add(text.substring(match.start(), match.end()) + "=" + match.name());
    }
    return found;
  }

  @Test
  public void singleEmoji() {
    assertEquals(Arrays.asList("😀=grinning face"), found(english, "Hi 😀"));
  }

  @Test
  public void skinToneIsPartOfTheEmoji() {
    assertEquals(
        Arrays.asList("👍🏽=thumbs up: medium skin tone"), found(english, "ok 👍🏽 then"));
  }

  @Test
  public void mixedSkinTones() {
    assertEquals(
        Arrays.asList("🫱🏻‍🫲🏿=handshake: light skin tone, dark skin tone"),
        found(english, "🫱🏻‍🫲🏿"));
  }

  @Test
  public void joinedSequences() {
    assertEquals(
        Arrays.asList("👨‍👩‍👧=family: man, woman, girl", "👩‍💻=woman technologist"),
        found(english, "👨‍👩‍👧 and 👩‍💻"));
  }

  @Test
  public void flagsAndKeycaps() {
    assertEquals(
        Arrays.asList("🇨🇦=flag: Canada", "#️⃣=keycap: #", "1⃣=keycap: 1"),
        found(english, "🇨🇦 #️⃣ 1⃣"));
  }

  @Test
  public void neighbouringEmojiAreSeparate() {
    assertEquals(Arrays.asList("😀=grinning face", "😀=grinning face"), found(english, "😀😀"));
  }

  @Test
  public void variationSelectorIsOptionalInSequences() {
    assertEquals(Arrays.asList("❤️‍🔥=heart on fire"), found(english, "❤️‍🔥"));
    assertEquals(Arrays.asList("❤‍🔥=heart on fire"), found(english, "❤‍🔥"));
  }

  @Test
  public void textStyleSymbolsNeedTheSelector() {
    assertEquals(Arrays.asList("❤️=red heart"), found(english, "I ❤️ it"));
    assertTrue(found(english, "I ❤ it").isEmpty());
    assertTrue(found(english, "© 2026, #1 ™").isEmpty());
  }

  @Test
  public void textSelectorStaysWithTheEmoji() {
    assertEquals(Arrays.asList("😀︎=grinning face"), found(english, "😀︎"));
  }

  @Test
  public void unknownSequenceFallsBackToItsParts() {
    // A woman with a skin tone, joined to a ball, is not an emoji, so its parts are named.
    assertEquals(
        Arrays.asList("👩🏽=woman: medium skin tone", "⚽=soccer ball"),
        found(english, "👩🏽‍⚽"));
  }

  @Test
  public void emoji18() {
    assertEquals(
        Arrays.asList("🫫=cracking face", "🫹🏽=leftwards thumb sign: medium skin tone"),
        found(english, "🫫 🫹🏽"));
  }

  @Test
  public void plainTextHasNoEmoji() {
    assertFalse(EmojiNames.mayHaveEmoji("Hello, world! 1 + 2 = 3 #tag"));
    assertFalse(EmojiNames.mayHaveEmoji("こんにちは、世界。"));
    assertTrue(EmojiNames.mayHaveEmoji("Hello 👋"));
    assertTrue(EmojiNames.mayHaveEmoji("1️⃣"));
  }

  @Test
  public void regionalNamesReplaceTheirParents() throws IOException {
    EmojiNames british = read("en", "en-001", "en-GB");
    assertEquals(Arrays.asList("🍬=sweet"), found(british, "🍬"));
    assertEquals(Arrays.asList("🍬=candy"), found(english, "🍬"));
  }

  @Test
  public void languagesBacktalkIsNotTranslatedInto() throws IOException {
    assertEquals(Arrays.asList("👍🏽=bys bawd i fyny: arlliw croen canolog"), found(read("cy"), "👍🏽"));
    assertEquals(Arrays.asList("👍=dole gumba juu"), found(read("sw"), "👍"));
  }

  @Test
  public void otherLanguages() throws IOException {
    assertEquals(
        Arrays.asList("👍🏽=Daumen hoch: mittlere Hautfarbe"), found(read("de"), "👍🏽"));
  }

  @Test
  @SuppressWarnings("deprecation") // Android still makes Hebrew locales with the old code.
  public void filesForLocales() {
    assertEquals(Arrays.asList("en-US", "en"), EmojiNames.filesFor(Locale.US));
    assertEquals(Arrays.asList("de"), EmojiNames.filesFor(Locale.GERMAN));
    assertEquals(
        Arrays.asList("zh-Hant-TW", "zh-Hant", "zh-TW", "zh"),
        EmojiNames.filesFor(Locale.TAIWAN));
    assertEquals(
        Arrays.asList("zh-Hans-CN", "zh-Hans", "zh-CN", "zh"),
        EmojiNames.filesFor(Locale.CHINA));
    assertEquals(
        Arrays.asList("es-MX", "es-419", "es"), EmojiNames.filesFor(Locale.forLanguageTag("es-MX")));
    assertEquals(Arrays.asList("he-IL", "he"), EmojiNames.filesFor(new Locale("iw", "IL")));
    assertEquals(Arrays.asList("no-NO", "no"), EmojiNames.filesFor(Locale.forLanguageTag("nb-NO")));
    assertEquals(
        Arrays.asList("sr-Latn-RS", "sr-Latn", "sr-RS", "sr"),
        EmojiNames.filesFor(Locale.forLanguageTag("sr-Latn-RS")));
    assertEquals(
        Arrays.asList("yue-Hans-CN", "yue-Hans", "yue-CN", "yue"),
        EmojiNames.filesFor(Locale.forLanguageTag("yue-CN")));
    assertEquals(Arrays.asList("cy-GB", "cy"), EmojiNames.filesFor(Locale.forLanguageTag("cy-GB")));
  }

  @Test
  public void everyLanguageHasEveryEmoji() throws IOException {
    // Regional files hold only differences; every other file names every emoji English does.
    long englishCount =
        Files.lines(Paths.get("src/main/assets", EmojiNames.ASSET_FOLDER, "en.txt")).count();
    try (var files = Files.list(Paths.get("src/main/assets", EmojiNames.ASSET_FOLDER))) {
      for (var path : (Iterable<java.nio.file.Path>) files::iterator) {
        if (!path.toString().endsWith(".txt")) {
          continue;
        }
        List<String> lines = Files.readAllLines(path, StandardCharsets.UTF_8);
        if (!lines.get(0).startsWith(EmojiNames.PARENT)) {
          assertEquals(path.toString(), englishCount, lines.size());
        }
      }
    }
  }
}
