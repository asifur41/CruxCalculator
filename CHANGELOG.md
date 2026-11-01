# Changelog

## Unreleased

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
