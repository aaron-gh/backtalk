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

package com.google.android.accessibility.talkback.preference.base;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.SharedPreferences.OnSharedPreferenceChangeListener;
import android.os.Bundle;
import android.speech.tts.TextToSpeech;
import android.speech.tts.Voice;
import android.text.InputType;
import android.widget.EditText;
import androidx.appcompat.app.AlertDialog;
import androidx.preference.ListPreference;
import androidx.preference.Preference;
import androidx.preference.PreferenceScreen;
import androidx.preference.SeekBarPreference;
import com.google.android.accessibility.material.preference.AccessibilitySeekBarPreference;
import com.google.android.accessibility.material.preference.AccessibilitySuiteListPreference;
import com.google.android.accessibility.material.preference.AccessibilitySuitePreference;
import com.google.android.accessibility.material.preference.AccessibilitySuiteSwitchPreference;
import com.google.android.accessibility.talkback.R;
import com.google.android.accessibility.talkback.actor.SpeechRateAndPitchActor;
import com.google.android.accessibility.utils.SharedPreferencesUtils;
import com.google.android.accessibility.utils.output.FailoverTextToSpeech;
import com.google.android.accessibility.utils.output.VoiceProfiles;
import com.google.android.accessibility.utils.output.VoiceProfiles.VoiceProfile;
import com.google.android.libraries.accessibility.utils.log.LogUtils;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.IntFunction;
import org.checkerframework.checker.nullness.qual.Nullable;

/**
 * One voice profile's settings: its name, engine, language, voice, volume, rate, pitch, and whether
 * it sends long text a sentence at a time. The languages and voices are the ones its engine has,
 * listed by starting the engine while the screen is open.
 */
public class VoiceProfileFragment extends TalkbackBaseFragment {

  private static final String TAG = "VoiceProfileFragment";

  /** The ID of the profile to show. */
  static final String ARG_PROFILE_ID = "profile_id";

  private SharedPreferences prefs;
  private String id = "";

  private @Nullable TextToSpeech tts;
  private int ttsGeneration;

  /** The engine's voices, by language and then name, or null while they load. */
  private @Nullable List<Voice> voices;

  private @Nullable ListPreference languagePref;
  private @Nullable ListPreference voicePref;

  // The rate and pitch also change from gestures and the reading controls.
  private final OnSharedPreferenceChangeListener prefsListener =
      (sharedPrefs, key) -> {
        if (key != null && key.startsWith(VoiceProfiles.key(id, ""))) {
          updateSeekBars();
        }
      };

  @Override
  public CharSequence getTitle() {
    Bundle args = getArguments();
    String profileId = (args == null) ? "" : args.getString(ARG_PROFILE_ID, "");
    return VoiceProfiles.read(
            SharedPreferencesUtils.getSharedPreferences(requireContext()), profileId)
        .name();
  }

  @Override
  public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
    Context context = requireContext();
    prefs = SharedPreferencesUtils.getSharedPreferences(context);
    Bundle args = getArguments();
    id = (args == null) ? "" : args.getString(ARG_PROFILE_ID, "");
    // Backtalk's settings are in device protected storage. Without this, the profile would be
    // saved to a second settings file, which the service moves over the real one when it starts,
    // losing every other setting.
    getPreferenceManager().setStorageDeviceProtected();
    PreferenceScreen screen = getPreferenceManager().createPreferenceScreen(context);
    setPreferenceScreen(screen);
    if (!VoiceProfiles.ids(prefs).contains(id)) {
      return;
    }

