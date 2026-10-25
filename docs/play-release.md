# Releasing on Google Play

The store listing (text, icon, feature graphic, screenshots) is in `fastlane/metadata/android/en-US/`, in the layout fastlane `supply` and Triple-T's Play Publisher read, so it can be uploaded by hand or by either tool.

## Before every upload

1. Bump `versionCode` and `versionName` in `app/build.gradle.kts`, and add `fastlane/metadata/android/en-US/changelogs/<versionCode>.txt` (500 characters at most).
2. Build from a clean working tree; uncommitted changes go into the build.
3. `./gradlew bundleRelease assembleRelease` with `keystore.properties` present (the upload key).
4. Run `scripts/smoke-test.sh` on the release APK on an emulator, fresh and upgrading over the previous release (`scripts/smoke-test.sh new.apk previous.apk`), on the newest Android and on an API 26 AVD.
5. Upload `app/build/outputs/bundle/release/app-release.aab`. The R8 mapping file is inside the bundle, so Play's crash reports stay readable.

## Play Console answers

These follow from what the app does; recheck them if that changes.

**Privacy policy URL**: required. Publish `PRIVACY.md` (for example on GitHub) and link it.

**App access**: all functionality is available without special access (no accounts or logins).

**Ads**: the app contains no ads.

**Content rating** (IARC questionnaire): category "Utility, Productivity, Communication, or Other". No violence, sexual content, profanity, drugs, gambling or user-to-user interaction. The app doesn't share the user's location and has no in-app purchases or digital purchases. It plays the user's own files, so answer "no" to user-generated content being shared.

**Target audience**: 13 and over is the simplest choice. Choosing younger ages brings in the Families policy requirements.

**Data safety**:
- Does the app collect or share any of the required user data types? **No.** Everything (library index, settings, play position, favourites) stays on the device, and the app has no internet permission.
- The folder backup (`.openplayer-backup.json`) and exports are written to storage the user picked, on their own device, so they're not "collection" in Play's sense (data sent off the device).
- Encryption in transit: not applicable (nothing is transmitted).
- Data deletion: users delete everything by uninstalling or clearing storage. Backup files in music folders can be deleted like any other file.

**Foreground service permissions**: the app declares `FOREGROUND_SERVICE_MEDIA_PLAYBACK`. Declare the use case "Media playback". Play asks for a short description ("Keeps music playing with the screen off or the app in the background, with controls in the notification") and may ask for a video showing playback continuing after leaving the app.

**News app / health / financial / government declarations**: not applicable.

## Known listing considerations

- Phone screenshots must be no more than twice as long as they are wide. The ones here are 1440 × 2560, taken on an emulator set to that resolution with only placeholder songs and playlist names. Retake them the same way after a visible UI change.
- From Android 16 (targetSdk 36), large screens (smallest width 600 dp and up) ignore the portrait lock in the manifest, so tablets and unfolded foldables can show the app in landscape. Worth a look on a tablet AVD before promoting to a wider audience.
- The listing says the app has no internet access. Keep it true: `ACCESS_NETWORK_STATE`, which Media3 adds, is removed in the manifest, and nothing requests `INTERNET`.
