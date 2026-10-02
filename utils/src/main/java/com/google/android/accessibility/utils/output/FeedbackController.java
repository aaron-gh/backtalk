/*
 * Copyright (C) 2014 The Android Open Source Project
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

import static com.google.android.accessibility.utils.Performance.EVENT_ID_UNTRACKED;

import android.content.Context;
import android.content.res.Resources;
import android.content.res.Resources.NotFoundException;
import android.media.AudioAttributes;
import android.media.AudioDeviceInfo;
import android.media.AudioManager;
import android.os.Build;
import android.media.SoundPool;
import android.os.SystemClock;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.util.SparseArray;
import android.util.SparseIntArray;
import com.google.android.accessibility.utils.BuildVersionUtils;
import com.google.android.accessibility.utils.Performance.EventId;
import com.google.android.accessibility.utils.R;
import com.google.android.libraries.accessibility.utils.log.LogUtils;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.checkerframework.checker.nullness.qual.Nullable;

/** A feedback controller that caches sounds for quicker playback. */
public class FeedbackController {

  //////////////////////////////////////////////////////////////////////////////////////////
  // Constants

  private static final String TAG = "FeedbackController";

  /** Default stream for audio feedback. */
  public static final int DEFAULT_STREAM =
      BuildVersionUtils.isAtLeastO()
          ? AudioManager.STREAM_ACCESSIBILITY
          : AudioManager.STREAM_MUSIC;

  /** Maximum number of concurrent audio streams. */
  private static final int MAX_STREAMS = 10;

  public static final long NO_SEPARATION = 0;

  /** Positioned sounds are panned between the left and right speakers. */
  public static final int SPATIAL_STEREO = 0;

  /** Positioned sounds are played in 3D, for headphones. */
  public static final int SPATIAL_3D = 1;

  /** Positioned sounds are played in 3D when headphones are connected, and panned otherwise. */
  public static final int SPATIAL_3D_WITH_HEADPHONES = 2;

  private static final AudioAttributes FEEDBACK_ATTRIBUTES =
      new AudioAttributes.Builder()
          .setUsage(AudioAttributes.USAGE_ASSISTANCE_ACCESSIBILITY)
          .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
          .build();

  //////////////////////////////////////////////////////////////////////////////////////////
  // Member data

  /** The parent context. */
  private final Context mContext;

  /** The resources for this context. */
  private final Resources mResources;

  /** The SoundPool instance for loading sounds and playing previously loaded sounds. */
  private final SoundPool mSoundPool;

  /** The vibration service used to play vibration patterns. */
  private final Vibrator mVibrator;

  /** Map from the resource IDs of loaded sounds to SoundPool sound IDs. */
  private final SparseIntArray mSoundIds = new SparseIntArray();

  private final HapticPatternParser parser;

  /** The volume adjustment for sound feedback. */
  private float mVolumeAdjustment = 1.0f;

  private boolean mAuditoryEnabled;
  private boolean mHapticEnabled;

  /** Resource names of the sounds the user turned off one by one. */
  private Set<String> mMutedAuditoryNames = Collections.emptySet();

  /** Resource names of the vibration patterns the user turned off one by one. */
  private Set<String> mMutedHapticNames = Collections.emptySet();

  /** Cache of resource names, so muting does not look one up on every sound. */
  private final SparseArray<String> mResourceNames = new SparseArray<>();

  private final Set<HapticFeedbackListener> mHapticFeedbackListeners = new HashSet<>();

  private final @NonNull HashMap<Integer, Long> resIdToLastPlayUptimeMillisec = new HashMap<>();

  /** How sounds with a position on the screen are played, one of the {@code SPATIAL_} values. */
  private int mSpatialMode = SPATIAL_3D_WITH_HEADPHONES;

  /** Created the first time a sound is played in 3D. */
  private @Nullable SpatialSoundPlayer mSpatialSoundPlayer;

  //////////////////////////////////////////////////////////////////////////////////////////
  // Construction

  public FeedbackController(Context context) {
    this(context, createSoundPool(), (Vibrator) context.getSystemService(Context.VIBRATOR_SERVICE));
  }

