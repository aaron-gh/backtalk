package com.google.android.accessibility.talkback.soundthemes

import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import com.google.android.accessibility.utils.SharedPreferencesUtils
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.Shadows.shadowOf
import android.os.Looper
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowAlertDialog

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class Build600ActivityTest {
  private fun views(view: View): List<View> = listOf(view) +
    if (view is ViewGroup) (0 until view.childCount).flatMap { views(view.getChildAt(it)) } else emptyList()
  private fun buttons(activity: Build600Activity) = views(activity.window.decorView).filterIsInstance<Button>()
  private fun click(activity: Build600Activity, text: String) = buttons(activity).first { it.text.toString() == text }.performClick()

  @Test fun pagesNavigateAndFinishMarksWalkthroughSeen() {
    val controller = Robolectric.buildActivity(Build600Activity::class.java).setup()
    val activity = controller.get()
    assertFalse(buttons(activity).any { it.text == "Previous page" })
    repeat(6) { click(activity, "Next page") }
    assertTrue(views(activity.window.decorView).filterIsInstance<TextView>().any { it.text == "Page 7 of 7" })
    assertFalse(buttons(activity).any { it.text == "Close" || it.text == "Next page" })
    assertTrue(buttons(activity).any { it.text == "Test rising vibration" })
    click(activity, "Previous page")
    click(activity, "Next page")
    click(activity, "Finish")
    assertTrue(activity.isFinishing)
    assertTrue(SharedPreferencesUtils.getSharedPreferences(activity).getBoolean("whats_new_build_600_seen", false))
    controller.pause().stop().destroy()
  }

  @Test fun closeRequiresConfirmation() {
    val controller = Robolectric.buildActivity(Build600Activity::class.java).setup()
    val activity = controller.get()
    click(activity, "Close")
    assertFalse(activity.isFinishing)
    ShadowAlertDialog.getLatestAlertDialog().getButton(android.content.DialogInterface.BUTTON_NEGATIVE).performClick()
    shadowOf(Looper.getMainLooper()).idle()
    assertFalse(activity.isFinishing)
    click(activity, "Close")
    ShadowAlertDialog.getLatestAlertDialog().getButton(android.content.DialogInterface.BUTTON_POSITIVE).performClick()
    shadowOf(Looper.getMainLooper()).idle()
    assertTrue(activity.isFinishing)
    controller.pause().stop().destroy()
  }
}
