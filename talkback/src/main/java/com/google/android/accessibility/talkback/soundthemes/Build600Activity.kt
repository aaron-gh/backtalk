/* Copyright 2026 Backtalk contributors. Licensed under the Apache License, Version 2.0. */
package com.google.android.accessibility.talkback.soundthemes

import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import android.app.AlertDialog
import android.app.KeyguardManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.VibrationEffect
import android.os.Vibrator
import androidx.core.view.ViewCompat
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import com.google.android.accessibility.talkback.R
import com.google.android.accessibility.talkback.individualfeedback.IndividualFeedbackSettings
import com.google.android.accessibility.talkback.individualfeedback.SoundPreview
import com.google.android.accessibility.utils.SharedPreferencesUtils
import com.google.android.accessibility.utils.output.AccessibilityVibration

/** One-time, accessible release walkthrough, with explicit vibration demonstrations. */
class Build600Activity : ComponentActivity() {
  private var page = 0
  private val preview = SoundPreview()
  private var samplePlaying = false

  override fun onCreate(state: Bundle?) {
    super.onCreate(state)
    onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
      override fun handleOnBackPressed() { confirmClose() }
    })
    page = (state?.getInt("page") ?: 0).coerceIn(0, PAGES.lastIndex)
    render()
  }

  override fun onSaveInstanceState(state: Bundle) {
    state.putInt("page", page)
    super.onSaveInstanceState(state)
  }

  private fun render() {
    val content = LinearLayout(this).apply {
      orientation = LinearLayout.VERTICAL
      val padding = (24 * resources.displayMetrics.density).toInt()
      setPadding(padding, padding, padding, padding)
    }
    val scroll = ScrollView(this).apply { addView(content) }
    setContentView(scroll)
    title = getString(R.string.build600_title)
    val heading = TextView(this).apply {
      text = getString(PAGES[page].first)
      textSize = 24f
      ViewCompat.setAccessibilityHeading(this, true)
    }
    content.addView(heading)
    content.addView(TextView(this).apply {
      text = getString(R.string.build600_page, page + 1, PAGES.size)
      textSize = 18f
    })
    content.addView(TextView(this).apply { text = getString(PAGES[page].second); textSize = 18f })
    if (page == PAGES.lastIndex) {
      button(content, R.string.build600_sample_short) { sample(longArrayOf(50), intArrayOf(255)) }
      button(content, R.string.build600_sample_rise) {
        sample(longArrayOf(70, 70, 70, 70), intArrayOf(200, 218, 236, 255))
      }
      button(content, R.string.build600_sample_rhythm) {
        sample(longArrayOf(60, 120, 60, 120, 100), intArrayOf(255, 0, 255, 0, 255))
      }
      button(content, R.string.build600_sample_click) {
        stopSamples()
        preview.play(this, SharedPreferencesUtils.getSharedPreferences(this),
          IndividualFeedbackSettings.SOUNDS.first { it.resourceNames.contains("tick") })
      }
    }
    if (page > 0) button(content, R.string.build600_previous) { stopSamples(); page--; render() }
    if (page < PAGES.lastIndex) {
      button(content, R.string.build600_next) { stopSamples(); page++; render() }
      button(content, R.string.build600_close) { confirmClose() }
    } else button(content, R.string.build600_finish) { complete() }
    heading.post { heading.sendAccessibilityEvent(android.view.accessibility.AccessibilityEvent.TYPE_VIEW_FOCUSED) }
  }

  private fun button(parent: LinearLayout, label: Int, action: () -> Unit) {
    parent.addView(Button(this).apply { setText(label); setOnClickListener { action() } })
  }

  private fun sample(times: LongArray, amplitudes: IntArray) {
    stopSamples()
    val vibrator = getSystemService(Vibrator::class.java)
    if (vibrator == null || !vibrator.hasVibrator()) {
      Toast.makeText(this, R.string.build600_no_motor, Toast.LENGTH_LONG).show()
      return
    }
    try {
      AccessibilityVibration.play(vibrator, VibrationEffect.createWaveform(times, amplitudes, -1))
      samplePlaying = true
    } catch (error: RuntimeException) {
      Toast.makeText(this, R.string.build600_sample_failed, Toast.LENGTH_LONG).show()
    }
  }

  private fun stopSamples() {
    preview.stop()
    if (samplePlaying) getSystemService(Vibrator::class.java)?.cancel()
    samplePlaying = false
  }

  private fun confirmClose() {
    stopSamples()
    AlertDialog.Builder(this)
      .setTitle(R.string.build600_close_title)
      .setMessage(R.string.build600_close_message)
      .setPositiveButton(R.string.build600_close) { _, _ -> complete() }
      .setNegativeButton(R.string.build600_keep_reading, null)
      .show()
  }

  private fun complete() {
    SharedPreferencesUtils.getSharedPreferences(this).edit().putBoolean(SEEN, true).apply()
    stopSamples()
    finish()
  }

  override fun onPause() { stopSamples(); super.onPause() }

  companion object {
    private const val SEEN = "whats_new_build_600_seen"
    private var launching = false
    private val PAGES = listOf(
      R.string.build600_title to R.string.build600_intro,
      R.string.build600_create_title to R.string.build600_create,
      R.string.build600_edit_title to R.string.build600_edit,
      R.string.build600_save_title to R.string.build600_save,
      R.string.build600_auto_title to R.string.build600_auto,
      R.string.build600_all_title to R.string.build600_all,
      R.string.build600_strong_title to R.string.build600_strong,
    )

    @JvmStatic
    fun showIfNeeded(context: Context) {
      if (launching || SharedPreferencesUtils.getSharedPreferences(context).getBoolean(SEEN, false)
          || context.getSystemService(KeyguardManager::class.java)?.isKeyguardLocked == true) return
      launching = true
      try {
        context.startActivity(Intent(context, Build600Activity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
      } catch (error: RuntimeException) { launching = false }
    }
  }
}
