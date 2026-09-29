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

package com.google.android.accessibility.talkback.update

import android.app.Activity
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.widget.Toast
import androidx.annotation.StringRes
import androidx.core.app.NotificationCompat
import androidx.core.content.IntentCompat
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import com.google.android.accessibility.talkback.BuildConfig
import com.google.android.accessibility.talkback.R
import com.google.android.accessibility.talkback.utils.NotificationUtils
import com.google.android.accessibility.utils.SharedPreferencesUtils
import com.google.android.accessibility.utils.material.A11yAlertDialogWrapper
import com.google.android.libraries.accessibility.utils.log.LogUtils
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/**
 * Checks for new development builds, shows a notification when one is available, and installs it.
 *
 * Automatic checks run when the service starts, and then about once a day while it runs.
 */
object Updater {
  private const val TAG = "Updater"
  private const val NOTIFICATION_ID = 0x0BAC7A1C
  private const val PREF_LAST_CHECK_TIME = "update_last_check_time"
  private const val PREF_LAST_NOTIFIED_BUILD = "update_last_notified_build"
  private const val APK_FILE_NAME = "backtalk.apk"
  private val CHECK_INTERVAL_MS = TimeUnit.DAYS.toMillis(1)
  private val POLL_INTERVAL_MS = TimeUnit.HOURS.toMillis(3)
  // Gives the network time to come up when the service starts at boot.
  private val STARTUP_DELAY_MS = TimeUnit.SECONDS.toMillis(30)
  // Lets the screen that opens be announced before the toast.
  private const val TOAST_AFTER_SCREEN_DELAY_MS = 1500L

  private val mainHandler = Handler(Looper.getMainLooper())
  private val executor = Executors.newSingleThreadExecutor()
  private var serviceContext: Context? = null

  private val startupCheck = Runnable { serviceContext?.let { checkAutomatically(it, true) } }

  private val periodicCheck =
    object : Runnable {
      override fun run() {
        val context = serviceContext ?: return
        checkAutomatically(context, false)
        mainHandler.postDelayed(this, POLL_INTERVAL_MS)
      }
    }

  /** Starts automatic checks. Called when the service starts. */
  @JvmStatic
  fun startAutomaticChecks(context: Context) {
    serviceContext = context.applicationContext
    mainHandler.removeCallbacks(startupCheck)
    mainHandler.removeCallbacks(periodicCheck)
    mainHandler.postDelayed(startupCheck, STARTUP_DELAY_MS)
    mainHandler.postDelayed(periodicCheck, POLL_INTERVAL_MS)
  }

  /** Stops automatic checks. Called when the service stops. */
  @JvmStatic
  fun stopAutomaticChecks() {
    mainHandler.removeCallbacks(startupCheck)
    mainHandler.removeCallbacks(periodicCheck)
    serviceContext = null
  }

  /** Sets up the "Check for updates" preference in the main settings screen. */
  @JvmStatic
  fun setUpPreferences(fragment: PreferenceFragmentCompat) {
    val preference =
      fragment.findPreference<Preference>(fragment.getString(R.string.pref_check_for_updates_key))
        ?: return
    preference.summary =
      fragment.getString(R.string.summary_pref_check_for_updates, BuildConfig.COMMIT_COUNT)
    preference.setOnPreferenceClickListener {
      fragment.activity?.let { checkManually(it) }
      true
    }
  }

  private fun isAutomaticCheckEnabled(context: Context): Boolean =
    SharedPreferencesUtils.getBooleanPref(
      SharedPreferencesUtils.getSharedPreferences(context),
      context.resources,
      R.string.pref_auto_update_check_key,
      R.bool.pref_auto_update_check_default,
    )

