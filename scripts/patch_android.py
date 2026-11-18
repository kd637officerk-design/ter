"""Run after `npx cap add android`: copies the native Java files and adds
permissions + the playback service to the Android manifest."""
import os, re, shutil

PKG_DIR = "android/app/src/main/java/com/absolute/one"
MANIFEST = "android/app/src/main/AndroidManifest.xml"

os.makedirs(PKG_DIR, exist_ok=True)
for f in os.listdir("native"):
    if f.endswith(".java"):
        shutil.copy(os.path.join("native", f), os.path.join(PKG_DIR, f))
        print("copied", f)

PERMS = [
    '<uses-permission android:name="android.permission.READ_MEDIA_AUDIO"/>',
    '<uses-permission android:name="android.permission.READ_MEDIA_IMAGES"/>',
    '<uses-permission android:name="android.permission.READ_MEDIA_VIDEO"/>',
    '<uses-permission android:name="android.permission.READ_EXTERNAL_STORAGE" android:maxSdkVersion="32"/>',
    '<uses-permission android:name="android.permission.WRITE_EXTERNAL_STORAGE" android:maxSdkVersion="29"/>',
    '<uses-permission android:name="android.permission.MANAGE_EXTERNAL_STORAGE"/>',
    '<uses-permission android:name="android.permission.RECORD_AUDIO"/>',
    '<uses-permission android:name="android.permission.MODIFY_AUDIO_SETTINGS"/>',
    '<uses-permission android:name="android.permission.POST_NOTIFICATIONS"/>',
    '<uses-permission android:name="android.permission.FOREGROUND_SERVICE"/>',
    '<uses-permission android:name="android.permission.FOREGROUND_SERVICE_MEDIA_PLAYBACK"/>',
    '<uses-permission android:name="android.permission.WAKE_LOCK"/>',
    '<uses-permission android:name="android.permission.BLUETOOTH_CONNECT"/>',
    '<uses-permission android:name="android.permission.BLUETOOTH" android:maxSdkVersion="30"/>',
    '<uses-permission android:name="android.permission.SYSTEM_ALERT_WINDOW"/>',
]

m = open(MANIFEST, encoding="utf-8").read()

block = "\n    ".join(p for p in PERMS if p.split('"')[1] not in m)
if block:
    m = m.replace("<application", "    " + block + "\n\n    <application", 1)

if "requestLegacyExternalStorage" not in m:
    m = m.replace("<application", '<application android:requestLegacyExternalStorage="true"', 1)

if "MediaPlaybackService" not in m:
    svc = ('    <service android:name=".MediaPlaybackService" android:exported="false" '
           'android:foregroundServiceType="mediaPlayback"/>\n')
    m = m.replace("</application>", svc + "    </application>", 1)

open(MANIFEST, "w", encoding="utf-8").write(m)
print(m)
