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
*   **Swiping away from long paragraphs.** Some speech engines, such as Gryphon, only stop between the blocks they synthesize, and treat a whole item as one block. When you swiped away from a long paragraph on a web page, the next item could wait up to 850 ms for the engine to stop. To fix this, go to **Text-to-speech** in Backtalk settings and turn on **Send long text a sentence at a time**. Backtalk then sends text longer than 150 characters a sentence at a time, queued one after another, so the engine stops at once. In Chrome on a Samsung phone with Gryphon, web swipes went from as much as 900 ms to at most about 110 ms. It is off by default, because many engines, such as RHVoice, already stop at once, and pause longer between sentences sent separately.
*   **Faster screen changes.** After a window changes, TalkBack waits for it to settle before it says the title, and counted that wait in a way that made 550 ms last about 900 ms. Backtalk counts the real time. **Turn off animations** in Advanced settings turns off animations for the whole phone, so screens change at once and the wait is only 200 ms. TalkBack had disabled the part that turns animations off, so the setting did nothing. Backtalk turns it back on, and turns animations back on when Backtalk is turned off. On Android 12 and earlier, which do not let Backtalk turn animations off, the setting only shortens the wait. The setting is on by default.

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
*   **Navigation gestures.** You can assign a gesture to move to the next or previous character, word, line, paragraph, heading, link, control, landmark, button, checkbox, radio button, edit field, combo box, focusable item, graphic, list, list item, table, visited link, unvisited link, or heading of a given level. The reading control does not change. Find these actions under **Navigate by text**, **Navigate by element**, and **Navigate by heading level** when you choose an action for a gesture. Headings, links, and controls work in apps and on web pages. The other elements work only on web pages, and elsewhere Backtalk says so.
*   **Status gesture.** Triple-tap with 2 fingers to hear what the status bar shows: the time, battery, Wi-Fi, and mobile signal, and the ringer, Do Not Disturb, and airplane mode when they are not in their usual state. To hear the Wi-Fi network name, allow location access when Backtalk asks the first time. To choose what it says and in what order, go to **Status readout** in Backtalk settings. Each item has **Move up** and **Move down** actions. This action is also in the gesture list as **Speak status**. To read from the current item, use **Read from next item** in the Backtalk menu.
*   **Rotor.** Turn 2 fingers on the screen like a dial to choose a reading control. Turn clockwise for the next one and counterclockwise for the previous one. Each step of about a twelfth of a turn moves one reading control, so you can keep turning to move further. Then swipe up or down to change it. You can assign other actions to **Rotate clockwise with 2 fingers** and **Rotate counterclockwise with 2 fingers** in gesture settings. This needs Android 13 or later.
*   **Gestures handled by Backtalk.** Backtalk now recognizes gestures itself by default, instead of leaving this to Android. This is what makes the rotor possible. To let Android recognize gestures again, go to **Developer settings** in Backtalk settings and turn off **Handle gestures in Backtalk**, then turn Backtalk off and on again. The rotor does not work then.

### Backtalk menu

*   **Shorter menu by default.** These items are off by default: Actions, Screen search, Add or edit labels, Describe text formatting, Copy last spoken phrase, Spoken language, Voice commands, Keyboard shortcuts, and Braille display settings. Actions stay available with the actions reading control. Text-to-speech stays on, so that you can get to speech settings if your speech engine crashes. To turn them back on, go to **Customize menus** in Backtalk settings.
*   **Circle menu.** The Backtalk menu can show as a circle in the middle of the screen, like in TalkBack 8.1 and earlier. Each item is a slice of the screen around the middle. Slide to an item, and lift to select it. Lift in the middle of the circle to close the menu. Items that open more items show them in a new circle. To turn it on, go to **Customize menus** in Backtalk settings and turn on **Circle menu**.

### Pause

*   **Pause Backtalk.** Pausing turns off Backtalk's speech, sounds, vibration and gestures, and explore by touch, so the phone works as if no screen reader is on. Backtalk stays on, so it resumes at once. This was in TalkBack 8.1 and earlier as suspend. To pause, choose **Pause Backtalk** in the Backtalk menu, assign the **Pause Backtalk** action to a gesture, or assign the **Pause or resume Backtalk** keyboard shortcut. The first time, Backtalk asks to confirm and says how to resume. To resume, tap the **Backtalk is paused** notification, press volume down 3 times quickly, or press the keyboard shortcut again. By default, Backtalk also resumes when the lock screen shows. To change this, go to **Advanced settings > Resume Backtalk** in Backtalk settings and choose **When the screen turns on** or **Only from the notification or a shortcut**. Volume keys still change the volume while Backtalk is paused. If Backtalk restarts while paused, it starts unpaused.

