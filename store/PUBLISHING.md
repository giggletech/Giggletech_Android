# Upload GiggleTech to Google Play

The signed bundle is `dist/GiggleTech-1.0.2.aab`. It targets API 36 and uses `com.giggletech.router`, version code 3. The APK in the same directory is for installing directly; upload the AAB to Play.

## Before the first upload

1. Confirm the permanent application ID and ownership of the GiggleTech name/source. The package ID must match any existing Play listing you intend to update. This build uses a newly generated upload key; an existing app may require its registered upload key instead.
2. Back up `C:\Users\jason\.giggletech\android-signing` securely. It contains the upload key, certificate and signing-password configuration. Keep all of it private except the public certificate. Do not regenerate the key for routine updates.
3. Enter your public support email and publisher name in the privacy-policy draft. Publish `privacy-policy.html` on your HTTPS website and use that URL in Play Console. These details were not invented for you.
4. Test with a physical Android phone, Quest/PC and GiggleTech hardware. Confirm discovery, avatar parameters, power limits, screen-off operation, Stop and network-loss behavior. The emulator checks do not prove Wi-Fi multicast or hardware compatibility.

## Play Console

1. Create the app in your developer account. Choose its language, app name and intended distribution settings.
2. Enroll in Play App Signing, using a Google-managed app signing key for a new app. This bundle is signed with your local **upload** key. Retain the key and password backup for future uploads. [Google's signing instructions](https://support.google.com/googleplay/android-developer/answer/9842756).
3. Begin with **Internal testing**, create a release and upload the AAB. Add release notes from `listing.txt` and invite hardware testers.
4. Fill the store listing from `listing.txt`. Add `icon-512.png`, `feature-graphic-1024x500.png`, and the real app screenshots in `screenshots/`. Add your public support email and hosted privacy-policy URL.
5. Complete app access, ads, content rating, target audience and Data safety accurately. This build has no login or ads, and no analytics or publisher backend. The privacy draft describes its local processing and LAN transmission. The owner must review the final Data safety answers against [Google's definitions](https://support.google.com/googleplay/android-developer/answer/10787469); do not claim encryption for OSC/HTTP LAN traffic.
6. Complete the foreground-service declaration for **connectedDevice**. Explain that users start a session to continuously send OSC motor commands to external Wi-Fi haptic hardware while using VRChat, including with the phone screen off. Stopping routing stops the session. Provide the required demonstration video showing Start, actual hardware interaction, the ongoing notification and Stop. The declaration corresponds to the implemented service, not a background data-sync job. [Foreground-service requirements](https://developer.android.com/develop/background-work/services/fgs/service-types#connected-device).
7. Review Play's pre-launch report and complete any account-specific testing requirements before requesting production access. New personal accounts created after 13 November 2023 currently need a closed test with at least 12 continuously opted-in testers for 14 days. [Official testing requirements](https://support.google.com/googleplay/android-developer/answer/14151465).

Target API 36 matches the current new-app submission requirement. [Official target SDK policy](https://support.google.com/googleplay/android-developer/answer/11926878).

## Review access / demonstration notes

The app opens without credentials. It requires external GiggleTech hardware for physical haptic output. Reviewers can inspect and edit settings without hardware. Explain the device requirement in App access and provide a real demonstration video. Avoid advertising the in-app simulated/emulator testing as proof of hardware functionality.

## Updates

Increase `versionCode`, update `versionName`, reuse `keystore.properties`, then run `scripts/Build-Release.ps1`. Update the output filenames in the release script when changing the version. Keep `dist/mapping.txt` with the corresponding release for crash deobfuscation. The AAB embeds the mapping for Play.

No Play Console upload, account creation, public hosting or publication has been performed by this project setup. Google reviews the app and declarations; building/signing does not guarantee approval.
