# App icon

The project ships with a **placeholder** adaptive icon (a paper tape with a plus sign) so it compiles and installs.
Replace it before you publish.

## Files that make up the icon

| File | Purpose |
|---|---|
| `app/src/main/res/drawable/ic_launcher_foreground.xml` | The artwork (vector, 108 x 108 dp canvas, keep important parts inside the centre 66 x 66 dp) |
| `app/src/main/res/values/colors.xml` -> `ic_launcher_background` | The solid background colour |
| `app/src/main/res/drawable/ic_launcher_monochrome.xml` | One-colour version for Android 13+ "themed icons" |
| `app/src/main/res/mipmap-anydpi-v26/ic_launcher.xml` and `ic_launcher_round.xml` | Wire foreground + background + monochrome together |

## Easiest way to get a professional icon (no Android Studio needed)

1. Design a 1024 x 1024 PNG (for example in Figma, Canva or Inkscape). Keep the symbol centred with generous margins.
2. Convert it to Android vector XML with an online "SVG to Android Vector Drawable" converter (draw the symbol as SVG first), or keep a PNG:
   - Put PNGs named `ic_launcher_foreground.png` (432 x 432) in `app/src/main/res/drawable-xxxhdpi/` and delete the XML foreground of the same name.
3. Commit and push; GitHub Actions will rebuild.

## Google Play also needs (uploaded in Play Console, not in the repo)

* App icon: 512 x 512 PNG, 32-bit, no transparency needed.
* Feature graphic: 1024 x 500 PNG or JPG.
* At least 2 phone screenshots (take them from the debug APK on your phone).
