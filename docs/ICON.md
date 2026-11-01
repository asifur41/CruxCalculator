# App icon

The app uses the Crux Calculator logo (a red ring of calculator symbols joined by a blue path) as its launcher icon.

## Files that make up the icon

| File | Purpose |
|---|---|
| `app/src/main/res/drawable-nodpi/ic_launcher_foreground.png` | The artwork: 432 x 432 px (a 108 x 108 dp canvas at 4x). The logo is kept inside the centre 66 x 66 dp so no phone's icon shape cuts it off |
| `app/src/main/res/values/colors.xml` and `values-night/colors.xml` -> `ic_launcher_background` | The solid background colour (white) |
| `app/src/main/res/drawable-nodpi/ic_launcher_monochrome.png` | One-colour version (shape in the alpha channel) for Android 13+ "themed icons" |
| `app/src/main/res/mipmap-anydpi-v26/ic_launcher.xml` and `ic_launcher_round.xml` | Wire foreground + background + monochrome together. The manifest points at these two |
| `docs/play-store-icon-512.png` | 512 x 512 PNG for the Google Play listing (uploaded in Play Console, not used by the app) |

The app's minimum Android version is 8.0 (API 26), which always supports adaptive icons, so no older per-density
launcher images are needed.

## Changing the icon later

1. Prepare your logo as a PNG with the symbol centred and generous margins.
2. Make two 432 x 432 PNGs: the full-colour foreground and a one-colour version (the shape drawn in any colour on a transparent background) for the monochrome layer.
3. Replace the two PNGs in `app/src/main/res/drawable-nodpi/` (keep the file names) and change `ic_launcher_background` in both `colors.xml` files if the background colour changes.
4. Make a 512 x 512 PNG for Play and replace `docs/play-store-icon-512.png`.
5. Commit and push; GitHub Actions will rebuild.

## Google Play also needs (uploaded in Play Console, not in the app)

* App icon: 512 x 512 PNG, 32-bit (`docs/play-store-icon-512.png`).
* Feature graphic: 1024 x 500 PNG or JPG.
* At least 2 phone screenshots (take them from the debug APK on your phone).
