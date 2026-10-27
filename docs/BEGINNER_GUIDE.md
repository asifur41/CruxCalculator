# Beginner guide: from ZIP to installed app

No Android Studio and no local setup are needed. Everything happens on github.com in your browser.

## 1. Create the repository and open a Codespace

1. Go to https://github.com and sign in. Click **+** (top right) > **New repository**.
2. Name: `CruxCalculator`. Choose **Private**. Tick **Add a README file**. Click **Create repository**.
   *You should see:* the new repository page with a README.md file.
3. Click the green **Code** button > **Codespaces** tab > **Create codespace on main**.
   *You should see:* a browser version of VS Code after 30-90 seconds. A terminal is open at the bottom.
   *If it asks to "Set up" or build a dev container, wait. First start can take 2-4 minutes.*

## 2. Upload and unzip

1. Drag `CruxCalculator-mvp.zip` from your computer into the **Explorer** list on the left.
2. In the terminal (bottom) run, one line at a time:

```bash
pwd
unzip CruxCalculator-mvp.zip
shopt -s dotglob
mv -f CruxCalculator/* .
rmdir CruxCalculator
rm CruxCalculator-mvp.zip
chmod +x gradlew
ls -la
```

*You should see:* `pwd` prints `/workspaces/CruxCalculator`; `ls -la` lists `app`, `core-math`, `gradlew`, `settings.gradle.kts`, `.github` and more.

Verify the important files exist:

```bash
ls settings.gradle.kts build.gradle.kts gradlew gradle/wrapper/gradle-wrapper.properties .github/workflows/android-build.yml app/build.gradle.kts
```

*Success:* it prints all six names with no "No such file". If you see "No such file", you are one folder too deep or too shallow: run `ls` and `cd` into the folder that contains `settings.gradle.kts`.

## 3. Commit and push

Website method (Codespace terminal):

```bash
git status
git add .
git add --chmod=+x gradlew
git commit -m "Add CruxCalculator MVP"
git push origin main
```

*Success:* the last line says `main -> main`. Refresh github.com; the folders are visible.
*If git asks for your name/e-mail:* `git config --global user.name "Your Name"` and `git config --global user.email "you@example.com"`, then repeat the commit.

GitHub CLI method (if you started from a blank folder instead of a repository):

```bash
git init -b main
git add .
git add --chmod=+x gradlew
git commit -m "Add CruxCalculator MVP"
gh repo create CruxCalculator --private --source=. --remote=origin --push
```

## 4. Watch the build

1. On github.com open your repository > **Actions** tab.
2. Click the newest run named **Android Build** (yellow dot = running, green check = success, red x = failed).
3. First run: 8-15 minutes. Later: 4-8 minutes.
4. On success scroll to the bottom of the run page: **Artifacts**. Click each name to download a ZIP:
   * `app-debug-apk` -> contains `app-debug.apk`: **install on your phone for testing**.
   * `app-release-apk` -> `app-release-unsigned.apk` (no signing yet; cannot be installed) or `app-release.apk` (signed).
   * `app-release-aab` -> `app-release.aab`: **the file you upload to Google Play**.

Debug APK = installable test build (bigger, slower, debug key). Release APK = optimised build for sideloading, installable only when signed. Release AAB = the Google Play upload format; Google creates optimised APKs from it. It cannot be installed directly.

Install the debug APK: unzip the download on your phone, tap `app-debug.apk`, allow "install unknown apps" for your browser/file manager when asked.

## 5. Add signing (only when you are ready for Google Play)

See README section 5. Short version:

```bash
keytool -genkeypair -v -keystore upload-keystore.jks -alias upload -keyalg RSA -keysize 2048 -validity 10000
base64 -w 0 upload-keystore.jks > keystore.b64
cat keystore.b64
```

Add four repository secrets (Settings > Secrets and variables > Actions): `KEYSTORE_BASE64`, `KEYSTORE_PASSWORD`, `KEY_ALIAS`, `KEY_PASSWORD`. Download `upload-keystore.jks` from the Explorer (right-click > Download) and keep two backups; then delete it from the Codespace.

## 6. Troubleshooting table