  private fun checkAutomatically(context: Context, ignoreInterval: Boolean) {
    if (!isAutomaticCheckEnabled(context)) {
      return
    }
    val prefs = SharedPreferencesUtils.getSharedPreferences(context)
    val now = System.currentTimeMillis()
    val lastCheck = prefs.getLong(PREF_LAST_CHECK_TIME, 0)
    // A clock that moved back also makes a check due.
    if (!ignoreInterval && now >= lastCheck && now - lastCheck < CHECK_INTERVAL_MS) {
      return
    }
    executor.execute {
      val update =
        try {
          UpdateChecker.check(context)
        } catch (e: Exception) {
          LogUtils.w(TAG, "Automatic update check failed: %s", e)
          return@execute
        }
      prefs.edit().putLong(PREF_LAST_CHECK_TIME, System.currentTimeMillis()).apply()
      if (update != null && update.build > prefs.getInt(PREF_LAST_NOTIFIED_BUILD, 0)) {
        prefs.edit().putInt(PREF_LAST_NOTIFIED_BUILD, update.build).apply()
        showNotification(context, update)
      }
    }
  }

  /** Checks now, and offers to install a newer build. */
  @JvmStatic
  fun checkManually(activity: Activity) {
    val context = activity.applicationContext
    toast(context, R.string.update_checking)
    executor.execute {
      val update =
        try {
          UpdateChecker.check(context)
        } catch (e: Exception) {
          LogUtils.w(TAG, "Update check failed: %s", e)
          mainHandler.post { toast(context, R.string.update_check_failed) }
          return@execute
        }
      SharedPreferencesUtils.getSharedPreferences(context)
        .edit()
        .putLong(PREF_LAST_CHECK_TIME, System.currentTimeMillis())
        .apply()
      mainHandler.post {
        when {
          update == null -> toast(context, R.string.update_up_to_date)
          activity.isFinishing || activity.isDestroyed -> showNotification(context, update)
          else -> showUpdateDialog(activity, update)
        }
      }
    }
  }

  private fun describe(context: Context, update: UpdateInfo): String {
    val summary = context.getString(R.string.update_notification_text, update.build)
    return (listOf(summary) + update.changes).joinToString("\n")
  }

  private fun showUpdateDialog(activity: Activity, update: UpdateInfo) {
    val message =
      update.changes
        .joinToString("\n")
        .ifEmpty { activity.getString(R.string.update_notification_text, update.build) }
    A11yAlertDialogWrapper.materialDialogBuilder(activity)
      .setTitle(activity.getString(R.string.update_dialog_title, update.build))
      .setMessage(message)
      .setPositiveButton(R.string.update_dialog_install) { _, _ ->
        install(activity, update.downloadUrl)
      }
      .setNegativeButton(android.R.string.cancel) { dialog, _ -> dialog.dismiss() }
      .setCancelable(true)
      .create()
      .show()
  }

  private fun showNotification(context: Context, update: UpdateInfo) {
    val intent =
      Intent(context, UpdateReceiver::class.java)
        .setAction(UpdateReceiver.ACTION_INSTALL)
        .putExtra(UpdateReceiver.EXTRA_DOWNLOAD_URL, update.downloadUrl)
    val pendingIntent =
      PendingIntent.getBroadcast(
        context,
        0,
        intent,
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
      )
    val title = context.getString(R.string.update_notification_title)
    val text = context.getString(R.string.update_notification_text, update.build)
    val notification =
      NotificationUtils.createDefaultNotificationBuilder(context)
        .setTicker(title)
        .setContentTitle(title)
        .setContentText(text)
        .setStyle(NotificationCompat.BigTextStyle().bigText(describe(context, update)))
        .setContentIntent(pendingIntent)
        .setAutoCancel(true)
        .build()
    context.getSystemService(NotificationManager::class.java)?.notify(NOTIFICATION_ID, notification)
  }