    addName(context, screen);
    addEngine(context, screen);
    languagePref =
        addList(context, screen, VoiceProfiles.LANGUAGE, R.string.title_pref_voice_profile_language);
    languagePref.setOnPreferenceChangeListener(
        (preference, newValue) -> {
          String language = (String) newValue;
          String voice = profile().voice();
          if (!voice.isEmpty() && !hasVoice(language, voice)) {
            voicePref.setValue("");
          }
          updateVoiceLists(language);
          return true;
        });
    voicePref =
        addList(context, screen, VoiceProfiles.VOICE, R.string.title_pref_voice_profile_voice);
    addSeekBar(
        new AccessibilitySeekBarPreference(context),
        screen,
        VoiceProfiles.VOLUME,
        R.string.title_pref_speech_volume,
        getResources().getInteger(R.integer.pref_speech_volume_min),
        /* max= */ 100,
        Integer::toString);
    addSeekBar(
        new SpeechRatePreference(context),
        screen,
        VoiceProfiles.RATE,
        R.string.title_pref_talkback_speech_rate,
        TextToSpeechSettingsFragment.toPercent(SpeechRateAndPitchActor.RATE_MINIMUM),
        TextToSpeechSettingsFragment.toPercent(SpeechRateAndPitchActor.RATE_MAXIMUM),
        TextToSpeechSettingsFragment::fromPercent);
    addSeekBar(
        new AccessibilitySeekBarPreference(context),
        screen,
        VoiceProfiles.PITCH,
        R.string.title_pref_speech_pitch,
        TextToSpeechSettingsFragment.toPercent(SpeechRateAndPitchActor.PITCH_MINIMUM),
        TextToSpeechSettingsFragment.toPercent(SpeechRateAndPitchActor.PITCH_MAXIMUM),
        TextToSpeechSettingsFragment::fromPercent);

    AccessibilitySuiteSwitchPreference phrases = new AccessibilitySuiteSwitchPreference(context);
    phrases.setKey(VoiceProfiles.key(id, VoiceProfiles.PHRASES));
    phrases.setDefaultValue(false);
    phrases.setTitle(R.string.title_pref_speak_in_phrases);
    phrases.setSummary(R.string.summary_pref_speak_in_phrases);
    phrases.setIconSpaceReserved(false);
    screen.addPreference(phrases);

    Preference delete = new AccessibilitySuitePreference(context);
    delete.setTitle(R.string.title_pref_delete_voice_profile);
    delete.setPersistent(false);
    delete.setIconSpaceReserved(false);
    delete.setOnPreferenceClickListener(
        preference -> {
          confirmDelete();
          return true;
        });
    screen.addPreference(delete);

