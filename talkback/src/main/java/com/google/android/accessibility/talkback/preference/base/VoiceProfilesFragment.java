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
import android.os.Bundle;
import android.text.InputType;
import android.widget.EditText;
import android.widget.FrameLayout;
import androidx.appcompat.app.AlertDialog;
import androidx.preference.Preference;
import androidx.preference.PreferenceScreen;
import com.google.android.accessibility.material.preference.AccessibilitySuiteListPreference;
import com.google.android.accessibility.material.preference.AccessibilitySuitePreference;
import com.google.android.accessibility.talkback.R;
import com.google.android.accessibility.utils.SharedPreferencesUtils;
import com.google.android.accessibility.utils.output.FailoverTextToSpeech;
import com.google.android.accessibility.utils.output.VoiceProfiles;
import com.google.android.accessibility.utils.output.VoiceProfiles.VoiceProfile;
import java.util.List;
import org.checkerframework.checker.nullness.qual.Nullable;

/**
 * The voice profiles: the one in use, a row for each that opens its settings, and a way to add one.
 * Backtalk's default is always there, and uses the text-to-speech settings.
 */
public class VoiceProfilesFragment extends TalkbackBaseFragment {

  private SharedPreferences prefs;

  @Override
  public CharSequence getTitle() {
    return getText(R.string.title_pref_voice_profiles);
  }

  @Override
  public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
    Context context = requireContext();
    prefs = SharedPreferencesUtils.getSharedPreferences(context);
    // Backtalk's settings are in device protected storage. Without this, the profile in use would
    // be saved to a second settings file, which the service moves over the real one when it
    // starts, losing every other setting.
    getPreferenceManager().setStorageDeviceProtected();
    setPreferenceScreen(getPreferenceManager().createPreferenceScreen(context));
  }

  @Override
  public void onResume() {
    super.onResume();
    // A profile may have been renamed, removed or switched to meanwhile.
    rebuild();
  }

  private void rebuild() {
    Context context = requireContext();
    PreferenceScreen screen = getPreferenceScreen();
    screen.removeAll();
    List<String> ids = VoiceProfiles.ids(prefs);

    CharSequence[] entries = new CharSequence[ids.size() + 1];
    CharSequence[] values = new CharSequence[ids.size() + 1];
    entries[0] = getString(R.string.voice_profile_default);
    values[0] = "";
    for (int i = 0; i < ids.size(); i++) {
      entries[i + 1] = VoiceProfiles.read(prefs, ids.get(i)).name();
      values[i + 1] = ids.get(i);
    }
    AccessibilitySuiteListPreference inUse = new AccessibilitySuiteListPreference(context);
    inUse.setKey(VoiceProfiles.PREF_ACTIVE);
    inUse.setTitle(R.string.title_pref_voice_profile_in_use);
    inUse.setDialogTitle(R.string.title_pref_voice_profile_in_use);
    inUse.setEntries(entries);
    inUse.setEntryValues(values);
    inUse.setDefaultValue("");
    inUse.setSummary("%s");
    inUse.setIconSpaceReserved(false);
    screen.addPreference(inUse);
    // A removed profile's ID may still be saved as the one in use.
    inUse.setValue(VoiceProfiles.activeId(prefs));

    for (String id : ids) {
      VoiceProfile profile = VoiceProfiles.read(prefs, id);
      Preference row = new AccessibilitySuitePreference(context);
      row.setKey(rowKey(id));
      row.setTitle(profile.name());
      row.setSummary(FailoverTextToSpeech.getEngineDisplayName(context, profile.engine()));
      row.setFragment(VoiceProfileFragment.class.getName());
      row.getExtras().putString(VoiceProfileFragment.ARG_PROFILE_ID, id);
      row.setPersistent(false);
      row.setIconSpaceReserved(false);
      screen.addPreference(row);
    }

    Preference add = new AccessibilitySuitePreference(context);
    add.setTitle(R.string.title_pref_add_voice_profile);
    add.setPersistent(false);
    add.setIconSpaceReserved(false);
    add.setOnPreferenceClickListener(
        preference -> {
          askForName(ids.size() + 1);
          return true;
        });
    screen.addPreference(add);
  }

  private void askForName(int number) {
    Context context = requireContext();
    EditText field = new EditText(context);
    field.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
    field.setSingleLine(true);
    field.setText(getString(R.string.voice_profile_new_name, number));
    field.selectAll();
    AlertDialog.Builder builder =
        new AlertDialog.Builder(context)
            .setTitle(R.string.title_pref_add_voice_profile)
            .setView(padded(context, field))
            .setPositiveButton(
                android.R.string.ok,
                (dialog, which) -> {
                  String name = field.getText().toString().trim();
                  add(name.isEmpty() ? getString(R.string.voice_profile_new_name, number) : name);
                })
            .setNegativeButton(android.R.string.cancel, null);
    builder.show();
    field.requestFocus();
  }

  /** Adds a profile that sounds like Backtalk's default to begin with, and opens its settings. */
  private void add(String name) {
    Context context = requireContext();
    String engine = FailoverTextToSpeech.getSelectedEngine(context);
    List<String> installed = FailoverTextToSpeech.getInstalledTtsEngines(context.getPackageManager());
    if ((engine == null || !installed.contains(engine)) && !installed.isEmpty()) {
      engine = installed.get(0);
    }
    String id =
        VoiceProfiles.create(
            prefs,
            name,
            engine == null ? "" : engine,
            prefs.getString(
                getString(R.string.pref_speech_volume_key),
                getString(R.string.pref_speech_volume_default)),
            prefs.getString(
                getString(R.string.pref_speech_rate_key),
                getString(R.string.pref_speech_rate_default)),
            prefs.getString(
                getString(R.string.pref_speech_pitch_key),
                getString(R.string.pref_speech_pitch_default)),
            prefs.getBoolean(
                getString(R.string.pref_speak_in_phrases_key),
                getResources().getBoolean(R.bool.pref_speak_in_phrases_default)));
    rebuild();
    @Nullable Preference row = findPreference(rowKey(id));
    if (row != null) {
      onPreferenceTreeClick(row);
    }
  }

  private static String rowKey(String id) {
    return "voice_profile_row_" + id;
  }

  /** Lines a dialog's text field up with its title. */
  static FrameLayout padded(Context context, EditText field) {
    int padding = (int) (24 * context.getResources().getDisplayMetrics().density);
    FrameLayout container = new FrameLayout(context);
    container.setPadding(padding, 0, padding, 0);
    container.addView(field);
    return container;
  }
}