  public FeedbackController(Context context, SoundPool soundPool, Vibrator vibrator) {
    mContext = context;
    mResources = context.getResources();
    mSoundPool = soundPool;
    mVibrator = vibrator;
    parser = new HapticPatternParser(mVibrator);
  }

  //////////////////////////////////////////////////////////////////////////////////////////
  // Methods

  /**
   * Plays the vibration pattern associated with the given resource ID.
   *
   * @param resId The vibration pattern's resource identifier.
   * @return {@code true} if successful.
   */
  public boolean playHaptic(int resId, @Nullable EventId eventId) {
    if (!mHapticEnabled || resId == 0 || isMuted(mMutedHapticNames, resId)) {
      return false;
    }
    LogUtils.v(TAG, "playHaptic() resId=%d eventId=%s", resId, eventId);

    final int[] patternArray;
    try {
      patternArray = mResources.getIntArray(resId);
    } catch (NotFoundException e) {
      LogUtils.e(TAG, "Failed to load pattern %d", resId);
      return false;
    }

    VibrationEffect effect = parser.parse(patternArray);

    long nanoTime = System.nanoTime();
    for (HapticFeedbackListener listener : mHapticFeedbackListeners) {
      listener.onHapticFeedbackStarting(nanoTime);
    }

    mVibrator.vibrate(effect);

    return true;
  }

  /**
   * Adds a listener to be called when haptic feedback begins.
   *
   * @param listener The listener to add.
   */
  public void addHapticFeedbackListener(FeedbackController.HapticFeedbackListener listener) {
    mHapticFeedbackListeners.add(listener);
  }

  /**
   * Removes a HapticFeedbackListener.
   *
   * @param listener The listener to remove.
   */
  public void removeHapticFeedbackListener(FeedbackController.HapticFeedbackListener listener) {
    mHapticFeedbackListeners.remove(listener);
  }

  /**
   * Plays the auditory feedback associated with the given resource ID using the default rate,
   * volume, and panning.
   *
   * @param resId The auditory feedback's resource identifier.
   */
  public void playAuditory(int resId, @Nullable EventId eventId) {
    playAuditory(resId, 1.0f /* rate */, 1.0f /* volume */, eventId);
  }

  /** Plays audio-resource only if it has not been played in the last separationMillisec. */
  public void playAuditory(
      int resId,
      final float rate,
      float volume,
      boolean ignoreVolumeAdjustment,
      @Nullable EventId eventId,
      long separationMillisec) {
    if (separationMillisec != NO_SEPARATION) {
      @Nullable Long lastPlayUptimeMillisec = resIdToLastPlayUptimeMillisec.get(resId);
      long nowUptimeMillisec = SystemClock.uptimeMillis();
      // If time to play... update last-play-time... else... skip playing.
      if ((lastPlayUptimeMillisec == null)
          || (separationMillisec < nowUptimeMillisec - lastPlayUptimeMillisec)) {
        resIdToLastPlayUptimeMillisec.put(resId, nowUptimeMillisec);
      } else {
        return;
      }
    }

    playAuditory(resId, rate, volume, ignoreVolumeAdjustment, eventId);
  }

  /**
   * Plays the auditory feedback associated with the given resource ID using the specified rate,
   * volume, and panning.
   *
   * @param resId The auditory feedback's resource identifier.
   * @param rate The playback rate adjustment, from 0.5 (half speed) to 2.0 (double speed).
   * @param volume The volume adjustment, from 0.0 (mute) to 1.0 (original volume).
   */
  public void playAuditory(int resId, final float rate, float volume, @Nullable EventId eventId) {
    playAuditory(resId, rate, volume, /* ignoreVolumeAdjustment= */ false, eventId);
  }

