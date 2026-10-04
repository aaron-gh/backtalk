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
import android.media.AudioManager;
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
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
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


  //////////////////////////////////////////////////////////////////////////////////////////
  // Member data

  /** The parent context. */
  private final Context mContext;

  /** The resources for this context. */
  private final Resources mResources;

  /** The SoundPool instance for loading sounds and playing previously loaded sounds. */
  private final SoundPool mSoundPool;

  /** How Backtalk's sounds play: as speech, so that they follow the output chosen for speech. */
  private static final AudioAttributes FEEDBACK_ATTRIBUTES =
      new AudioAttributes.Builder()
          .setUsage(AudioAttributes.USAGE_ASSISTANCE_ACCESSIBILITY)
          .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
          .build();

  /** Whether sounds play through {@link LowLatencyAudio}. */
  private volatile boolean mLowLatencyAudio;

  /**
   * Sounds decoded for {@link LowLatencyAudio}, by resource or file, or null while being decoded or
   * if they cannot be.
   */
  private final Map<String, LowLatencyAudio.@Nullable Clip> mClips = new HashMap<>();

  private final ExecutorService mDecoder = Executors.newSingleThreadExecutor();

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

  /** The vibration pattern that plays with each sound, by the sound's resource entry name. */
  private Map<String, Integer> mSoundHaptics = Collections.emptyMap();

  /** Skips an event's own vibration right after its sound vibrated. */
  private final SoundHapticCover mSoundHapticCover = new SoundHapticCover();

  private final Set<HapticFeedbackListener> mHapticFeedbackListeners = new HashSet<>();

  private final @NonNull HashMap<Integer, Long> resIdToLastPlayUptimeMillisec = new HashMap<>();

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
   * Plays the vibration pattern associated with the given resource ID, unless a sound for the same
   * event just played its own vibration, or would have if the user had not turned it off.
   *
   * @param resId The vibration pattern's resource identifier.
   * @return {@code true} if successful.
   */
  public boolean playHaptic(int resId, @Nullable EventId eventId) {
    if (mSoundHapticCover.covers(eventId, SystemClock.uptimeMillis())) {
      LogUtils.v(TAG, "playHaptic() resId=%d skipped, the sound vibrated", resId);
      return false;
    }
    return vibrate(resId, eventId);
  }

  /**
   * Plays the vibration that goes with a sound, whether or not sound feedback is on, so that every
   * sound can be felt as well as heard.
   */
  private void playSoundHaptic(int soundResId, @Nullable EventId eventId) {
    if (!mHapticEnabled || mSoundHaptics.isEmpty()) {
      return;
    }
    @Nullable String name = resourceName(soundResId);
    @Nullable Integer patternResId = name == null ? null : mSoundHaptics.get(name);
    if (patternResId != null) {
      // Even if the user turned this vibration off, the event's own vibration stays quiet.
      vibrate(patternResId, eventId);
      mSoundHapticCover.soundVibrated(eventId, SystemClock.uptimeMillis());
    }
  }

  private boolean vibrate(int resId, @Nullable EventId eventId) {
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
    if (resId != 0) {
      playSoundHaptic(resId, eventId);
    }
    playSound(resId, rate, volume, ignoreVolumeAdjustment, eventId);
  }

  /**
   * Plays a sound without the vibration that goes with it, for sounds that should not be felt,
   * such as repeating progress tones, or whose vibration is someone else's, such as braille.
   */
  public void playAuditoryWithoutHaptic(int resId, @Nullable EventId eventId) {
    playAuditoryWithoutHaptic(resId, 1.0f /* rate */, 1.0f /* volume */, eventId);
  }

  /** Plays a sound with the given rate and volume, without its vibration. */
  public void playAuditoryWithoutHaptic(
      int resId, float rate, float volume, @Nullable EventId eventId) {
    playSound(resId, rate, volume, /* ignoreVolumeAdjustment= */ false, eventId);
  }

  private void playSound(
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
    if (mLowLatencyAudio && playLowLatency(resId, rate, adjustedVolume, adjustedVolume)) {
      return;
    }
    int soundId = mSoundIds.get(resId);

    if (soundId != 0) {
      new EarconsPlayTask(mSoundPool, soundId, adjustedVolume, rate).execute();
    } else {
      // The sound could not be played from the cache. Start loading the sound into the
      // SoundPool for future use, and use a listener to play the sound ASAP.
      mSoundPool.setOnLoadCompleteListener(
          (soundPool, sampleId, status) -> {
            if (mAuditoryEnabled && sampleId != 0) {
              new EarconsPlayTask(mSoundPool, sampleId, adjustedVolume, rate).execute();
            }
          });
      mSoundIds.put(resId, mSoundPool.load(mContext, resId, 1));
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

  /**
   * Sets the vibration pattern that plays with each sound. Each sound's vibration plays even when
   * sound feedback is off.
   *
   * @param patternsBySoundName Pattern resource IDs, by sound resource entry names such as {@code
   *     "focus"} for {@code R.raw.focus}.
   */
  public void setSoundHaptics(Map<String, Integer> patternsBySoundName) {
    mSoundHaptics = new HashMap<>(patternsBySoundName);
  }

  private boolean isMuted(Set<String> mutedNames, int resId) {
    if (mutedNames.isEmpty()) {
      return false;
    }
    @Nullable String name = resourceName(resId);
    return name != null && mutedNames.contains(name);
  }

  private @Nullable String resourceName(int resId) {
    String name = mResourceNames.get(resId);
    if (name == null) {
      try {
        name = mResources.getResourceEntryName(resId);
      } catch (NotFoundException e) {
        return null;
      }
      mResourceNames.put(resId, name);
    }
    return name;
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
   * Provides vibration and sound feedback to acknowledge the completion of an action (e.g. item
   * selection in Switch Access, gesture completion in TalkBack, etc.).
   */
  public void playActionCompletionFeedback() {
    // The sound first, so that its own vibration stands in for the one below.
    playAuditory(R.raw.window_state, EVENT_ID_UNTRACKED);
    playHaptic(R.array.window_state_pattern, EVENT_ID_UNTRACKED);
  }

  private static SoundPool createSoundPool() {
    return new SoundPool.Builder()
        .setMaxStreams(MAX_STREAMS)
        .setAudioAttributes(FEEDBACK_ATTRIBUTES)
        .build();
  }

  /**
   * Plays the sound through the low-latency player, and returns whether it did. A sound plays the
   * usual way the first time, while it is decoded for next time, and whenever it cannot be decoded.
   */
  private boolean playLowLatency(int resId, float rate, float leftVolume, float rightVolume) {
    return playLowLatency(resId, /* path= */ null, rate, leftVolume, rightVolume);
  }

  /**
   * Plays the sound, or the file at {@code path} in its place, through the low-latency player, and
   * returns whether it did.
   */
  private boolean playLowLatency(
      int resId, @Nullable String path, float rate, float leftVolume, float rightVolume) {
    @Nullable LowLatencyAudio player = LowLatencyAudio.get(mContext, FEEDBACK_ATTRIBUTES);
    if (player == null) {
      return false;
    }
    // By file for a file, so that a replaced file is decoded again.
    String key = path != null ? path : "res:" + resId;
    LowLatencyAudio.@Nullable Clip clip;
    synchronized (mClips) {
      clip = mClips.get(key);
      if (clip == null) {
        if (!mClips.containsKey(key)) {
          // Null marks a sound being decoded, or one that cannot be.
          mClips.put(key, null);
          mDecoder.execute(
              () -> {
                AudioDecoder.@Nullable Decoded decoded =
                    path != null ? AudioDecoder.decode(path) : AudioDecoder.decode(mContext, resId);
                if (decoded != null) {
                  LowLatencyAudio.Clip prepared = player.prepare(decoded);
                  synchronized (mClips) {
                    mClips.put(key, prepared);
                  }
                }
              });
        }
        return false;
      }
    }
    player.play(clip, leftVolume, rightVolume, rate);
    return true;
  }

  /**
   * Sets whether sounds play through the low-latency player, which reaches the speaker sooner than
   * the usual way.
   */
  public void setLowLatencyAudio(boolean enabled) {
    mLowLatencyAudio = enabled;
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
