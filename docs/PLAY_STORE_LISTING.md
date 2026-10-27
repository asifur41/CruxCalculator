# Google Play listing draft

## Name

The app is called **CruxCalculator** (`app_name` in `app/src/main/res/values/strings.xml`).
Before you publish, search Google Play for "Crux" to see whether similar calculator apps already use it, and check the trademark databases for your country.

The Android **application id** is `com.cruxcalculator.app` (set in `app/build.gradle.kts`). It becomes permanent the moment you upload to Google Play, so change it now if you want a different one.

## Short description (max 80 characters)

> Private, ad-free calculator with an editable history tape. Pay once.

## Full description

**The private, ad-free calculator for people who actually calculate.**

Pay once. No ads, ever. No subscription. No tracking. No account. Works fully offline.

**An editable calculation tape**
* Every calculation lands on a continuous tape. Tap any line to fix it - later results that use `ans` or a variable update automatically (or are flagged stale, your choice).
* Add labels, notes and #tags: "Rent", "Client quote", "Physics homework".
* Search, pin, duplicate and delete. Export as plain text, Markdown, CSV or shareable text.
* Separate workspaces: Personal, Work, Study, Engineering.

**Accurate maths**
* 0.1 + 0.2 is exactly 0.3. Exact fractions (1/3), repeating decimals (0.(3)), 34-digit internal precision.
* Correct operator precedence, percentages that behave like a shop receipt (100 + 10% = 110), parentheses, implicit multiplication.
* Scientific mode: trig and hyperbolic functions in Deg / Rad / Grad, logs, powers, roots, factorials, permutations and combinations, constants.

**Unit conversion, offline**
* Length, mass, volume, temperature, speed, area, data, pressure, energy, time, angle and fuel economy.
* Type it inline: `12 km to mi`, `5 kg to lb`, `72 F to C`.

**Variables**
* `rate = 0.18` then `200 * rate`. Smart paste understands "$1,234.50" and "1.234,56".

**Beautiful and accessible**
* 12 themes including AMOLED Black, Nord, Dracula, Solarized and Material You. Custom accent colour, key styles and shapes.
* Left- and right-handed layouts, haptic strength control, two-pane layout for tablets and foldables, full TalkBack support and large-text support.

**Private by design**
* The app does not even request the internet permission. Your numbers never leave your device.

## Data safety form answers (Play Console > App content)

* Does your app collect or share any of the required user data types? **No**
* Is all of the user data collected by your app encrypted in transit? Not applicable (no data collected).
* Do you provide a way for users to request data deletion? Not applicable (no data collected; uninstall deletes local data).

## Other declarations

* Privacy policy URL: host `docs/PRIVACY_POLICY.md` somewhere public (for example GitHub Pages) and paste the link.
* App access: all functionality is available without special access.
* Ads: **No, my app does not contain ads.**
* Content rating: questionnaire, utility app, no sensitive content -> Everyone.
* Target audience: 13+ / general audience (not designed for children).
* Pricing: **Paid**, USD 2.00 (optional launch price 1.99). Choose Paid BEFORE the first publication; a free app can never be changed to paid.
