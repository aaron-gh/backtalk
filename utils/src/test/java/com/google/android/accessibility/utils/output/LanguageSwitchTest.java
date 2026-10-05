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
import static org.junit.Assert.assertNull;

import java.util.Locale;
import org.junit.Test;

public class LanguageSwitchTest {
  private static final Locale GERMAN = Locale.GERMANY;

  @Test
  public void bothOnFollowsTheText() {
    assertEquals(GERMAN, LanguageSwitch.localeToSpeak(GERMAN, null, Locale.UK, true, true));
    assertEquals(Locale.US, LanguageSwitch.localeToSpeak(Locale.US, null, Locale.UK, true, true));
  }

  @Test
  public void unmarkedTextStaysUnmarked() {
    assertNull(LanguageSwitch.localeToSpeak(null, null, Locale.UK, false, false));
  }

  @Test
  public void dialectsOffKeepsTheVoiceButSwitchesLanguage() {
    assertNull(LanguageSwitch.localeToSpeak(Locale.US, null, Locale.UK, true, false));
    assertEquals(GERMAN, LanguageSwitch.localeToSpeak(GERMAN, null, Locale.UK, true, false));
  }

  @Test
  public void languagesOffKeepsTheVoiceButSwitchesDialect() {
    assertNull(LanguageSwitch.localeToSpeak(GERMAN, null, Locale.UK, false, true));
    assertEquals(Locale.US, LanguageSwitch.localeToSpeak(Locale.US, null, Locale.UK, false, true));
  }

  @Test
  public void bothOffKeepsTheVoice() {
    assertNull(LanguageSwitch.localeToSpeak(GERMAN, null, Locale.UK, false, false));
    assertNull(LanguageSwitch.localeToSpeak(Locale.US, null, Locale.UK, false, false));
  }

  @Test
  public void languageWithoutCountryIsTheSameLanguage() {
    assertNull(LanguageSwitch.localeToSpeak(Locale.ENGLISH, null, Locale.UK, true, false));
  }

  @Test
  public void chosenLanguageIsAlwaysSpokenAndTakesOverFromTheVoice() {
    Locale us = Locale.US;
    assertEquals(us, LanguageSwitch.localeToSpeak(us, us, us, false, false));
    // Australian English is a dialect of the chosen US English, so stays in US English.
    Locale australian = Locale.forLanguageTag("en-AU");
    assertEquals(us, LanguageSwitch.localeToSpeak(australian, us, us, true, false));
    assertEquals(us, LanguageSwitch.localeToSpeak(GERMAN, us, us, false, true));
  }
}
