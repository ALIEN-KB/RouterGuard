# Router Guard

Android app for the Huawei HG8546M: see who is on your Wi-Fi, block / unblock devices, and
remember every device with your own names.

## Build the APK

**Option A - Android Studio (easiest on Windows)**
1. Install Android Studio, then File > Open > select this folder.
2. Wait for Gradle sync (first time downloads a few hundred MB).
3. Build > Build APK(s). Or plug in your phone (USB debugging on) and press Run.
   The APK lands in `app/build/outputs/apk/debug/app-debug.apk`.

**Option B - no installs: GitHub Actions**
1. Create a private GitHub repo and push this folder to the `main` branch.
2. Open the Actions tab > "Build APK" > when it finishes, download `RouterGuard-debug-apk`.
3. Unzip it and install `app-debug.apk` on your phone (allow "install unknown apps").

## Using it
Join your home Wi-Fi, open the app, enter the router address (192.168.100.1), username and
password. Pull down to refresh, tap a device for details/rename, press Block / Unblock.
Close the router's web page in your browser first: the router allows one admin at a time.
The app logs out of the router after every action so it never locks you out.
