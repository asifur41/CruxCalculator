# CruxCalculator

**The private, ad-free calculator for people who actually calculate.**

Pay once. No ads forever. No subscription. No tracking. No account. Works fully offline.

* Kotlin + Jetpack Compose + Material 3, Hilt, Room, DataStore
* Application id: `com.cruxcalculator.app` (internal code package stays `com.tapecalc`) | minSdk 26 | targetSdk 36 | Java 17
* The app has **no INTERNET permission**. Its only permission is VIBRATE (key haptics).

## What is in the MVP

| Area | Included |
|---|---|
| Basic | + - x / %, parentheses, sign, decimals, backspace, clear entry, live result preview, copy result / expression, smart paste, haptics (4 strengths), optional key sound |
| Editable tape | Chronological tape; tap any line to edit; `ans` token; later results are **updated or marked stale**; labels, notes, #tags; search, pin, duplicate, delete; export to text / Markdown / CSV / share text; 4 workspaces (Personal, Work, Study, Engineering) plus your own |
| Scientific | sin cos tan, inverse, hyperbolic, Deg/Rad/Grad (always visible), log / ln / log2 / log base N, powers, roots, factorial, mod, abs, pi e phi, nPr, nCr, exact fractions and repeating decimals |
| Convert | 12 categories, ~100 units, offline. Inline: `12 km to mi`, `5 kg to lb`, `72 F to C` |
| Variables | `rate = 0.18`, then `200 * rate`; variables panel |
| Look and feel | 12 themes, custom accent, key styles, button shapes, font size, left-handed layout, tablet / foldable two-pane layout, TalkBack labels, hardware keyboard support |

Not in this version (by design): programmer mode, complex numbers, matrices, custom formulas, currency conversion, graphing, symbolic algebra.

## Project layout

```text
CruxCalculator/
├── .github/workflows/android-build.yml   CI: tests + APK + AAB
├── .devcontainer/                        GitHub Codespaces (Java 17 + Android SDK)
├── app/                                  Application: Hilt, ViewModel, main screen, settings
├── core-precision/                       Rational, BigDecimal math functions, number formatting (pure Kotlin)
├── core-math/                            Tokenizer, Pratt parser, AST evaluator, units, smart paste (pure Kotlin)
├── core-data/                            Room database, repositories, tape editing logic, export, DataStore settings
├── core-ui/                              Themes, keypad key component, shared widgets
├── feature-basic/                        Display + basic keypad
├── feature-scientific/                   Scientific key grid
├── feature-converter/                    Unit converter screen
├── feature-history/                      Tape, variables, notes, entry editor
├── docs/                                 Play listing text, privacy policy template, icon guide, beginner guide
├── gradle/                               Version catalog + wrapper
└── gradlew, gradlew.bat, settings.gradle.kts, build.gradle.kts, gradle.properties
```

## 1. Get the project into GitHub

You received either `CruxCalculator-mvp.zip` or `create-project.sh`.

**ZIP method (in a GitHub Codespace)**

1. On github.com click **+ > New repository**, name it `CruxCalculator`, choose **Private**, tick **Add a README file**, click **Create repository**.
2. Click the green **Code** button > **Codespaces** tab > **Create codespace on main**. Wait for the editor to open.
3. Drag `CruxCalculator-mvp.zip` from your computer into the file list on the left (Explorer).
4. In the terminal at the bottom run:

```bash
unzip CruxCalculator-mvp.zip
shopt -s dotglob
mv -f CruxCalculator/* .
rmdir CruxCalculator
rm CruxCalculator-mvp.zip
chmod +x gradlew
ls -la
```

You should see `settings.gradle.kts`, `build.gradle.kts`, `gradlew`, `app`, `.github`, ...

**Script method**

Create a new empty file `create-project.sh` in the Codespace, paste the script in, save, and run `bash create-project.sh`.

## 2. Commit and push

```bash
git status
git add .
git add --chmod=+x gradlew
git commit -m "Add CruxCalculator MVP"
git push origin main
```

Open the repository on github.com and refresh; you should see all the folders.

## 3. Build with GitHub Actions

Pushing to `main` starts the **Android Build** workflow automatically (also on pull requests to `main`, or manually: **Actions > Android Build > Run workflow**).

* First run takes about 8-15 minutes, later runs about 4-8 minutes.
* When the run has a green check, scroll to **Artifacts** at the bottom of the run page and download:
  * `app-release-aab` -> `app-release.aab` (upload this to Google Play)
  * `app-release-apk` -> `app-release.apk` (signed) or `app-release-unsigned.apk` (no signing secrets yet; cannot be installed)
  * `app-debug-apk` -> `app-debug.apk` (**install this one on your phone to try the app**)
  * `test-reports` (HTML test results)

