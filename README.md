# ABSOLUTE #1

Upload everything to your GitHub repo (keep the folder structure), then
Actions -> Build APK -> Run workflow, and download "ABSOLUTE-1-apk".

- `www/index.html`   the app
- `native/`          Android code: notification/lock-screen player, phone music scanner
- `scripts/patch_android.py`  adds permissions + the player service during the build
- `android-res/`     app icon
- `.github/workflows/build-apk.yml`  the build (must contain the step "Add native player, permissions and scanner")
