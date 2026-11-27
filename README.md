# Free Radio

A native Android FM radio app focused on real terrestrial FM reception, saved frequencies, a clean tuner interface, and playback controls.

**Built by IOX Technologies**

## Product principles

- Real over-the-air FM only. No internet streams, accounts, advertisements, subscriptions, or backend.
- Favourites and preferences stay on the device.
- Wired headset connections may provide an FM antenna on compatible phones. Bluetooth is an audio output only; it cannot act as the FM antenna.
- Background playback, lock-screen/media controls, wired/Bluetooth audio output, and Android hardware volume controls are first-class requirements.
- Unsupported FM hardware must be reported honestly. The app must never simulate an FM signal or silently fall back to internet radio.

## Technical direction

- Kotlin + Jetpack Compose for a native Android application.
- A hardware-adapter boundary for OEM-specific FM receiver integration.
- DataStore for on-device station persistence.
- Android audio-device observation and AudioManager volume integration.
- A dedicated playback-service boundary for background radio playback.

## Important hardware limitation

Android does not expose a universal, unrestricted FM tuner API to ordinary third-party applications. On many devices the FM receiver is disabled, unavailable, or controlled by vendor/system components. The app therefore starts with a clear unsupported-hardware state and an adapter contract. Actual tuning and scanning require a compatible device-specific integration and physical-device validation.

A wired headset is only an antenna on devices whose FM RF path is wired to the headset connector. Bluetooth headphones can play audio, but cannot provide that physical antenna connection. Some devices may permit speaker playback while a wired headset remains attached; this is OEM-specific and must be tested.

## Build

Open the project in a current Android Studio installation with Android SDK Platform 36 or later and JDK 17. Allow Gradle sync to resolve the plugins and dependencies.

The repository is at the initial foundation stage. The native tuner adapter is intentionally not presented as implemented until it has been validated against actual FM hardware.

## Project status

- [x] Native Android project structure
- [x] Jetpack Compose design system and tuner UI
- [x] Local favourites persistence
- [x] Audio-device observation and volume controls
- [x] Hardware engine abstraction and honest unsupported-device state
- [ ] Device-specific FM tuner integration
- [ ] Physical-device validation for speaker + wired-antenna routing
- [ ] End-to-end background playback and media-session controls
- [ ] Automated tests and release hardening