    loadVoices(profile().engine());
  }

  @Override
  public void onResume() {
    super.onResume();
    prefs.registerOnSharedPreferenceChangeListener(prefsListener);
    updateSeekBars();
  }

  @Override
  public void onPause() {
    super.onPause();
    prefs.unregisterOnSharedPreferenceChangeListener(prefsListener);
  }

  @Override
  public void onDestroy() {
    super.onDestroy();
    shutDownTts();
  }

  private VoiceProfile profile() {
    return VoiceProfiles.read(prefs, id);
  }

  private void addName(Context context, PreferenceScreen screen) {
    Preference name = new AccessibilitySuitePreference(context);
    name.setTitle(R.string.title_pref_voice_profile_name);
    name.setSummary(profile().name());
    name.setPersistent(false);
    name.setIconSpaceReserved(false);
    name.setOnPreferenceClickListener(
        preference -> {
          askForName(preference);
          return true;
        });
    screen.addPreference(name);
  }

  private void askForName(Preference namePref) {
    Context context = requireContext();
    EditText field = new EditText(context);
    field.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
    field.setSingleLine(true);
    field.setText(profile().name());
    field.selectAll();
    new AlertDialog.Builder(context)
        .setTitle(R.string.title_pref_voice_profile_name)
        .setView(VoiceProfilesFragment.padded(context, field))
        .setPositiveButton(
            android.R.string.ok,
            (dialog, which) -> {
              String name = field.getText().toString().trim();
              if (!name.isEmpty()) {
                prefs.edit().putString(VoiceProfiles.key(id, VoiceProfiles.NAME), name).apply();
                namePref.setSummary(name);
              }
            })
        .setNegativeButton(android.R.string.cancel, null)
        .show();
    field.requestFocus();
  }

  private void addEngine(Context context, PreferenceScreen screen) {
    List<String> engines = FailoverTextToSpeech.getInstalledTtsEngines(context.getPackageManager());
    String current = profile().engine();
    List<CharSequence> entries = new ArrayList<>();
    List<CharSequence> values = new ArrayList<>();
    for (String engine : engines) {
      entries.add(FailoverTextToSpeech.getEngineDisplayName(context, engine));
      values.add(engine);
    }
    if (!current.isEmpty() && !engines.contains(current)) {
      entries.add(getString(R.string.voice_profile_not_installed, current));
      values.add(current);
    }
    ListPreference enginePref =
        addList(context, screen, VoiceProfiles.ENGINE, R.string.title_pref_tts_engine);
    enginePref.setEntries(entries.toArray(new CharSequence[0]));
    enginePref.setEntryValues(values.toArray(new CharSequence[0]));
    enginePref.setSummary("%s");
    enginePref.setOnPreferenceChangeListener(
        (preference, newValue) -> {
          // Another engine has other languages and voices.
          languagePref.setValue("");
          voicePref.setValue("");
          loadVoices((String) newValue);
          return true;
        });
  }

  private ListPreference addList(
      Context context, PreferenceScreen screen, String field, int titleResId) {
    ListPreference list = new AccessibilitySuiteListPreference(context);
    list.setKey(VoiceProfiles.key(id, field));
    list.setDefaultValue("");
    list.setTitle(titleResId);
    list.setDialogTitle(titleResId);
    list.setIconSpaceReserved(false);
    screen.addPreference(list);
    return list;
  }

  private void addSeekBar(
      SeekBarPreference seekBar,
      PreferenceScreen screen,
      String field,
      int titleResId,
      int min,
      int max,
      IntFunction<String> toPrefValue) {
    seekBar.setKey(VoiceProfiles.key(id, field) + "_seekbar");
    seekBar.setPersistent(false);
    seekBar.setTitle(titleResId);
    seekBar.setMin(min);
    seekBar.setMax(max);
    seekBar.setIconSpaceReserved(false);
    seekBar.setOnPreferenceChangeListener(
        (preference, newValue) -> {
          prefs
              .edit()
              .putString(VoiceProfiles.key(id, field), toPrefValue.apply((Integer) newValue))
              .apply();
          return true;
        });
    screen.addPreference(seekBar);
  }

  private void updateSeekBars() {
    if (!VoiceProfiles.ids(prefs).contains(id)) {
      return;
    }
    VoiceProfile profile = profile();
    setSeekBarValue(VoiceProfiles.VOLUME, profile.volume());
    setSeekBarValue(VoiceProfiles.RATE, TextToSpeechSettingsFragment.toPercent(profile.rate()));
    setSeekBarValue(VoiceProfiles.PITCH, TextToSpeechSettingsFragment.toPercent(profile.pitch()));
  }

  private void setSeekBarValue(String field, int value) {
    if (findPreference(VoiceProfiles.key(id, field) + "_seekbar") instanceof SeekBarPreference bar) {
      bar.setValue(value);
    }
  }

  /** Starts {@code engine} to list its voices, which fill the language and voice lists. */
  private void loadVoices(String engine) {
    shutDownTts();
    voices = null;
    updateVoiceLists(profile().language());
    if (engine.isEmpty()) {
      return;
    }
    int generation = ++ttsGeneration;
    tts =
        new TextToSpeech(
            requireContext().getApplicationContext(),
            status -> onTtsReady(generation, status),
            engine);
  }

  private void onTtsReady(int generation, int status) {
    if (generation != ttsGeneration || tts == null || !isAdded()) {
      return;
    }
    List<Voice> loaded = new ArrayList<>();
    if (status == TextToSpeech.SUCCESS) {
      try {
        Set<Voice> engineVoices = tts.getVoices();
        if (engineVoices != null) {
          for (Voice voice : engineVoices) {
            // An engine may leave out a voice's language, which every list here needs.
            if (voice.getName() == null || voice.getLocale() == null) {
              continue;
            }
            Set<String> features = voice.getFeatures();
            if (features == null
                || !features.contains(TextToSpeech.Engine.KEY_FEATURE_NOT_INSTALLED)) {
              loaded.add(voice);
            }
          }
        }
      } catch (RuntimeException e) {
        // The engine may have stopped.
        LogUtils.e(TAG, "Failed to list voices: %s", e.toString());
      }
    }
    shutDownTts();
    loaded.sort(
        Comparator.comparing((Voice voice) -> voice.getLocale().getDisplayName())
            .thenComparing(Voice::getName));
    voices = loaded;
    updateVoiceLists(profile().language());
  }

  private void shutDownTts() {
    if (tts != null) {
      try {
        tts.shutdown();
      } catch (RuntimeException e) {
        // Already stopped.
      }
      tts = null;
    }
  }

  /** Fills the language list, and the voice list with the voices in {@code language}. */
  private void updateVoiceLists(String language) {
    if (languagePref == null || voicePref == null) {
      return;
    }
    List<Voice> voices = this.voices;
    languagePref.setEnabled(voices != null);
    voicePref.setEnabled(voices != null);
    if (voices == null) {
      languagePref.setSummary(R.string.voice_profile_loading);
      voicePref.setSummary(R.string.voice_profile_loading);
      return;
    }

    Map<String, String> languages = new LinkedHashMap<>();
    languages.put("", getString(R.string.voice_profile_engine_default));
    for (Voice voice : voices) {
      languages.put(voice.getLocale().toLanguageTag(), voice.getLocale().getDisplayName());
    }
    String currentLanguage = profile().language();
    if (!languages.containsKey(currentLanguage)) {
      languages.put(
          currentLanguage,
          getString(
              R.string.voice_profile_not_installed,
              Locale.forLanguageTag(currentLanguage).getDisplayName()));
    }
    setEntries(languagePref, languages);

    Map<String, String> names = new LinkedHashMap<>();
    names.put("", getString(R.string.voice_profile_engine_default));
    for (Voice voice : voices) {
      String tag = voice.getLocale().toLanguageTag();
      if (language.isEmpty()) {
        names.put(
            voice.getName(),
            getString(
                R.string.voice_profile_voice_in_language,
                voice.getName(),
                voice.getLocale().getDisplayName()));
      } else if (tag.equals(language)) {
        names.put(voice.getName(), voice.getName());
      }
    }
    String currentVoice = profile().voice();
    if (!names.containsKey(currentVoice) && !hasVoice(language, currentVoice)) {
      names.put(currentVoice, getString(R.string.voice_profile_not_installed, currentVoice));
    }
    setEntries(voicePref, names);
  }

  /** Whether the engine has {@code voice}, in {@code language} unless that is empty. */
  private boolean hasVoice(String language, String voice) {
    List<Voice> voices = this.voices;
    if (voices == null) {
      return true;
    }
    for (Voice candidate : voices) {
      if (candidate.getName().equals(voice)
          && (language.isEmpty() || candidate.getLocale().toLanguageTag().equals(language))) {
        return true;
      }
    }
    return false;
  }

  private static void setEntries(ListPreference list, Map<String, String> entries) {
    list.setEntries(entries.values().toArray(new CharSequence[0]));
    list.setEntryValues(entries.keySet().toArray(new CharSequence[0]));
    list.setSummary("%s");
  }

  private void confirmDelete() {
    String name = profile().name();
    new AlertDialog.Builder(requireContext())
        .setMessage(getString(R.string.voice_profile_delete_confirm, name))
        .setPositiveButton(
            R.string.voice_profile_delete,
            (dialog, which) -> {
              VoiceProfiles.delete(prefs, id);
              requireActivity().getOnBackPressedDispatcher().onBackPressed();
            })
        .setNegativeButton(android.R.string.cancel, null)
        .show();
  }
}
