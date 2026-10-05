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

import android.content.SharedPreferences;
import java.util.ArrayList;
import java.util.List;
import org.checkerframework.checker.nullness.qual.Nullable;

/**
 * Voice profiles: speech settings the user can switch to from the reading controls, each with its
 * own engine, language, voice, volume, rate, pitch, and sentence sending. Backtalk's default is no
 * profile, and uses the text-to-speech settings. A profile speaks everything in its own voice, so
 * speech never changes language while one is in use.
 *
 * <p>Each profile's settings are separate preferences, named by {@link #key}, so that settings
 * screens can bind to them directly.
 */
public final class VoiceProfiles {

  /** The ID of the profile in use, or empty for Backtalk's default. */
  public static final String PREF_ACTIVE = "pref_voice_profile";

  /** The IDs of the profiles, in order, separated by commas. */
  public static final String PREF_IDS = "pref_voice_profile_ids";

  /** The ID the next new profile gets, so that a removed profile's ID is never reused. */
  private static final String PREF_NEXT_ID = "pref_voice_profile_next_id";

  /** Starts the name of every voice profile preference. */
  public static final String PREF_PREFIX = "pref_voice_profile";

  public static final String NAME = "name";
  public static final String ENGINE = "engine";
  public static final String LANGUAGE = "language";
  public static final String VOICE = "voice";
  public static final String VOLUME = "volume";
  public static final String RATE = "rate";
  public static final String PITCH = "pitch";
  public static final String PHRASES = "phrases";

  public static final String DEFAULT_RATE = "1.0";
  public static final String DEFAULT_PITCH = "1.0";
  public static final String DEFAULT_VOLUME = "100";

  /**
   * A profile's settings.
   *
   * @param engine the speech engine's package
   * @param language a language tag, or empty for the engine's default
   * @param voice the voice's name, or empty for the language's default voice
   * @param volume the speech volume, from 0 to 100
   */
  public record VoiceProfile(
      String id,
      String name,
      String engine,
      String language,
      String voice,
      int volume,
      float rate,
      float pitch,
      boolean phrases) {

    /** Whether this profile speaks with the same engine and voice as {@code other}. */
    boolean sameVoiceAs(@Nullable VoiceProfile other) {
      return other != null
          && engine.equals(other.engine)
          && language.equals(other.language)
          && voice.equals(other.voice);
    }
  }

  /** The profile in use, or null for Backtalk's default. */
  private static volatile @Nullable VoiceProfile active;

  private VoiceProfiles() {}

  /** Returns the profile in use, or null for Backtalk's default. */
  public static @Nullable VoiceProfile active() {
    return active;
  }

  /** Whether a profile other than Backtalk's default is in use. */
  public static boolean isProfileActive() {
    return active != null;
  }

  static void setActive(@Nullable VoiceProfile profile) {
    active = profile;
  }

  /** Returns the name of the preference holding {@code field} of profile {@code id}. */
  public static String key(String id, String field) {
    return PREF_PREFIX + "_" + id + "_" + field;
  }

  /** Whether {@code key} is one of the voice profile preferences. */
  public static boolean isProfileKey(@Nullable String key) {
    return key != null && key.startsWith(PREF_PREFIX);
  }

  /** Returns the IDs of the profiles, in order. */
  public static List<String> ids(SharedPreferences prefs) {
    List<String> ids = new ArrayList<>();
    for (String id : prefs.getString(PREF_IDS, "").split(",")) {
      if (!id.isEmpty()) {
        ids.add(id);
      }
    }
    return ids;
  }

  /** Returns the ID of the profile in use, or empty for Backtalk's default. */
  public static String activeId(SharedPreferences prefs) {
    String id = prefs.getString(PREF_ACTIVE, "");
    return ids(prefs).contains(id) ? id : "";
  }

  /** Returns the profile in use, or null for Backtalk's default. */
  public static @Nullable VoiceProfile readActive(SharedPreferences prefs) {
    String id = activeId(prefs);
    return id.isEmpty() ? null : read(prefs, id);
  }

  /** Returns profile {@code id}. */
  public static VoiceProfile read(SharedPreferences prefs, String id) {
    return new VoiceProfile(
        id,
        prefs.getString(key(id, NAME), ""),
        prefs.getString(key(id, ENGINE), ""),
        prefs.getString(key(id, LANGUAGE), ""),
        prefs.getString(key(id, VOICE), ""),
        parseInt(prefs.getString(key(id, VOLUME), DEFAULT_VOLUME), 100),
        parseFloat(prefs.getString(key(id, RATE), DEFAULT_RATE), 1f),
        parseFloat(prefs.getString(key(id, PITCH), DEFAULT_PITCH), 1f),
        prefs.getBoolean(key(id, PHRASES), false));
  }

  /**
   * Adds a profile called {@code name}, speaking with {@code engine} and otherwise like Backtalk's
   * default, and returns its ID.
   */
  public static String create(
      SharedPreferences prefs,
      String name,
      String engine,
      String volume,
      String rate,
      String pitch,
      boolean phrases) {
    List<String> ids = ids(prefs);
    int next = Math.max(1, parseInt(prefs.getString(PREF_NEXT_ID, "1"), 1));
    for (String id : ids) {
      next = Math.max(next, parseInt(id, 0) + 1);
    }
    String id = Integer.toString(next);
    ids.add(id);
    prefs
        .edit()
        .putString(PREF_NEXT_ID, Integer.toString(next + 1))
        .putString(key(id, NAME), name)
        .putString(key(id, ENGINE), engine)
        .putString(key(id, LANGUAGE), "")
        .putString(key(id, VOICE), "")
        .putString(key(id, VOLUME), volume)
        .putString(key(id, RATE), rate)
        .putString(key(id, PITCH), pitch)
        .putBoolean(key(id, PHRASES), phrases)
        .putString(PREF_IDS, String.join(",", ids))
        .apply();
    return id;
  }

  /** Removes profile {@code id}, going back to Backtalk's default if it is in use. */
  public static void delete(SharedPreferences prefs, String id) {
    List<String> ids = ids(prefs);
    ids.remove(id);
    SharedPreferences.Editor editor = prefs.edit();
    for (String field : new String[] {NAME, ENGINE, LANGUAGE, VOICE, VOLUME, RATE, PITCH, PHRASES}) {
      editor.remove(key(id, field));
    }
    if (id.equals(prefs.getString(PREF_ACTIVE, ""))) {
      editor.putString(PREF_ACTIVE, "");
    }
    editor.putString(PREF_IDS, String.join(",", ids)).apply();
  }

  private static int parseInt(@Nullable String value, int fallback) {
    try {
      return value == null ? fallback : Integer.parseInt(value);
    } catch (NumberFormatException e) {
      return fallback;
    }
  }

  private static float parseFloat(@Nullable String value, float fallback) {
    try {
      return value == null ? fallback : Float.parseFloat(value);
    } catch (NumberFormatException e) {
      return fallback;
    }
  }
}
