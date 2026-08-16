# Slight Morphe Patches

Morphe patches for Android apps by Slight.

## ❓ About

Custom patches built for the Morphe patcher framework.

### How to use these patches

Click here to add these patches to Morphe: https://morphe.software/add-source?github=HSlightsteel/slight-patches

## 🩹 Patches list

<!-- PATCHES_START EXPANDED -->

#### Included Patches

- **AT4K Launcher** (`com.overdevs.at4k`)
  - **Unlock Premium**: Unlocks AT4K Launcher's premium features (wallpapers, extra apps per row, widgets) without requiring purchase or license activation.
  - **Disable License Check** (dependency): Bypasses the PairIP Google Play license verification to allow running modified/re-signed builds.

&nbsp;

<!-- PATCHES_END -->

### 🛠️ Building locally

- Run `./gradlew buildAndroid` (or `gradlew.bat buildAndroid`)
- The built patches `.mpp` file is found in `patches/build/libs/patches-*.mpp`
- Apply the patch bundle using [Morphe Desktop](https://github.com/MorpheApp/morphe-desktop) or the Morphe Manager app.

See the [Morphe documentation](https://github.com/MorpheApp/morphe-documentation) for more information.

## 📜 License

Slight Patches are licensed under the [GNU General Public License v3.0](LICENSE)