## 4. Run it in Codespace (optional)

```bash
./gradlew testDebugUnitTest      # unit tests
./gradlew assembleDebug          # debug APK -> app/build/outputs/apk/debug/app-debug.apk
```

The Codespace installs Java 17 and the Android SDK for you (`.devcontainer/setup-android.sh`).

## 5. Release signing

The release build reads four environment variables (GitHub Secrets). If any is missing the release build is simply **unsigned** and still succeeds.

| Secret | Meaning |
|---|---|
| `KEYSTORE_BASE64` | your keystore file encoded as Base64 text |
| `KEYSTORE_PASSWORD` | password of the keystore |
| `KEY_ALIAS` | alias of the key inside the keystore |
| `KEY_PASSWORD` | password of the key |

**Create the keystore** (once, in the Codespace terminal; answer the questions, remember the passwords):

```bash
keytool -genkeypair -v -keystore upload-keystore.jks -alias upload \
  -keyalg RSA -keysize 2048 -validity 10000
```

**Encode it as Base64 and copy the text:**

```bash
base64 -w 0 upload-keystore.jks > keystore.b64
cat keystore.b64
```

**Add the secrets:** GitHub repo > **Settings > Secrets and variables > Actions > New repository secret**. Create the four secrets above (`KEYSTORE_BASE64` = the long text from `keystore.b64`).

**Back it up (important):**

1. In the Codespace Explorer right-click `upload-keystore.jks` > **Download**.
2. Store it in two places (password manager attachment, encrypted USB/cloud vault) together with the alias and both passwords.
3. Delete `upload-keystore.jks` and `keystore.b64` from the Codespace (`rm upload-keystore.jks keystore.b64`).
4. Never commit them. `.gitignore` already blocks `*.jks`, `*.keystore` and `.env`.

When you enrol in **Play App Signing** (default for new apps), this is your *upload key*. If you ever lose it Google can reset it, but that is slow, so keep the backup.

Re-run the workflow after adding secrets; the log step "Report signing mode" tells you whether it signed.

## 6. Publishing on Google Play (checklist)

* Set the app to **Paid before the first publication**. A free app can never be converted to paid afterwards.
* Create a **Google Play developer account** and a **merchant account / payments profile** (needed to sell paid apps). Target price: **USD 2.00** (launch offer: 1.99).
* Upload the signed **`.aab`**, not the APK. Use the APK only for sideloading and testing.
* Complete **App content**: Data safety (no data collected), Privacy policy URL (template in `docs/PRIVACY_POLICY.md`), Content rating questionnaire, Ads = No, App access = all features available, Target audience.
* New personal developer accounts may have to run a closed test with a minimum number of testers before production - check the Play Console dashboard.
* Each upload needs a higher `versionCode`; CI sets it from `GITHUB_RUN_NUMBER` automatically.
* Highlight in the listing: **No ads, no subscription, no tracking, offline-first, one-time purchase, premium themes, editable history tape**. Draft text: `docs/PLAY_STORE_LISTING.md`.

## 7. Development notes

* Math engine: tokenizer -> Pratt parser -> AST -> evaluator. Values are exact `Rational` numbers while possible and 40-digit `BigDecimal` otherwise; display uses 15 significant digits, "full" view 34.
* `0.1 + 0.2` is exactly `0.3` (also available as the fraction `3/10`).
* Percent: `100 + 10%` = 110, `50%` = 0.5, `200 * 10%` = 20.
* Implicit multiplication `2x`, `2(3+4)`, `2pi` works; `1/2x` is read as `1/(2x)` and shows a warning.
* The whole tape logic (edit, stale / updated tracking) is pure Kotlin in `core-data/.../domain` and unit tested without Android.
* Tests: `core-precision`, `core-math`, `core-data` (history persistence, editing, export, Turbine flow test), `app` (keypad input rules), and an instrumented Compose UI test in `app/src/androidTest` (`./gradlew connectedDebugAndroidTest` on a device).

## 8. Troubleshooting quick list

See `docs/BEGINNER_GUIDE.md` for the full table. The three most common problems:

* `./gradlew: Permission denied` -> `chmod +x gradlew`
* `SDK location not found` in Codespace -> `bash .devcontainer/setup-android.sh`
* A red build in Actions -> open the failed step, copy the **first** red error line and send it back with the file path.

## Licence

See `LICENSE` (placeholder, all rights reserved - edit to suit you).
