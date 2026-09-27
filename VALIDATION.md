# Validation — 27 September 2026

## Independent device tests — 28 September 2026, version 1.0.2

- Test now starts a short connected-device foreground service without a VRChat address, OSC receiver, discovery server or routing-settings validation.
- Emulator integration passed a standalone pulse with blank VRChat/parameter settings and invalid unrelated routing settings, a 1% power cap, three stop packets, repeat testing and cancellation. Hardware discovery and all prior routing integration checks also passed.
- Eleven JVM tests and release lint pass; signed APK/AAB rebuilt with version code 3 and the existing upload key.
- Physical motor response still requires testing on the user's hardware. Successful UDP transmission is not a device acknowledgement.

## Hardware discovery update — 28 September 2026, version 1.0.1

- Added the Your devices > Find GiggleTech devices picker, automatic IP filling, duplicate handling and manual-entry fallback.
- Eleven JVM tests pass, including compressed mDNS A records, unrelated/expired records, malformed packets and compression loops.
- Emulator integration passed discovery of a simulated `_http._tcp` GiggleTech hardware advertisement through Android NSD, followed by all existing routing checks.
- Release lint and signed APK/AAB builds pass. The same upload key is reused, with version code 2.
- Discovery on physical GiggleTech firmware and the `giggletech.local` fallback over real Wi-Fi still require hardware validation.

## Initial release

Passed:

- Debug APK compilation and release APK/AAB compilation with AGP 8.13.0, Gradle 8.13, JDK 21, compile/target SDK 36.
- Eight JVM tests covering OSC wire compatibility and malformed input, motor limits/velocity/timeout and settings validation.
- Debug and release Android lint with zero errors. Remaining warnings are nonblocking tool-version/resource-configuration suggestions.
- Android 15 (API 35) x86_64 emulator: real loopback UDP input to hardware `/motor` output; silence timeout; half-second test termination; sender-IP filtering; graceful stop and restart; OSCQuery automatic UDP port and HTTP HOST_INFO response.
- Visual inspection of the app at 1080 × 1920, 420 dpi.
- Signed, R8-minified release APK installed and launched on that emulator; connection and device settings edited/saved through the actual UI. Store screenshots are captures of the release app with example IP addresses; they do not show a physical hardware connection.
- APK signature verified with Android `apksigner`; AAB JAR signature verified with JDK `jarsigner`. Android upload certificates are self-signed, so normal CA-chain/timestamp warnings are expected.

Not verified with physical equipment:

- VRChat on a real Quest finding the phone through multicast DNS.
- GigglePuck/GiggleSpark motor response and reachability on a real Wi-Fi network.
- OEM battery management and long sessions with screen off.
- Wi-Fi disconnect/reconnect, abrupt Android process termination and firmware watchdog behavior.
- Runtime compatibility on Android 8 and Android 16 devices (API compatibility was linted; emulator runtime was Android 15).

The release should enter internal hardware testing before public rollout. See README for the hardware's last-value retention limitation and store/PUBLISHING.md for Play Console steps.
