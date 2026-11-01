# Changelog

## Unreleased

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
