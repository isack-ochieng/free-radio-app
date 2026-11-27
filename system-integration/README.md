# Radio system integration

The system flavor communicates directly with Android's Broadcast Radio service. It does not launch the manufacturer's radio application.

## Requirements

- An unlocked bootloader and a supported root/system-overlay method. CI packages the system release APK as a Magisk module.
- Firmware exposing an Android Broadcast Radio FM module.
- A functioning FM HAL and audio route in the firmware.
- Android granting `android.permission.ACCESS_BROADCAST_RADIO` to package `tech.iox.freeradio`.

The Magisk module places the APK at:

`/system_ext/priv-app/Radio/Radio.apk`

and the permission allowlist at:

`/system_ext/etc/permissions/privapp-permissions-tech-iox-radio.xml`

The allowlist must be on the same partition as the privileged app. Installing the APK normally in `/data/app` or merely declaring the permission in a manifest will not grant it.

## Install

1. Back up the device and keep a recovery path. Rooting and system overlays can prevent boot when mismatched with firmware.
2. Install the CI artifact `radio-system-magisk.zip` from Magisk.
3. Reboot.
4. Verify that the app is installed from the system partition and that `ACCESS_BROADCAST_RADIO` is granted.
5. Connect the required wired FM antenna if the handset uses one, then test tuning, scanning, audio routing, and background playback.

Useful ADB checks:

```sh
adb shell pm path tech.iox.freeradio
adb shell dumpsys package tech.iox.freeradio
adb shell cmd package check-permission android.permission.ACCESS_BROADCAST_RADIO tech.iox.freeradio
```

If the permission is granted but no FM module is exposed, the next step is a handset-specific vendor adapter and the exact firmware's libraries—not another generic permission declaration.
