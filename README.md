# Radio

A native Android FM tuner companion focused on saved frequencies, scheduled show reminders, and a compact listening interface.

## App identity

- App name: Radio
- Android application ID: `tech.iox.freeradio`
- Native stack: Kotlin + Jetpack Compose
- Minimum Android version: 8.0 (API 26)
- Attribution: Isack Ochieng — https://isack-ochieng.pages.dev

## Features

- Manual FM frequency selection and local favourites.
- Recurring weekly show reminders by title, frequency, weekday, and time.
- Audible alarm-tone alerts with Snooze 5 min and Dismiss actions.
- Reminder persistence and rescheduling after device reboot, time changes, and time-zone changes.
- Wired-headset and Bluetooth audio route detection.
- Hardware volume-key routing and in-app volume controls.
- Best-effort handoff to installed OEM FM radio apps where the handset keeps tuner access privileged.

## FM hardware compatibility

Android does not provide a universal public FM tuner API to ordinary third-party phone apps. On some handsets, the stock radio is a privileged preinstalled system app with access to the broadcast radio HAL and audio routing permissions. This app cannot acquire those signature/privileged permissions merely by declaring them.

Where the stock FM application is installed, Radio can open it. The Transsion radio package is checked for Infinix, TECNO, and itel devices; Samsung and other known OEM radio packages are checked as well. This handoff is not equivalent to controlling the phone's receiver from inside Radio. True in-app FM tuning requires a public vendor API, an OEM partnership/system signature, or a separately validated hardware integration for the exact model.

A 3.5 mm wired headset is the FM antenna on some devices (including documented Infinix models). Bluetooth headphones are audio output only and cannot act as the FM antenna.

## Show reminders

- Each reminder stores a show name, frequency, weekday, time, and enabled state locally.
- Android exact-alarm access is requested through system settings where available. If not granted, the app schedules an inexact idle-allowed fallback and tells the user.
- Android 13+ notification permission is required for the reminder notification to appear. Exact alarms can start the ringing foreground service; if Android restricts a background service for an approximate alarm, Radio falls back to a high-priority notification with alarm sound and vibration.
- The alarm tone uses the handset's default alarm ringtone and loops on supported Android versions until dismissed or snoozed.
- Weekly alarms are restored after reboot and after system time/time-zone changes.

## Build

Open the project in Android Studio with JDK 17 and the Android SDK required by the configured compile SDK. Run the `testDebugUnitTest` and `assembleDebug` Gradle tasks.

The device-specific FM tuner itself remains dependent on OEM hardware access. Do not present the in-app tuner as working on a device until physical tuning, scanning, and audio routing have been validated on that exact model.
