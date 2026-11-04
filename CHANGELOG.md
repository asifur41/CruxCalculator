# Changelog

## Unreleased

### Changed (update 6)
* The CI now builds an **installable, optimized release APK** (`app-release.apk`) signed with a public test key, so
  the real speed of the app can be tested. The debug APK is much slower and larger by design. Google Play builds are
  unaffected: real signing secrets always win and the AAB is never signed with the test key.
* New `docs/PERFORMANCE.md`: why debug builds are slow, why the installed size grows, and how to measure.

### Added (update 5)
* **All 47 constants of the Casio fx-991EX list** (23 new: Z0, Planck length/time, nuclear magneton, flux quantum,
  conductance quantum, Josephson and von Klitzing constants (and their 1990 values), muon / tau masses, classical electron
  radius, Compton wavelengths, proton / electron / neutron / muon magnetic moments, proton gyromagnetic ratio, first and
  second radiation constants). The Constants tool now lists them in the same five groups as the Casio list. See
  `docs/CONSTANTS_AND_UNITS.md`.
* **All 40 conversion directions** of that list: new parsec, kgf/cm2, horsepower <-> kW (new Power category) and kgf·m,
  plus light-year and astronomical unit.

### Added (update 4)
* **Casio-style scientific keypad**: SHIFT, ALPHA, HYP layers (one-shot), D-pad cursor pad, MODE, CALC, integral, x^-1,
  log base, a/b, root, x^2, x^y, log, ln, (-), degrees-minutes-seconds, hyp, trig, RCL / STO, ENG, S<=>D, M+ / M-,
  EXP and Ans. The existing dark theme, teal accents and rounded keys are kept.
* **Single bracket key** on the scientific keypad: tap for "(", long-press for ")". Basic keeps two keys.
* **Movable cursor** with natural-display templates: fraction, mixed fraction, log base, n-th root, integral, sum, product
  and derivative with empty boxes to fill in; up/down jump between boxes; long equations wrap onto several lines.
* **Taller scientific display** with automatic font shrinking; tap a tape line to bring its equation back for editing.
* **Variables screen**: A-Z (without E), Ans, PreAns, 3x3 matrices MatA-MatD and vectors VctA-VctD, each with an input
  box, a bin and a send arrow.
* **Graph**: full screen, dark grid with x / y labels, floating Zoom in, Zoom out, Home and Centre buttons, smooth pan
  and pinch.
* Distinct icons for every tool.

### Fixed (update 4)
* The on-screen keyboard no longer appears by itself after visiting QR, going back and reopening the app; it opens
  only when a text box is tapped.
* QR opens only when its button is tapped and is not remembered.
* Lag: only the visible tab is built; the graph draws fewer samples while you move it.
* Swipe navigation between Basic / Scientific / Convert / Tools is removed; use the bottom bar.

### Known limits (update 4)
* Casio keys with no engine support yet are left out: angle sign, factorial-key variants, Ran#, CLRv, cot, i.
* Sending a matrix or vector to its tool copies the values and opens the tool; paste them there.

### Added (update 3)
* **Calculator language**: Fix / Norm 1 / Norm 2 number formats (Settings, with 0-9 decimal places for Fix); colon
  statements (`A=5 : B=A*2 : B+1`); degrees-minutes-seconds input (`2°30'15"`, `45°`) and DMS read-out; `Pol(x,y)` and
  `Rec(r,θ)` (sets X, Y); 25 physical constants (`c0`, `hP`, `NA`, `kB`...); results shown as multiples of π (`2π/3`) and
  simple radicals (`3√2`); `atan2`, `hypot`, `gcd`, `lcm`; `sum(f,k,a,b)`, `prod(...)`, `integral(f,x,a,b)`,
  `deriv(f,x,a)`, `solve(f,x,guess)`.
