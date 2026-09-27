# GiggleTech for Android

A native Android port of the core Leatherjacket OSC router. Use your Android phone as the bridge between VRChat on Quest/PC and GiggleTech Wi-Fi haptic hardware.

## Install or upload

- `dist/GiggleTech-1.0.2.apk`: signed, installable Android application.
- `dist/GiggleTech-1.0.2.aab`: signed Android App Bundle for Google Play.
- `store/PUBLISHING.md`: publishing instructions and outstanding owner-supplied details.
- `store/`: listing text, icon, feature graphic, screenshots and privacy-policy draft.

Requires Android 8.0 or later; targets Android 16 / API 36. Application ID: `com.giggletech.router`. Version: `1.0.2` (code `3`). Confirm that this is the intended permanent package ID before the first Play upload. No runtime third-party SDKs, ads, accounts, native libraries or cloud backend.

## Connect

1. Join the same Wi-Fi as the Quest/PC and hardware. Guest Wi-Fi must allow devices to communicate.
2. Enable OSC in VRChat. In Connection settings, find VRChat or enter the Quest/PC IPv4 address. Only that source address is accepted for control.
3. Leave OSCQuery enabled for discovery. For manual routing, disable it, choose a receive port, and configure the OSC sender to the phone address displayed while routing.
4. Under **Your devices**, tap **Find GiggleTech devices**. Select discovered hardware to prefill its IP, then choose its avatar proximity parameter and Save. Already-added devices open their existing settings. **Add a device manually** remains available. Output uses `/motor`, integer values, UDP port 8888.
5. Tap **Test · ½ second** on a saved device to check it immediately. No computer, VRChat IP or running router is required. The pulse is capped at 10% power or the device's lower configured limit and ends with repeated stop commands. You can also cancel with **Stop test**. Start routing only when ready to receive VRChat OSC.
6. Leave the foreground service running while playing. The notification provides a Stop action. Allow notifications if you want that action visible outside the app.

Device Wi-Fi setup follows the original hardware workflow: join `Giggletech_haptics` with password `giggletech`, open `http://192.168.4.1`, and configure your home Wi-Fi. Obtain the resulting hardware IP from your router.

## Implemented

- Multiple devices, local persistent configuration, device names, per-device power and output scale.
- Proximity band mapping; velocity control with jitter deadzone, retreat spike rejection, pull-away, soft cap and EMA smoothing.
- Global/per-device OSC power parameter as a 0–1 multiplier of the configured local limit. Starting pulses never exceed that limit.
- Original 0.66 output scaling, zero commands on startup, avatar change, timeout and graceful shutdown, plus repeated idle stop packets.
- OSC floats, integers, bools, avatar-change strings and nested bundles; malformed/nonfinite packets are rejected atomically.
- Native mDNS/OSCQuery advertisement, read-only HTTP address tree and HOST_INFO, and VRChat discovery picker.
- Hardware discovery: an eight-second Wi-Fi scan of `_http._tcp` services plus a direct `giggletech.local` mDNS hostname lookup, matching the desktop discovery approach. Results are deduplicated by IPv4 address. Closing the picker stops scanning and releases the multicast lock. Discovery never sends motor commands. Firmware must advertise its service or answer the hostname query; isolated guest Wi-Fi can prevent discovery.
- Optional reachability Bool feedback to the configured VRChat address on UDP 9000. ICMP reachability is best-effort; a failed ping is shown as “No ping reply”, not proof of offline hardware.
- Connected-device foreground service, CPU wake lock during routing, bounded in-memory log and live levels.

The app is a mobile port of the router, not a wrapper around the Windows executable. Desktop YAML import/export, web/tray UI, collider visualization, Twitch/Supabase bot, desktop-only utilities are not included. Enter existing device settings in the Android interface. Velocity formulas follow the source; remote power always reduces the configured cap rather than raising it. The source tree was not modified.

## Hardware limitation

The source hardware protocol can retain its last motor value if connectivity or app execution stops abruptly. The app sends stop commands on normal shutdown and input timeout, but cannot deliver them through a disconnected network or after Android force-stops/kills it. A firmware watchdog is required for a guaranteed stop on lost communication. Confirm device behavior with a low power cap before use. OEM battery management and Wi-Fi multicast behavior require physical-device testing.

## Build

Open this folder in Android Studio, or set `JAVA_HOME` to JDK 17/21 and configure the Android SDK in `local.properties`:

```properties
sdk.dir=C\:/Users/jason/AppData/Local/Android/Sdk
```

```powershell
.\gradlew.bat assembleDebug testDebugUnitTest lintDebug
.\scripts\Build-Release.ps1
```

Gradle 8.13 and Android Gradle Plugin 8.13.0 are pinned. Wrapper downloads the required Gradle version. Signing is loaded from ignored `keystore.properties`. This machine's upload key and a signing-properties backup are stored in `C:\Users\jason\.giggletech\android-signing`. Back that folder up securely; never commit it or include it in store assets. Reuse the key for updates. `scripts/New-UploadKey.ps1` is only for a new signing setup and refuses to replace existing keys.

`Build-Release.ps1` runs JVM tests and release lint, builds signed AAB/APK, and places them with the R8 mapping and hashes in `dist`. Increment `versionCode` in `app/build.gradle` for each Play update.

## Verification

Eight JVM tests cover known OSC wire bytes, float/int/bool round trips, nested/malformed bundles, random malformed datagrams, band and power caps, retreat behavior, timeout reset and validation. The emulator instrumentation checks real UDP routing, timeout shutdown, timed tests, wrong-source rejection, stop/restart and real OSCQuery HTTP responses. Run it on an emulator with no physical hardware involved:

```powershell
.\gradlew.bat assembleDebug assembleDebugAndroidTest
adb install -r app\build\outputs\apk\debug\app-debug.apk
adb install -r app\build\outputs\apk\androidTest\debug\app-debug-androidTest.apk
adb shell am instrument -w com.giggletech.router.test/com.giggletech.router.IntegrationTest
```

See `VALIDATION.md` for the performed checks and remaining real-device checks.
