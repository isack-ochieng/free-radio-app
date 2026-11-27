# Radio

A native Android FM radio application with local station presets, weekly show reminders, audible alarm alerts, and hardware volume controls.

## App identity

- App name: Radio
- Android application ID: `tech.iox.freeradio` (unchanged)
- Native stack: Kotlin + Jetpack Compose
- Minimum Android version: 8.0 (API 26)
- Attribution: Isack Ochieng — https://isack-ochieng.pages.dev

## Features

- FM frequency dial with 0.1 MHz tuning.
- Station scanning through Android's connected Broadcast Radio tuner.
- Local favourite frequencies.
- Recurring weekly show reminders by show name, frequency, weekday, and time.
- Audible reminder alarms with Snooze 5 min and Dismiss actions.
- Reminder restoration after reboot, time changes, and time-zone changes.
- Hardware volume-key routing and in-app volume controls.
- Compact footer attribution using the repository's `footerimage.png`.

## Build variants and FM access

There are two product flavors:

- `play`: ordinary Play Store distribution; it does not request privileged Broadcast Radio access.
- `system`: includes the Android Broadcast Radio API adapter and declares `ACCESS_BROADCAST_RADIO`.

The system adapter discovers an actual FM module through Android's Broadcast Radio service, opens a tuner, tunes frequencies, and scans the FM band. It does not launch the phone manufacturer's radio application.

**Important:** Android's Broadcast Radio framework and tuner permission are system/privileged interfaces. The system APK must be installed as a privileged app in a system partition, and the permission must be allowlisted on that same partition. Installing the APK as a normal app or merely declaring the permission does not grant access. Device firmware must also expose a functioning FM Broadcast Radio HAL. Some Infinix/Transsion builds use a proprietary MediaTek FM service instead; those builds may require a model-specific adapter and vendor libraries.

The repository includes the privileged permission allowlist and Magisk overlay layout in `system-integration/`. CI packages `radio-system-magisk.zip` with the system release APK. Rooting, unlocking a bootloader, and system overlays carry device-specific risks. Back up the device and only use a module verified against the exact firmware.

## R8 and release artifacts

Both release flavors enable R8 code shrinking, obfuscation, and resource shrinking. CI preserves generated `mapping.txt` files so minified crash reports can be retraced.

Local build commands:

```bash
gradle testPlayDebugUnitTest testSystemDebugUnitTest
gradle assemblePlayRelease assembleSystemRelease
```

CI signs release test artifacts with the ephemeral Android debug key only so they can be installed for size and behavior testing. This key is **not** a Google Play upload key. Configure a private upload key for Play publication; never commit the keystore or passwords.

## Show reminders

- Each reminder stores its show name, frequency, weekday, time, and enabled state locally.
- Android exact-alarm access is requested through system settings where available. Without it, the app falls back to an inexact idle-allowed schedule.
- Android 13+ notification permission is needed for reminder alerts.
- Alarm alerts use the handset's default alarm ringtone and loop on supported Android versions until dismissed or snoozed.
- Weekly alarms are restored after reboot and system time/time-zone changes.

## Device validation

A successful build does not prove that FM works on every handset. Test module discovery, permission grant, wired antenna requirements, real reception, station scanning, background playback, and audio routing on each target model and firmware before declaring the device supported.