  /**
   * Plays the auditory feedback associated with the given resource ID using the specified rate,
   * volume, and panning.
   *
   * @param resId The auditory feedback's resource identifier.
   * @param rate The playback rate adjustment, from 0.5 (half speed) to 2.0 (double speed).
   * @param volume The volume adjustment, from 0.0 (mute) to 1.0 (original volume).
   * @param ignoreVolumeAdjustment Ignore the volume adjustment from {@link
   *     #setVolumeAdjustment(float)} or not.
   */
  public void playAuditory(
      int resId,
      final float rate,
      float volume,
      boolean ignoreVolumeAdjustment,
      @Nullable EventId eventId) {
    if (!mAuditoryEnabled || resId == 0 || isMuted(mMutedAuditoryNames, resId)) {
      return;
    }
    LogUtils.v(TAG, "playAuditory() resId=%d eventId=%s", resId, eventId);

    final float adjustedVolume = ignoreVolumeAdjustment ? volume : volume * mVolumeAdjustment;
    playFromPool(resId, rate, adjustedVolume, adjustedVolume);
  }

  /**
   * Plays a sound as if it came from a place on the screen, in 3D or panned between the speakers
   * according to {@link #setSpatialMode}. Only mono, 16 bit, 44.1 kHz WAV sounds can play in 3D.
   *
   * @param x The place from the left edge of the screen, from 0 to 1, or negative for no place.
   * @param y The place from the top edge of the screen, from 0 to 1, or negative for no place.
   */
  public void playAuditory(
      int resId, float rate, float volume, float x, float y, @Nullable EventId eventId) {
    if (x < 0 || y < 0) {
      playAuditory(resId, rate, volume, eventId);
      return;
    }
    if (!mAuditoryEnabled || resId == 0 || isMuted(mMutedAuditoryNames, resId)) {
      return;
    }
    LogUtils.v(TAG, "playAuditory() resId=%d x=%.2f y=%.2f eventId=%s", resId, x, y, eventId);

    float adjustedVolume = volume * mVolumeAdjustment;
    if (shouldPlayIn3d()) {
      if (mSpatialSoundPlayer == null) {
        mSpatialSoundPlayer = new SpatialSoundPlayer(mContext);
      }
      mSpatialSoundPlayer.play(resId, x, y, adjustedVolume);
    } else {
      // Full volume in the middle, fading out of the far speaker towards either edge.
      float pan = Math.max(-1f, Math.min(1f, (x - 0.5f) * 2));
      playFromPool(
          resId,
          rate,
          adjustedVolume * Math.min(1f, 1 - pan),
          adjustedVolume * Math.min(1f, 1 + pan));
    }
  }

  private void playFromPool(int resId, float rate, float leftVolume, float rightVolume) {
    int soundId = mSoundIds.get(resId);

    if (soundId != 0) {
      new EarconsPlayTask(mSoundPool, soundId, leftVolume, rightVolume, rate).execute();
    } else {
      // The sound could not be played from the cache. Start loading the sound into the
      // SoundPool for future use, and use a listener to play the sound ASAP.
      mSoundPool.setOnLoadCompleteListener(
          (soundPool, sampleId, status) -> {
            if (mAuditoryEnabled && sampleId != 0) {
              new EarconsPlayTask(mSoundPool, sampleId, leftVolume, rightVolume, rate).execute();
            }
          });
      mSoundIds.put(resId, mSoundPool.load(mContext, resId, 1));
    }
  }

  private boolean shouldPlayIn3d() {
    switch (mSpatialMode) {
      case SPATIAL_3D:
        return true;
      case SPATIAL_3D_WITH_HEADPHONES:
        return isHeadphoneOutput();
      default:
        return false;
    }
  }