### Speech

*   **Speak notifications setting.** To stop Backtalk from reading new notifications when they arrive, go to **Verbosity** in Backtalk settings and turn off **Speak notifications**. Incoming calls are still read, and you can still read notifications in the notification shade.
*   **Resume speech where you paused it.** With speech engines that do not report word positions, such as RHVoice and Gryphon, tapping with 2 fingers to resume speech started the text over. Backtalk now learns how fast your engine speaks from the items it finishes, works out how far it got when you paused, and resumes from the start of that sentence or phrase. It can repeat a few words, but it never skips any. Engines that report word positions still resume from the word.
*   **No "collapsed" on notifications.** Backtalk does not say "collapsed" for each notification on the lock screen and in the notification shade. It still says "expanded" when you open one, and it still says "collapsed" in other apps.

### Calls

*   **Speaker when away from your ear.** Like VoiceOver on iPhone, Backtalk can move a call to the speaker when you take the phone away from your ear, and back to the earpiece when you hold it up again. A call that starts with the phone away from your ear goes straight to the speaker. Backtalk leaves Bluetooth and wired headsets alone, and if you turn the speaker on or off in the Phone app, your choice stays until you move the phone again. Android only lets apps such as smartwatch companions change where call audio goes, so you grant the permission yourself, once, with adb or [Shizuku](https://shizuku.rikka.app): `adb shell appops set fyi.quin.backtalk MANAGE_ONGOING_CALLS allow`. Then turn on **Speaker when away from your ear** in **Advanced settings**. Until the permission is granted, the setting is unavailable and shows the command. This needs Android 12 or later.

### Sound and vibration

*   **Individual sounds and vibrations.** To turn off one sound or one vibration and keep the rest, go to **Sound and vibration** in Backtalk settings and open **Individual sounds and vibrations**. There is a switch for each sound and each vibration, including the braille display and braille keyboard sounds. To hear or feel one, open the actions menu on its switch and choose **Preview**. Changing a switch plays nothing. **Sound feedback** and **Vibration feedback** still turn all of them off at once.
*   **Control sounds.** Like the [Unspoken](https://github.com/ahicks92/Unspoken) add-on for NVDA, Backtalk can play a sound for each kind of control in place of the focus sound, and leave out saying "button", "checkbox" and the like. There are sounds for buttons, checkboxes, switches, radio buttons, edit fields, drop-down lists, sliders, images and links on web pages. Each sound comes from where the control is on the screen: from the left for controls on the left, and from higher up for controls near the top. A row that is not a control itself, such as a row in Settings, plays the sound of the control in it, such as its switch. Keys on the keyboard keep the usual focus sound, and so does moving into or out of a list, and then Backtalk still says the kind of control. To turn them on, go to **Sound and vibration** in Backtalk settings and turn on **Control sounds**. With headphones, the sounds play in 3D, using measurements of how a real head hears sound from each direction (the MIT Media Lab KEMAR measurements, by Bill Gardner and Keith Martin). On the phone speaker, they only move between left and right. To choose this yourself, use **3D audio**. To keep hearing the kind of control as well, turn on **Still say the kind of control**. Each sound also has its own switch and preview in **Individual sounds and vibrations**.

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

### On-device AI

If you do not want to use an API key, or you have hit the free tier limit, Backtalk can describe images and screens with a Gemma 4 model that runs on your phone. Nothing is sent to Google or anyone else when you use it.

1.  Open Backtalk settings, then **Automatic descriptions**, then **On-device AI**.
2.  Choose a model. **Gemma 4 E2B** is the one to start with: 2.6 GB, and it needs a phone with about 6 GB of memory. **Gemma 4 E4B** is 3.7 GB, gives better answers, and needs about 8 GB. The list also has other small vision models from the [LiteRT community](https://huggingface.co/litert-community), from 0.4 GB up, so that you can try them. Those are marked experimental: they are community conversions that the Backtalk developers have not tried, and some may not work or may follow the screen description format badly. The list only shows models that your phone has enough memory for, plus any you already have. If your phone does not have enough memory for Gemma 4 E2B, Backtalk picks the largest model that fits.
3.  Choose **Download model**. It downloads once from [Hugging Face](https://huggingface.co/litert-community) and carries on where it stopped if the connection drops. A notification shows the progress. Backtalk checks the file against a known SHA-256 hash and deletes it if it does not match. Or choose **Use a model file from storage** to use a `.litertlm` file that you downloaded yourself, such as `gemma-4-E2B-it.litertlm` from `litert-community/gemma-4-E2B-it-litert-lm`. Backtalk works out which model it is.
4.  Turn on **Use on-device AI**.

You still need to turn on Gemini support in the Gemini settings, which switches on Describe image and Describe screen. After that, they use the model on your phone. Turn **Use on-device AI** off to go back to the Gemini API.

Answers take several seconds and use battery, more than the cloud on a mid-range phone. The model loads on the first request and unloads after two idle minutes to free memory. The model runs in its own process, so if the phone runs out of memory, Android stops the model and not Backtalk, and Backtalk says so. Before it loads a model, Backtalk checks that the phone has at least as much free memory as the model's size, and if not, it says there is not enough free memory and does not load it. To test with a model that is too big for your phone, turn on **Ignore on-device AI memory limits** in **Developer settings**. It lists every model, lets you choose any downloaded one, and skips the free memory check. If answers fail or the phone slows down, try the other model, or turn **Use the GPU** on or off. On-device AI needs a 64-bit ARM phone and adds about 22 MB to the app.

## Install

Install the APK on your device with adb.

On Windows, `.\deploy.ps1` builds the APK and installs it with adb. This script needs PowerShell 7. To install the last build without building again, use `-SkipBuild`.

Backtalk installs as `fyi.quin.backtalk`, so it does not replace Google's TalkBack. The two apps have separate settings. Because its app ID is its own, Backtalk also installs on GrapheneOS and other ROMs that ship the AOSP TalkBack as a system app named `com.android.talkback`.

### Moving from the old app ID

Earlier builds of Backtalk installed as `com.android.talkback`. To move to the new app ID:

1.  Update as usual. The update installs the new Backtalk next to the old one, and the accessibility settings open.
2.  Turn on the new Backtalk when asked. Your settings and custom labels come along.
3.  The old Backtalk turns itself off, and a notification asks to remove it. Tap it to uninstall the old app.

On-device AI models are not carried over, so download them again in the new app. If you used the braille keyboard or an accessibility shortcut, turn them on again for the new Backtalk.

To make the switch fully automatic, grant the new app permission to change secure settings before you turn it on: `adb shell pm grant fyi.quin.backtalk android.permission.WRITE_SECURE_SETTINGS`. Then the new Backtalk turns the old one off, and moves the accessibility shortcut and the braille keyboard over, by itself.

## Updates

Each change to Backtalk is built on GitHub as a development build, on the [dev release](https://github.com/trypsynth/backtalk/releases/tag/dev) page. The [latest release](https://github.com/trypsynth/backtalk/releases/tag/latest) holds the last build with the old app ID, which moves old installs to the new one. Backtalk checks for a new build when it starts and about once a day. When there is one, it shows a notification with the list of changes. Tap the notification to download and install the new build. The first time, Android asks you to allow Backtalk to install apps.

To check now, go to **Check for updates** in Backtalk settings. To stop the daily checks, turn off **Automatically check for updates**.

Android only installs an update that is signed with the same key as the installed app. If you build Backtalk yourself, your build is signed with your own debug key, so it cannot be updated by the builds from GitHub. To use them, uninstall your build first.

## Run

After you install Backtalk, go to **Settings > Accessibility**. Backtalk is listed as **Backtalk** and is off by default. Turn off Google's TalkBack first, then turn on Backtalk.

## Debug tools

Debug builds include tools to find lag:

*   The `BacktalkStall` logcat tag logs each time the main thread is blocked for more than 100 ms, with the code that was running.
*   The `BacktalkGesture` logcat tag logs touch state changes and each gesture that Backtalk detects.
*   Event processing has trace sections, which show in [Perfetto](https://perfetto.dev) system traces.
