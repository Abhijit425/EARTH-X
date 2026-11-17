# Building & installing the EARTH-X APK

## Option A — download the CI build (easiest)
Every push to `main` builds the APK on GitHub Actions:
- **Release page:** `https://github.com/Abhijit425/EARTH-X/releases/tag/latest` → `EARTH-X-debug.apk`
- Also committed with build logs to the `apk` branch (`output/` contents) and attached as a workflow artifact.

The CI build also downloads NASA's large Blue Marble / Black Marble images and bakes 8K (8192×4096)
"Ultra HD" textures into the APK (see `tools/fetch_hd_textures.py`).

## Option B — build locally
Requirements: JDK 17, Android SDK (platform 36, build-tools 36), internet access for Gradle.
```bash
python3 tools/fetch_hd_textures.py        # optional: 8K Ultra HD textures (needs internet)
./gradlew testDebugUnitTest                # unit tests
./gradlew assembleDebug                    # → app/build/outputs/apk/debug/app-debug.apk
```
Package name: `com.earthx.explorer` · minSdk 26 (Android 8.0) · targetSdk 36.

### Release APK
Create `keystore.properties` in the project root (never commit it):
```
storeFile=release.keystore
storePassword=…
keyAlias=…
keyPassword=…
```
then `./gradlew assembleRelease`. On CI, add the secrets `KEYSTORE_B64`, `KEYSTORE_PASSWORD`, `KEY_ALIAS`,
`KEY_PASSWORD` and a signed `EARTH-X-release.apk` is produced as well.

## Installing on the tablet
1. On the tablet, open the release page above in the browser and download `EARTH-X-debug.apk`
   (or copy it over USB).
2. Open the file. When Android asks, allow **Install unknown apps** for your browser / file manager.
3. Tap **Install**, then **Open**.
Via USB debugging: `adb install -r EARTH-X-debug.apk`.

Requirements on the device: Android 8.0+, OpenGL ES 3.0 (WebGL2), an up-to-date Android System WebView /
Chrome. Ultra HD mode needs a GPU with max texture size ≥ 8192 (automatically detected; otherwise 4K is used).
