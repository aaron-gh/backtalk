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

import java.io.BufferedReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.checkerframework.checker.nullness.qual.Nullable;

/**
 * The names of emoji in one language, from Unicode CLDR, and finds emoji in text. An emoji can be
 * a sequence of characters: a skin tone, a family joined by zero-width joiners, a flag or a keycap
 * is one emoji with one name, so the longest sequence with a name wins.
 *
 * <p>Variation selector 16, which asks for emoji style, is ignored when matching, since apps send
 * emoji both with and without it. A character that is shown as text unless that selector follows
 * it, such as © or ❤, is only an emoji when it does follow.
 *
 * <p>The files are made by tools/emoji_names/make_emoji_names.py, which describes their format.
 */
final class EmojiNames {
  /** The folder in the app's assets that holds a file of names for each language. */
  static final String ASSET_FOLDER = "emoji_names";

  /** The first line of a regional file, followed by the language whose names it changes. */
  static final String PARENT = "@parent\t";

  private static final char VARIATION_SELECTOR_TEXT = '︎';
  private static final char VARIATION_SELECTOR_EMOJI = '️';
  private static final char COMBINING_KEYCAP = '⃣';

  /** An emoji in text, from {@code start} to {@code end}, and its name. */
  record Match(int start, int end, String name) {}

  /** Names by emoji, without variation selectors. */
  private final Map<String, String> names = new HashMap<>();

  /** Every shorter start of an emoji in {@link #names}, so matching stops when none can follow. */
  private final Set<String> prefixes = new HashSet<>();

  /** Single characters that are only emoji when variation selector 16 follows them. */
  private final Set<String> textStyle = new HashSet<>();

  /**
   * Adds the names in a names file. Its {@link #PARENT} line is skipped: the caller reads the
   * parent's names first.
   */
  void read(BufferedReader reader) throws IOException {
    String line;
    while ((line = reader.readLine()) != null) {
      int tab = line.indexOf('\t');
      if (tab > 0 && tab < line.length() - 1 && !line.startsWith(PARENT)) {
        add(line.substring(0, tab), line.substring(tab + 1));
      }
    }
  }

  /** Adds or replaces the name of an emoji, given in its fully qualified form. */
  void add(String emoji, String name) {
    String key = withoutSelectors(emoji);
    if (key.isEmpty()) {
      return;
    }
    names.put(key, name);
    int firstLength = Character.charCount(key.codePointAt(0));
    if (key.length() == firstLength && emoji.length() > key.length()) {
      textStyle.add(key);
    }
    for (int i = firstLength; i < key.length(); i += Character.charCount(key.codePointAt(i))) {
      prefixes.add(key.substring(0, i));
    }
  }

  boolean isEmpty() {
    return names.isEmpty();
  }

  /** Returns the emoji in {@code text}, in order. */
  List<Match> findAll(CharSequence text) {
    List<Match> matches = new ArrayList<>();
    int i = 0;
    while (i < text.length()) {
      @Nullable Match match = mayStartEmoji(text, i) ? find(text, i) : null;
      if (match != null) {
        matches.add(match);
        i = match.end();
      } else {
        i += Character.charCount(Character.codePointAt(text, i));
      }
    }
    return matches;
  }

  /** Returns the longest emoji that starts at {@code start}, or null if none does. */
  @Nullable Match find(CharSequence text, int start) {
    StringBuilder key = new StringBuilder();
    int end = -1;
    @Nullable String name = null;
    int i = start;
    while (i < text.length()) {
      int codePoint = Character.codePointAt(text, i);
      if (codePoint == VARIATION_SELECTOR_EMOJI) {
        i++;
        if (end == i - 1) {
          end = i;
        }
        continue;
      }
      key.appendCodePoint(codePoint);
      i += Character.charCount(codePoint);
      String candidate = key.toString();
      @Nullable String found = names.get(candidate);
      if (found != null
          && (!textStyle.contains(candidate)
              || (i < text.length() && text.charAt(i) == VARIATION_SELECTOR_EMOJI))) {
        end = i;
        name = found;
      }
      if (!prefixes.contains(candidate)) {
        break;
      }
    }
    if (name == null) {
      return null;
    }
    // An emoji asked to look like text is still the same emoji.
    if (end < text.length() && text.charAt(end) == VARIATION_SELECTOR_TEXT) {
      end++;
    }
    return new Match(start, end, name);
  }

  /**
   * Returns whether {@code text} may hold an emoji, without loading any names, so that most text
   * is passed on untouched.
   */
  static boolean mayHaveEmoji(CharSequence text) {
    for (int i = 0; i < text.length(); i++) {
      if (mayStartEmoji(text, i)) {
        return true;
      }
    }
    return false;
  }

  /** Returns whether an emoji may start at {@code i}: the ranges emoji are in, and keycaps. */
  static boolean mayStartEmoji(CharSequence text, int i) {
    char c = text.charAt(i);
    if (c == '©' || c == '®' || (c >= '‼' && c <= '⭕')) {
      return true;
    }
    if (c == '〰' || c == '〽' || c == '㊗' || c == '㊙') {
      return true;
    }
    if (c >= '\uD83C' && c <= '\uD83E') {
      return true;
    }
    if (c == '#' || c == '*' || (c >= '0' && c <= '9')) {
      int next = i + 1;
      if (next < text.length() && text.charAt(next) == VARIATION_SELECTOR_EMOJI) {
        next++;
      }
      return next < text.length() && text.charAt(next) == COMBINING_KEYCAP;
    }
    return false;
  }

  private static String withoutSelectors(String emoji) {
    return emoji.replace(String.valueOf(VARIATION_SELECTOR_EMOJI), "");
  }

  /**
   * Returns the names files to try for {@code locale}, best first, as CLDR locales. Android and
   * CLDR name some languages differently, and Chinese is told apart by script, not by country.
   */
  static List<String> filesFor(Locale locale) {
    // The language tag, unlike getLanguage(), gives "he", "id" and "yi" rather than old codes.
    String language = locale.toLanguageTag().split("-", 2)[0];
    switch (language) {
      case "tl" -> language = "fil";
      case "nb" -> language = "no";
      default -> {}
    }
    String script = locale.getScript();
    String region = locale.getCountry();
    if (language.equals("zh") && script.isEmpty()) {
      script = region.equals("TW") || region.equals("HK") || region.equals("MO") ? "Hant" : "Hans";
    }
    if (language.equals("yue") && script.isEmpty()) {
      script = region.equals("CN") ? "Hans" : "Hant";
    }
    List<String> files = new ArrayList<>();
    if (!script.isEmpty()) {
      if (!region.isEmpty()) {
        files.add(language + "-" + script + "-" + region);
      }
      files.add(language + "-" + script);
    }
    if (!region.isEmpty()) {
      files.add(language + "-" + region);
      if (language.equals("es") && !region.equals("ES")) {
        files.add("es-419");
      }
    }
    files.add(language);
    return files;
  }
}
