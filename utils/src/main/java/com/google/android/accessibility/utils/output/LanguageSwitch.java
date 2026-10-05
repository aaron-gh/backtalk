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

import java.util.Locale;
import org.checkerframework.checker.nullness.qual.Nullable;

/**
 * Decides whether speech follows the language an app marks text as. Switching to another
 * language, such as German for a British English voice, and switching to another country's form
 * of the voice's own language, such as US English, are turned off separately.
 */
final class LanguageSwitch {

  private LanguageSwitch() {}

  /**
   * Returns the language to speak text marked as {@code marked} in.
   *
   * @param marked the language the text is marked as, or null if it isn't marked
   * @param chosen the language chosen from the language menu, or null if none is
   * @param own the voice's language: {@code chosen}, or else the one unmarked text is spoken in
   * @return {@code marked} if speech should switch to it, otherwise {@code chosen}, which is null
   *     to speak in the usual voice
   */
  static @Nullable Locale localeToSpeak(
      @Nullable Locale marked,
      @Nullable Locale chosen,
      Locale own,
      boolean switchLanguages,
      boolean switchDialects) {
    if (marked == null || (switchLanguages && switchDialects) || marked.equals(chosen)) {
      return marked;
    }
    boolean sameLanguage = marked.getLanguage().equals(own.getLanguage());
    return (sameLanguage ? switchDialects : switchLanguages) ? marked : chosen;
  }
}