  /** Returns whether feedback sounds go to headphones, wired or wireless, or hearing aids. */
  private boolean isHeadphoneOutput() {
    AudioManager audioManager = mContext.getSystemService(AudioManager.class);
    if (audioManager == null) {
      return false;
    }
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
      // Where feedback really goes, so a headset that is connected but not in use does not count.
      List<AudioDeviceInfo> devices = audioManager.getAudioDevicesForAttributes(FEEDBACK_ATTRIBUTES);
      for (AudioDeviceInfo device : devices) {
        if (isHeadphone(device)) {
          return true;
        }
      }
      return false;
    }
    for (AudioDeviceInfo device : audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS)) {
      if (isHeadphone(device)) {
        return true;
      }
    }
    return false;
  }

  private static boolean isHeadphone(AudioDeviceInfo device) {
    switch (device.getType()) {
      case AudioDeviceInfo.TYPE_WIRED_HEADPHONES:
      case AudioDeviceInfo.TYPE_WIRED_HEADSET:
      case AudioDeviceInfo.TYPE_BLUETOOTH_A2DP:
      case AudioDeviceInfo.TYPE_USB_HEADSET:
      case AudioDeviceInfo.TYPE_HEARING_AID:
      case AudioDeviceInfo.TYPE_BLE_HEADSET:
        return true;
      default:
        return false;
    }
  }

  /** Interrupts all ongoing feedback. */
  public void interrupt() {
    // TODO: Stop all sounds.
    mVibrator.cancel();
  }

  /**
   * Releases all resources held by the feedback controller and clears the shared instance. No calls
   * should be made to this instance after calling this method.
   */
  public void shutdown() {
    mHapticFeedbackListeners.clear();
    mSoundPool.release();
    if (mSpatialSoundPlayer != null) {
      mSpatialSoundPlayer.shutdown();
      mSpatialSoundPlayer = null;
    }
    mVibrator.cancel();
    mAuditoryEnabled = false;
    mHapticEnabled = false;
  }

  /**
   * Sets whether to enable or disable the haptic feedback.
   *
   * @param enabled Whether haptic feedback should be enabled.
   */
  public void setHapticEnabled(boolean enabled) {
    mHapticEnabled = enabled;
  }

  /**
   * Sets whether to enable or disable the auditory feedback.
   *
   * @param enabled Whether auditory feedback should be enabled.
   */
  public void setAuditoryEnabled(boolean enabled) {
    mAuditoryEnabled = enabled;
  }

  /**
   * Sets the sounds to skip while auditory feedback is on.
   *
   * @param resourceNames Resource entry names, such as {@code "focus"} for {@code R.raw.focus}.
   */
  public void setMutedAuditory(Set<String> resourceNames) {
    mMutedAuditoryNames = new HashSet<>(resourceNames);
  }

  /**
   * Sets the vibration patterns to skip while haptic feedback is on.
   *
   * @param resourceNames Resource entry names, such as {@code "view_clicked_pattern"}.
   */
  public void setMutedHaptic(Set<String> resourceNames) {
    mMutedHapticNames = new HashSet<>(resourceNames);
  }

  private boolean isMuted(Set<String> mutedNames, int resId) {
    if (mutedNames.isEmpty()) {
      return false;
    }
    String name = mResourceNames.get(resId);
    if (name == null) {
      try {
        name = mResources.getResourceEntryName(resId);
      } catch (NotFoundException e) {
        return false;
      }
      mResourceNames.put(resId, name);
    }
    return mutedNames.contains(name);
  }

  /**
   * Sets the current volume adjustment for auditory feedback.
   *
   * @param adjustment The amount by which to adjust the volume of auditory feedback. 0.0 mutes the
   *     feedback while 1.0 plays it at its original volume.
   */
  public void setVolumeAdjustment(float adjustment) {
    mVolumeAdjustment = adjustment;
  }

  /**
   * Sets how sounds with a place on the screen are played.
   *
   * @param mode {@link #SPATIAL_STEREO}, {@link #SPATIAL_3D} or {@link
   *     #SPATIAL_3D_WITH_HEADPHONES}.
   */
  public void setSpatialMode(int mode) {
    mSpatialMode = mode;
  }

  /**
   * Provides vibration and sound feedback to acknowledge the completion of an action (e.g. item
   * selection in Switch Access, gesture completion in TalkBack, etc.).
   */
  public void playActionCompletionFeedback() {
    playHaptic(R.array.window_state_pattern, EVENT_ID_UNTRACKED);
    playAuditory(R.raw.window_state, EVENT_ID_UNTRACKED);
  }

  private static SoundPool createSoundPool() {
    return new SoundPool.Builder()
        .setMaxStreams(MAX_STREAMS)
        .setAudioAttributes(FEEDBACK_ATTRIBUTES)
        .build();
  }

  /**
   * Some features, such as the tap detector, may be affected by haptic feedback and want to know
   * when we initiate it.
   */
  public interface HapticFeedbackListener {

    /**
     * Alerts the listener that haptic feedback is about to start.
     *
     * @param currentNanoTime The current system time.
     */
    void onHapticFeedbackStarting(long currentNanoTime);
  }
}