* **Tools tab** (new): polynomial roots to degree 4, simultaneous equations (up to 4 unknowns), SOLVE, calculus, symbolic
  algebra (simplify / expand / factor / derivative / integral), one- and two-variable statistics with 7 regression types and
  predictions, normal / binomial / Poisson distributions, BASE-N logic with 8/16/32/64-bit two's complement, matrices,
  eigenvalues, vectors, complex numbers, step-by-step solutions, graphing (3 functions, pan / zoom / trace / zeros / peaks /
  intersections), constants table, and an offline QR-code maker.
* Statements that set several variables now save all of them to Variables and recalculate correctly on the tape.

### Not included in update 3
* Memory keys (M+, M-, MR, MC) and SHIFT/ALPHA keys on the keypad: use variables (`M = ans`, then `M + 5`) instead.
* Tools results are not saved to the tape (use the "Use" button for single numbers).

### Fixed (debug update 1)
* Slow start-up and the brief default-theme flash: the saved settings are now read instantly before the first
  frame (small local copy, DataStore stays the source of truth) and the window is painted in the saved theme colour.
* Smoothness: the key display, tape and editor now observe their own state, so a key press no longer recomposes the
  whole app; handlers are remembered; only the first tab is built at start-up and the rest are prepared right after.
* Tab changes now use an eased slide with a fade and slight scale; Settings slides over the calculator.
* Tape: tapping an entry opens the editor (label, note, tags). The whole card is the touch target and shows an edit icon;
  the tape jumps to the newest entry instead of a long animated scroll.
* Paste: events are handled independently of snackbars (a pending message used to queue paste events that then fired
  together), paste is limited to once per gesture, and the long-press paste no longer goes through the event channel.

### Added (debug update 1)
* Convert tab: all categories plus "Number system" are a static 5-column grid over 3 rows (no sideways scrolling).
* One-time swipeable onboarding with a "Get Started" button; replay it from Settings.
* Settings: Developer group (name, email, country, website) and Support group (community, latest APK, GitHub,
  review, bug report, donate, replay tutorial, version). Links live in `AppInfo.kt`.

### Added
* Number-system conversion in the Convert tab (new "Number system" chip): binary, ternary, quaternary, senary,
  octal, decimal, duodecimal, hexadecimal, base 32 and base 36. Whole numbers of any size, negative numbers,
  separators (`1111 1111`) and prefixes (`0b`, `0o`, `0x`). The result card also lists BIN / OCT / DEC / HEX.
  Logic is in `core-math` (`NumberBases`), tests in `NumberBasesTest`.
* New Crux Calculator launcher icon (adaptive icon with a themed monochrome layer) and a 512 px Play Store icon.
* "Did you mean ...?" hints for unknown functions, constants, variables and units
  (`sinn(30)` -> `Did you mean 'sin'?`, `5 kg to pund` -> `Did you mean 'pound'?`).
  The suggestion is also available as `CalcError.suggestion` for a future one-tap fix.
* Number notation setting (Settings > Calculation): Auto, Scientific (`1.2345E4`) or
  Engineering (`12.345E3`). It changes only how the main result and the live preview are shown.
* `NumberFormatter.formatScientific` and `formatEngineering` in `core-precision`.
* Tests: `SuggestionsTest`, `ErrorMessagesTest`, `NumberNotationTest` (core-math) and `NotationTest` (core-precision).

### Changed
* App name is now "Crux Calculator" (`app_name`); the launcher label is the shorter "Crux Calc" (`app_name_short`).
* Clearer error messages:
  * `5 +` -> `'+' needs a number after it`
  * `2+*3` -> `'*' needs a number before it`
  * `()` / `3(` -> `Nothing inside the parentheses`
  * `sin()` -> `sin needs 1 value but got 0`
  * `5 xyz to km` -> `Unknown unit 'xyz'. Example: 12 km to mi` (names the unit that was not understood)
* Error kinds are unchanged, so existing behaviour that depends on them is unaffected.

### Not changed
* Calculation results, tape storage format, exports and the database schema.
