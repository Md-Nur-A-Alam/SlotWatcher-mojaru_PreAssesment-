# Slot Watcher

An Android app for Mojaru pre-assessment examiners. It signs in to the Mojaru Teacher Panel at the times you choose and checks the Pre Assessment List for booked slots, so you don't have to keep checking by hand. When slots are booked, it rings and reads out each slot's time and participant ("3 slots booked. 6:40 PM, Rukiya Mahmud. 6:50 PM, …") until you dismiss it.

<p>
  <img src="assets/logo.png" alt="Slot Watcher logo" width="96">
</p>

## How it works

1. Sign in once with your Mojaru Teacher Panel email or mobile and password.
2. Start a session: pick the date, the first and last check time, and the gap. For example, 6:25 PM to 9:15 PM every 10 minutes gives 18 checks: 6:25, 6:35 … 9:05, 9:15.
3. At each check time the app signs in and reads the slots for the chosen date from the panel's own data feed, the same one the Pre Assessment List uses to draw its table. It reads every page (15 rows per page), with each slot's time, status and participant name. If the feed fails, it falls back to opening the list in a hidden in-app browser. The booked count is the higher of the Booked Slots counter and the booked rows found.
4. It announces the result:
   - **Slots booked:** alarm tone and spoken slot times, repeating until you tap Dismiss. It shows full-screen, even over the lock screen.
   - **Nothing booked:** one short tone and "No slot booked. Next check at 6:45 PM." This can be turned off.
   - **Slot list couldn't be read:** "Could not read the slot list. Please check the panel." An empty or unloaded table is never reported as "no slot booked".
   - **Login rejected:** a separate "Login failed" alarm, and watching pauses.
   - **Network trouble:** up to 3 retries, 30 seconds apart, then a notification.

Alerts use the alarm volume, so they sound even when the phone is on silent.

## Features

- Exact check times, even with the screen off (alarm-clock scheduling plus a short foreground service). Sessions resume after a restart.
- Live countdown to the next check, with progress through the session.
- History of every check, grouped by day, with today's totals.
- Voice in English or Bangla (Bangla needs the Bangla voice in Google Text-to-speech).
- Settings for announcements, vibration and voice, plus a test alarm.
- "Share last checked page" in Settings, for diagnosing a wrong result.
- Reliability checklist for the Android permissions the app needs, each with an Allow button.
- Mojaru brand theme (purple `#532D80`, yellow `#FFCE39`, Inter font) with light and dark modes.
- Developer profile tab.
- The password is encrypted with the Android Keystore and only sent to `teacher.mojaru.com`. There are no analytics or third-party servers.

## Install

The app isn't on the Play Store. Build the APK (below), copy `SlotWatcher/app/build/outputs/apk/debug/app-debug.apk` to your phone and install it. You may need to allow installs from your file manager.

On first launch, allow everything listed under **Settings → Reliability**: notifications, alarms & reminders, unrestricted battery and full-screen alarm. Without them, checks can run late or the alarm may not show.

Requires Android 8.0 or later.

## Build

Requirements: JDK 17 or newer, and the Android SDK with platform 34.

```bash
cd SlotWatcher
./gradlew testDebugUnitTest assembleDebug     # Windows: gradlew.bat ...
```

Create `SlotWatcher/local.properties` with your SDK path if Android Studio hasn't already:

```properties
sdk.dir=C\:\\Users\\<you>\\AppData\\Local\\Android\\Sdk
```

To look at every screen with sample data and no login (debug builds only):

```bash
adb shell am start -n app.slotwatcher/.ui.MainActivity --ez demo true
```

## Project layout

```
document/requirements.html     Requirements specification
assets/                        Logo, developer photo, screenshots of the manual workflow
SlotWatcher/                   Android app (Kotlin, Jetpack Compose, Material 3)
  app/src/main/java/app/slotwatcher/
    net/      MojaruClient (login + slot data feed), PageRenderer (fallback: hidden WebView that runs the
              page's JavaScript), SlotParser (reads booked slots)
    check/    CheckTimes, CheckScheduler (exact alarms), CheckService, receivers, Diagnostics
    alarm/    AlarmRingService (tone + voice), AlarmActivity (full-screen alarm)
    data/     Encrypted login, session + history, settings, text formatting
    ui/       Theme and screens: Home, New session, History, Settings, Developer, Account
  app/src/test/   Unit tests for check times, the slot parser (including the empty-table case)
                  and spoken messages
  tools/probe.ps1  Saves the panel's raw pages from a computer
```

## When a result looks wrong

1. In the app, open **History**. Each check shows what it read, for example "18 slots read".
2. Go to **Settings → About → Share last checked page** and send the file. It's the exact page the last check read, with a header noting the counters and rows found. It contains participant names, so share it privately.

You can also save the panel's pages from a computer. This saves the raw HTML only, before the page's JavaScript runs. Run this from the repo root:

```powershell
powershell -ExecutionPolicy Bypass -File SlotWatcher\tools\probe.ps1
```

It asks for your login (nothing is saved) and writes the pages to `SlotWatcher/tools/probe-output/`. That folder contains participant names and phone numbers. It is git-ignored; delete it when you're done.

## Note

Slot Watcher signs in to your own Mojaru account automatically. Make sure Mojaru allows this kind of automated checking before you rely on it.

## Developer

**Md. Nur A Alam**, Software Engineer.
[Portfolio](https://nur-dynamic-profile-client-beta.vercel.app) ·
[GitHub](https://github.com/Md-Nur-A-Alam) ·
[LinkedIn](https://www.linkedin.com/in/md-nur-a-alam13/)
