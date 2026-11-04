# Speed and size: what is normal and what to install

## 1. Test the release APK, not the debug APK
Every CI run makes two APKs:

| File | Package name | What it is |
|---|---|---|
| `app-debug.apk` | `com.cruxcalculator.app.debug` | For developers. Not optimized, not shrunk, full of debugging code. |
| `app-release.apk` | `com.cruxcalculator.app` | The real app: code shrunk and optimized by R8, unused icons removed. |

Jetpack Compose in a debug build is several times slower than in a release build, and the debug APK carries the whole
Material icon library (tens of MB of code). Start-up time, tab switching and installed size should always be judged on
`app-release.apk`.

Both can be installed side by side (different package names). They do not share history or settings.
The release APK is signed with a public test key (`app/test-signing.keystore`), so each new build installs over the
previous one without losing data. Google Play builds use your own secret key instead.

## 2. Why the installed size grows from 19 MB to 80 MB
The app never writes caches or files of its own (only the small history database and settings). The growth is Android
compiling the app's code on the phone in the background (the `oat` / `odex` files), which counts towards "App size"
in Settings. A debug build compiles far more code than a release build, so it grows most. With the release APK the
growth is much smaller. Clearing storage or reinstalling shrinks it again; it is not a leak.

## 3. How to measure
* Cold start: force-stop the app, open it, and time it from the tap to a usable keypad (screen recording, count frames).
* Do it three times and ignore the first run after an install: Android is still compiling in the background then.
* Smoothness: Settings > Developer options > "Profile HWUI rendering" (On screen as bars). Bars above the green line
  mean slow frames.
