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

package com.google.android.accessibility.talkback.dialog;

import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Build;
import android.provider.Settings;
import android.view.LayoutInflater;
import android.view.View;
import com.google.android.accessibility.talkback.R;
import com.google.android.accessibility.talkback.utils.NotificationUtils;
import com.google.android.accessibility.utils.FeatureSupport;
import com.google.android.accessibility.utils.FormFactorUtils;
import com.google.android.libraries.accessibility.utils.log.LogUtils;

/**
 * Asks the user once to allow notifications, which Android 13 and later only show with the user's
 * permission. Without it, the update check finds new builds but its notification never shows.
 *
 * <p>Backtalk targets Android 11, and Android ignores the permission request of an app that
 * targets Android 12L or lower. So this dialog explains why notifications matter, and opens
 * Backtalk's notification settings, where the user turns them on. Once Backtalk targets Android 13
 * or later, TalkBack's own request at start-up gets Android's prompt, so this dialog isn't shown.
 */
public class NotificationPermissionDialog extends BaseDialog {
  private static final String TAG = "NotificationPermissionDialog";

  /** Set once the dialog has been shown, so that it is only ever shown once. */
  public static final String PREF_ASKED = "notification_permission_asked";

  public NotificationPermissionDialog(Context context) {
    super(context, R.string.notification_permission_dialog_title, /* pipeline= */ null);
    setPositiveButtonStringRes(R.string.notification_permission_dialog_open_settings);
    setNegativeButtonStringRes(R.string.notification_permission_dialog_not_now);
  }

  /**
   * Returns whether to ask: notifications need permission on this phone, Android ignores
   * Backtalk's own request for it, Backtalk doesn't have it, and the user hasn't been asked before.
   */
  public static boolean shouldAsk(Context context, SharedPreferences prefs) {
    return FeatureSupport.postNotificationsPermission()
        && context.getApplicationInfo().targetSdkVersion < Build.VERSION_CODES.TIRAMISU
        && !FormFactorUtils.isAndroidTv()
        && !NotificationUtils.hasPostNotificationPermission(context)
        && !prefs.getBoolean(PREF_ASKED, false);
  }

  /** Shows the dialog, and remembers that the user was asked. */
  public static void show(Context context, SharedPreferences prefs) {
    prefs.edit().putBoolean(PREF_ASKED, true).apply();
    new NotificationPermissionDialog(context).showDialog();
  }

  @Override
  public void handleDialogClick(int buttonClicked) {
    if (buttonClicked != DialogInterface.BUTTON_POSITIVE) {
      return;
    }
    Intent intent =
        new Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
            .putExtra(Settings.EXTRA_APP_PACKAGE, context.getPackageName())
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
    try {
      context.startActivity(intent);
    } catch (ActivityNotFoundException e) {
      // Some phones, such as watches, have no notification settings screen for one app.
      LogUtils.w(TAG, "Cannot open the notification settings: %s", e);
      context.startActivity(
          new Intent(
                  Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                  Uri.fromParts("package", context.getPackageName(), null))
              .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
    }
  }

  @Override
  public void handleDialogDismiss() {}

  @Override
  public String getMessageString() {
    return context.getString(R.string.notification_permission_dialog_message);
  }

  @Override
  public View getCustomizedView(LayoutInflater inflater) {
    return null;
  }
}