| Problem | Likely cause | How to diagnose | Exact fix |
|---|---|---|---|
| ZIP does not unzip correctly (`End-of-central-directory signature not found`, or files in the wrong folder) | Incomplete upload, or files ended up one folder level too deep | `ls -la` and look for `settings.gradle.kts` | Re-upload the ZIP; run the `mv -f CruxCalculator/* .` step; or `cd CruxCalculator` and work from there |
| `./gradlew: Permission denied` | Wrapper lost its executable flag | `ls -l gradlew` (no `x` in the first column) | `chmod +x gradlew` then `git add --chmod=+x gradlew` and commit |
| `JAVA_HOME is not set`, `Unsupported class file major version`, `requires JDK 17` | Wrong Java version | `java -version` | In Codespace: rebuild container (F1 > "Codespaces: Rebuild Container"). In Actions the workflow already installs 17 |
| `SDK location not found. Define a valid SDK location with ANDROID_HOME` | Android SDK not installed in Codespace | `echo $ANDROID_HOME`; `ls local.properties` | `bash .devcontainer/setup-android.sh` (Actions installs the SDK by itself) |
| `Failed to install the following Android SDK packages ... licences` | SDK licences not accepted | Read the step "Install Android SDK platform and build tools" | Re-run the workflow; or add `yes \| sdkmanager --licenses` before the Gradle steps |
| `Could not resolve com.android.tools.build:gradle:8.9.1` / `Plugin ... was not found` | Network hiccup, or version typo | First red lines of the failed step | Click **Re-run failed jobs**. If it repeats, send me the log |
| `Android Gradle plugin requires ... compileSdk 36` / `compileSdk = 36 not supported` | AGP/SDK mismatch | Error names the versions | In `app/build.gradle.kts` and each module's `build.gradle.kts` change `compileSdk = 36` to `35` (and `targetSdk`), or bump `agp` in `gradle/libs.versions.toml` as the message suggests |
| `Unresolved reference: ...` / `e: file:///.../Something.kt:NN:NN` | Code or version mismatch in a Kotlin file | The path and line in the first `e:` line | Send me that line plus the file; I will return the corrected file |
| `Execution failed for task ':kspDebugKotlin'` | KSP / Kotlin version pair mismatch | Error mentions `ksp` and a Kotlin version | `ksp` in `libs.versions.toml` must start with the Kotlin version (`2.0.21-1.0.28`) |
| Unit tests fail (`There were failing tests`) | Code or test mismatch | Download artifact `test-reports`, open `index.html`; the red test shows expected vs actual | Send me the test name and message |
| Artifact missing after a green run | Upload step path wrong, or the run failed earlier | Scroll the run page: no "Artifacts" section | Open the failed step; make sure "Collect build outputs" is green; re-run |
| AAB builds but APK does not | Release APK task or signing issue | Log of step "Build release APK" | Without secrets the APK is named `app-release-unsigned.apk` (not a failure). With secrets, check them (next row) |
| Signing fails (`Keystore was tampered with, or password was incorrect`, `Failed to read key`) | Wrong secret value | Step "Build release AAB" log | Re-create the secrets: `KEYSTORE_BASE64` must be one unbroken line (`base64 -w 0`), `KEY_ALIAS` must match `keytool -list -keystore upload-keystore.jks`. To build unsigned instead, delete the four secrets |
| `OutOfMemoryError: Java heap space` / `Daemon disappeared` | Not enough RAM in Codespace | Error text | In `gradle.properties` change `-Xmx3g` to `-Xmx2g`, or use a bigger Codespace machine |
| Google Play rejects the AAB: `Version code N has already been used` | Version code not higher | Play Console error | Re-run the workflow (it increases automatically) and upload the newer AAB |
| Google Play: `Your Android App Bundle is signed with the wrong key` | Different keystore than the first upload | Play Console error | Use the SAME keystore as the first upload (your backup) |
| Google Play: `Your app targets API level N, which must be at least M` | Play raised the target level | Play Console error | Raise `targetSdk` and `compileSdk` in `app/build.gradle.kts`; ask me for the small upgrade |
| `git push` rejected: `non-fast-forward` | The GitHub README commit is newer | Error text | `git pull --rebase origin main` then `git push origin main` |

## 7. What to send back if something fails

Copy and fill in:

```text
Claude, the build failed.

Step where it failed:
[Paste step name, e.g. "Run unit tests" or "Build release AAB"]

Error message:
[Paste the FIRST red error line(s), full text]

Relevant GitHub Actions log:
[Paste about 30 lines around the first error]

Files involved:
[Paste the file path from the error, e.g. feature-history/src/main/kotlin/.../HistoryHub.kt]

What I did before the error:
[Paste steps. Also: ZIP method or create-project.sh method; did it fail in Codespace setup, Git push, Actions, APK build, AAB build or signing; link to the workflow run]

Please identify the exact cause and give me the exact corrected file or command.
```

## 8. How to ask for the next feature

Ask for ONE small thing per message, for example:

```text
Claude, add a "Copy tape as image" button to the tape screen of the CruxCalculator project.
Only change or create the files that are necessary, list every file you created or changed
(with full relative paths and full contents), tell me the exact git commands to commit and push,
and tell me how to verify the feature in the APK built by GitHub Actions.
```
