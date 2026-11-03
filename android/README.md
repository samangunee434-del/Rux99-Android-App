# Rux99 for Android

Rux99 retains the original red/orange Free Fire dashboard and runs fixed Android shell commands through Shizuku. It is not an FPS guarantee, anti-cheat bypass or game modification tool. No root is required when Shizuku is started with ADB.

## Produce an APK

An APK was not compiled or device-tested in the Netlify editing environment. This directory is an Android Studio project, not an APK. Requires JDK 17, Android SDK 35 and Gradle 8.9. Open this directory in Android Studio, let Gradle sync, and select Build > Build App Bundle(s) / APK(s) > Build APK(s). The debug APK is at `app/build/outputs/apk/debug/app-debug.apk`. Debug APKs are installable for testing; use Android Studio's Generate Signed Bundle / APK for a release. Keep your release signing key private and use the same key for future updates.

Alternatively, upload the source archive contents to a GitHub repository, including its `.github` folder. In Actions, run **Rux99 Android APK** manually. Download the **Rux99-test-apk** artifact, extract it, and install `app-debug.apk`. The workflow has not been run or validated here. GitHub's artifact ZIP itself is not installable. CI debug signing keys can change between runs; uninstall an earlier test build if Android reports an incompatible signature. Review source before granting Shizuku permission.

## Use

1. Install Free Fire (`com.dts.freefireth`). This version does not target Free Fire MAX.
2. Install Shizuku from https://shizuku.rikka.app and follow its setup instructions. Android 11+ supports wireless debugging; earlier supported Android versions need a computer with ADB.
3. Start Shizuku, open Rux99, select **Setup & APK**, and tap **Connect Shizuku**. Grant permission.
4. Wait for **Connected**. Run one command or the full boost sequence and inspect the returned output. Commands unsupported by your phone are reported. A zero exit code does not establish that a vendor honors a setting.
5. Reconnect after rebooting or restarting Shizuku.

## Changes and restore

Boost sets all three system animation scales to 0, requests the global GPU rendering flag, fixed performance mode and Free Fire performance mode, exempts Free Fire from battery idle restrictions, requests background app cleanup and speed-profile compilation, then launches the game. Cleanup may close other background apps. Performance mode can increase heat and battery consumption. GPU rendering flags and game commands may have no effect or be unsupported on newer/vendor Android builds.

**Restore standard settings** sets the three animation scales to 1, the GPU flag to 0, disables fixed performance mode, selects standard game mode, and removes Free Fire's idle exemption. It does not restore custom previous settings, undo bytecode compilation, or reopen closed apps. Original custom settings are not saved. RAM is measured with Android's ActivityManager; animation/game-mode cards remain unknown unless explicitly confirmed by an action. No FPS values are fabricated.

The UI assets are bundled for offline operation. External HTTPS help links open in the browser. The web preview has no native bridge and cannot run shell commands. The native bridge only loads bundled content, accepts named operations, and has no free-form shell endpoint. No analytics or server storage is used.

## Device validation needed

Check fresh install, wireless-debugging setup, permission granted/denied, stopped Shizuku, restart/reconnect, missing Free Fire, unsupported commands, full boost, restore, offline layout, Android back behavior and small-screen layout on a physical Android device. No Android SDK compilation or physical-device checks were performed here.
