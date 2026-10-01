# Backtalk

Backtalk is a fork of [Google's TalkBack](https://github.com/google/talkback), the screen reader for blind and visually-impaired users of Android. It adds fixes and features on top of Google's source releases. It is not affiliated with Google.

## Goals

*   Make Backtalk faster and more responsive for people who use their phone quickly.
*   Bring back useful behavior from older TalkBack and other screen readers.
*   Keep the build working on Windows, Linux, and macOS.
*   Move the code to Kotlin over time. New code is written in Kotlin. Google's Java files are converted only when they already need large changes, so that new Google releases stay easy to merge.
*   Stay close to Google's releases, and merge each new one.

## Changes from TalkBack

### Speed

*   **Time between taps setting.** In Backtalk settings, go to **Advanced settings > Reduce delay > Time between taps** to set how long Backtalk waits for another tap. The default is 0.25 seconds, and you can lower it to 0.1 seconds. A shorter time makes all multi-tap gestures respond faster, but a slow double tap can count as two single taps.
*   **Less lag while scrolling.** After each scroll event, TalkBack searched the list for a new item to focus. This blocked touch exploration and could make gestures fail. Backtalk now does this search once, after scrolling stops.
*   **Faster swiping in lists.** To find the next item in a list, TalkBack built the reading order of the whole screen three times: once to check whether you were at the end of the list, once to find the item, and once to check whether the item needed scrolling into view. Backtalk now builds it once and reuses it.
*   **Faster swiping on screens that do not change.** TalkBack asked the app for every item on the screen on each swipe, and waited for each answer. Backtalk now keeps the reading order between swipes, and builds it again only when the screen changes or Backtalk scrolls or clicks. In a test on a Realme phone, 3 of 4 swipes reused the order, and the longest pause during swiping went from about 450 ms to about 150 ms.

### Screen and brightness

*   **Brightness reading control.** Swipe up or down to change the screen brightness in steps of about 10%. This works when the screen is hidden, so you can set the brightness before you give the phone to someone. The first time you use it, Backtalk asks for the "Modify system settings" permission.
*   **Shorter hide screen message.** If you turn off **Always show this** in the hide screen dialog, Backtalk only says "Screen hidden" and skips the instructions for showing the screen again.
*   **Hide screen brightness fix.** For 3 minutes after you hide the screen, TalkBack set the screen to full brightness. Backtalk keeps your brightness.
*   **Proximity sensor off by default.** Backtalk does not stop speech when something covers the proximity sensor. To turn it back on, go to **Advanced settings > Cover proximity sensor to stop speech** in Backtalk settings.

### Gestures

*   **New default gestures.**
    *   Tap with 4 fingers: go back.
    *   Double-tap with 4 fingers: go home.
    *   Triple-tap with 4 fingers: open recent apps.
    *   Double-tap and hold with 4 fingers: open the notification shade.
    *   Double-tap and hold with 2 fingers: switch to the braille keyboard.
    *   Tap and hold with 3 fingers: pass through the next gesture.
    *   Triple-tap with 3 fingers: copy the last spoken phrase.
    *   Triple-tap and hold with 3 fingers: paste.
    *   Selection mode has no gesture.
*   **Status gesture.** Triple-tap with 2 fingers to hear what the status bar shows: the time, battery, Wi-Fi, and mobile signal, and the ringer, Do Not Disturb, and airplane mode when they are not in their usual state. To hear the Wi-Fi network name, allow location access when Backtalk asks the first time. To choose what it says and in what order, go to **Status readout** in Backtalk settings. Each item has **Move up** and **Move down** actions. This action is also in the gesture list as **Speak status**. To read from the current item, use **Read from next item** in the Backtalk menu.

### Backtalk menu

*   **Shorter menu by default.** These items are off by default: Actions, Screen search, Add or edit labels, Describe text formatting, Copy last spoken phrase, Spoken language, Voice commands, Keyboard shortcuts, and Braille display settings. Actions stay available with the actions reading control. Text-to-speech stays on, so that you can get to speech settings if your speech engine crashes. To turn them back on, go to **Customize menus** in Backtalk settings.
*   **Circle menu.** The Backtalk menu can show as a circle in the middle of the screen, like in TalkBack 8.1 and earlier. Each item is a slice of the screen around the middle. Slide to an item, and lift to select it. Lift in the middle of the circle to close the menu. Items that open more items show them in a new circle. To turn it on, go to **Customize menus** in Backtalk settings and turn on **Circle menu**.

### Speech

*   **Speak notifications setting.** To stop Backtalk from reading new notifications when they arrive, go to **Verbosity** in Backtalk settings and turn off **Speak notifications**. Incoming calls are still read, and you can still read notifications in the notification shade.
*   **No "collapsed" on notifications.** Backtalk does not say "collapsed" for each notification on the lock screen and in the notification shade. It still says "expanded" when you open one, and it still says "collapsed" in other apps.

### Sound and vibration

*   **Individual sounds and vibrations.** To turn off one sound or one vibration and keep the rest, go to **Sound and vibration** in Backtalk settings and open **Individual sounds and vibrations**. There is a switch for each sound and each vibration, including the braille display and braille keyboard sounds. To hear or feel one, open the actions menu on its switch and choose **Preview**. Changing a switch plays nothing. **Sound feedback** and **Vibration feedback** still turn all of them off at once.

### Direct touch

Audio games need raw touch, but Explore by Touch captures taps and swipes before the game sees them. With direct touch, Backtalk passes your touches straight to the games you choose, and you do not have to suspend Backtalk.

Direct touch is all of [NVGT Bridge](https://github.com/trypsynth/nvgt-bridge), the accessibility service that gives audio games direct touch, built into Backtalk. It works better in games than running NVGT Bridge next to Backtalk, because Backtalk is the screen reader and already knows what is on the screen:

*   **One service, not two.** There is nothing extra to install, sideload, or turn on in Accessibility settings, and no second service that can undo Backtalk's touch setting. Direct touch shares that setting with the pass-through gesture and the braille keyboard, so none of them cancels another.
*   **No searching the screen.** NVGT Bridge searches each window's view tree for dialogs and text fields, and only looks five levels deep. Backtalk already follows the windows, the keyboard and the screen state, so it hands touch back to your screen reader as soon as they change, and there is no depth limit.
*   **Announcements through Backtalk.** "Direct touch on" and "Direct touch off" are spoken by Backtalk's own speech, even while the game is playing audio, and it can vibrate on each change.

To use it:

*   **Choose your games.** Go to **Direct touch** in TalkBack settings and turn on each game in the **Apps** list. Backtalk says "Direct touch on" when a game you chose comes to the front, and "Direct touch off" when it gives touch back. You can turn the speech off, and turn on a short vibration instead or as well.
*   **Backtalk takes touch back when it is needed.** Touch returns to Backtalk when a dialog appears, another app opens on top of the game, you open the notification shade or quick settings, a text field takes focus, or the screen turns off. It goes back to the game when they are gone. The keyboard always stays with Backtalk, so you can explore it while the rest of the screen stays in direct touch.
*   **Direct typing.** By default the keyboard area keeps working with Backtalk. For a game that draws its own keyboard, open the actions menu on the game in the list and choose **Turn on direct typing**.
*   **Quick settings tile.** Add the **Direct touch** tile to pause and resume direct touch without leaving your game.
*   **Backup and restore.** **Back up settings** saves your choices to a file, and **Restore settings** loads them on another device.
*   **For game developers.** Add this `<meta-data>` tag inside your `<application>` or your main `<activity>`, and Backtalk turns your game on the first time it sees it. Players can still turn it off.

    ```xml
    <meta-data
        android:name="dev.nvgt.capability.DIRECT_TOUCH"
        android:value="true" />
    ```

*   **Not covered.** A dialog or text field that a game draws inside its own screen is invisible to Backtalk. Use the quick settings tile to pause direct touch when that happens.

### Braille keyboard

*   **Swap top and bottom dots.** In braille keyboard settings, **Swap top and bottom dots** makes dot 1 trade places with dot 3, and dot 4 with dot 6. **Reverse dots** is now called **Swap left and right dots**. You can turn on both.
*   **Skip the tutorial.** The first page of the braille keyboard tutorial has a **Skip tutorial** button, which opens the keyboard right away.
*   **Better haptics.** The braille keyboard uses the same crisp vibration effects as the rest of Backtalk. Submitting text feels the same as closing or switching the keyboard. Deleting in an empty field gives a soft vibration that fades out, so that you know there was nothing to delete.
*   **Navigation stays in the text field.** If you swiped to another control while the braille keyboard opened, moving by character, word, or line read that control instead of the text field. The braille keyboard now moves Backtalk's focus back to the text field before each command.

## Build

You need JDK 17 or newer, the Android SDK with platform 37, and NDK 27.3.13750724. The Gradle wrapper downloads the correct Gradle version, so you do not need to install Gradle.

### Linux or macOS

Set `ANDROID_SDK` to your SDK path, then run `./build.sh`. This produces an APK file.

### Windows

Set `ANDROID_HOME` to your SDK path, then run:

```
.\gradlew.bat assemblePhoneDebug
```

### Dependencies

Library and plugin versions are in `gradle/libs.versions.toml`. Renovate opens pull requests to update them each week, and GitHub Actions builds each pull request.

### Image descriptions with Gemini

Google's source release does not include the Gemini settings, so **Describe image** only reads text in images, and **Describe screen** does not work, unless you add your own Gemini API key. Backtalk adds its own support for Describe screen, including follow-up questions. To add a key:

1.  Get an API key from [Google AI Studio](https://aistudio.google.com/apikey).
2.  Add this line to `local.properties` in the project folder: `gemini.api.key=YOUR_KEY`
3.  Optional: to use a different model, add `gemini.model=MODEL_NAME`. The default is `gemini-flash-latest`.
4.  Build and install Backtalk again.

Git ignores `local.properties`, so your key is not committed. The key is built into the APK, so do not share an APK that contains your key.

Images and screenshots that you describe are sent to Google. On the free tier, Google can use this data to improve its products.

## Install

Install the APK on your device with adb.

On Windows, `.\deploy.ps1` builds the APK and installs it with adb. This script needs PowerShell 7. To install the last build without building again, use `-SkipBuild`.

Backtalk installs as `com.android.talkback`, so it does not replace Google's TalkBack. The two apps have separate settings.

## Updates

Each change to Backtalk is built on GitHub as a development build, on the [latest release](https://github.com/trypsynth/backtalk/releases/tag/latest) page. Backtalk checks for a new build when it starts and about once a day. When there is one, it shows a notification with the list of changes. Tap the notification to download and install the new build. The first time, Android asks you to allow Backtalk to install apps.

To check now, go to **Check for updates** in Backtalk settings. To stop the daily checks, turn off **Automatically check for updates**.

Android only installs an update that is signed with the same key as the installed app. If you build Backtalk yourself, your build is signed with your own debug key, so it cannot be updated by the builds from GitHub. To use them, uninstall your build first.

## Run

After you install Backtalk, go to **Settings > Accessibility**. Backtalk is listed as **Backtalk** and is off by default. Turn off Google's TalkBack first, then turn on Backtalk.

## Debug tools

Debug builds include tools to find lag:

*   The `BacktalkStall` logcat tag logs each time the main thread is blocked for more than 100 ms, with the code that was running.
*   The `BacktalkGesture` logcat tag logs touch state changes and each gesture that Backtalk detects.
*   Event processing has trace sections, which show in [Perfetto](https://perfetto.dev) system traces.