  /** Downloads the APK and opens the system install prompt. */
  @JvmStatic
  fun install(context: Context, downloadUrl: String) {
    val appContext = context.applicationContext
    if (!appContext.packageManager.canRequestPackageInstalls()) {
      val intent =
        Intent(
            Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
            Uri.parse("package:" + appContext.packageName),
          )
          .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
      try {
        appContext.startActivity(intent)
      } catch (e: ActivityNotFoundException) {
        LogUtils.w(TAG, "Cannot open the install apps setting: %s", e)
      }
      mainHandler.postDelayed(
        { toast(appContext, R.string.update_allow_install, Toast.LENGTH_LONG) },
        TOAST_AFTER_SCREEN_DELAY_MS,
      )
      return
    }
    toast(appContext, R.string.update_downloading)
    executor.execute {
      try {
        val apk = download(appContext, downloadUrl)
        try {
          installApk(appContext, apk)
        } finally {
          apk.delete()
        }
      } catch (e: Exception) {
        LogUtils.e(TAG, "Cannot install the update: %s", e)
        mainHandler.post { toast(appContext, R.string.update_install_failed) }
      }
    }
  }

  private fun download(context: Context, url: String): File {
    val directory = File(context.cacheDir, "update")
    directory.mkdirs()
    val file = File(directory, APK_FILE_NAME)
    val connection = UpdateChecker.openConnection(context, url)
    try {
      val code = connection.responseCode
      if (code != HttpURLConnection.HTTP_OK) {
        throw IOException("Download returned HTTP $code")
      }
      connection.inputStream.use { input -> file.outputStream().use { input.copyTo(it) } }
    } finally {
      connection.disconnect()
    }
    return file
  }

  private fun installApk(context: Context, apk: File) {
    val installer = context.packageManager.packageInstaller
    val params =
      PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL).apply {
        setAppPackageName(context.packageName)
      }
    val sessionId = installer.createSession(params)
    try {
      installer.openSession(sessionId).use { session ->
        session.openWrite(APK_FILE_NAME, 0, apk.length()).use { output ->
          apk.inputStream().use { it.copyTo(output) }
          session.fsync(output)
        }
        val intent =
          Intent(context, UpdateReceiver::class.java)
            .setAction(UpdateReceiver.ACTION_INSTALL_STATUS)
        // The installer fills in the status, so the intent must be mutable.
        val flags =
          PendingIntent.FLAG_UPDATE_CURRENT or
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) PendingIntent.FLAG_MUTABLE else 0
        val statusIntent = PendingIntent.getBroadcast(context, sessionId, intent, flags)
        session.commit(statusIntent.intentSender)
      }
    } catch (e: Exception) {
      installer.abandonSession(sessionId)
      throw e
    }
  }

  /** Handles a status update from the install session. */
  fun onInstallStatus(context: Context, intent: Intent) {
    val appContext = context.applicationContext
    val status = intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE)
    when (status) {
      PackageInstaller.STATUS_PENDING_USER_ACTION -> {
        val confirmIntent =
          IntentCompat.getParcelableExtra(intent, Intent.EXTRA_INTENT, Intent::class.java)
        if (confirmIntent == null) {
          toast(appContext, R.string.update_install_failed)
          return
        }
        try {
          appContext.startActivity(confirmIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        } catch (e: ActivityNotFoundException) {
          LogUtils.e(TAG, "Cannot open the install prompt: %s", e)
          toast(appContext, R.string.update_install_failed)
        }
      }
      // The system stops Backtalk to replace it, and restarts the service if it was on.
      PackageInstaller.STATUS_SUCCESS -> {}
      PackageInstaller.STATUS_FAILURE_ABORTED -> toast(appContext, R.string.update_install_canceled)
      else -> {
        LogUtils.e(
          TAG,
          "Install failed with status %d: %s",
          status,
          intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE),
        )
        toast(appContext, R.string.update_install_failed)
      }
    }
  }

  private fun toast(context: Context, @StringRes message: Int, duration: Int = Toast.LENGTH_SHORT) {
    Toast.makeText(context, message, duration).show()
  }
}
