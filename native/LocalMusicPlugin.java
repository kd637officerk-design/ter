package com.absolute.one;

import android.Manifest;
import android.content.ContentResolver;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.provider.MediaStore;
import android.provider.Settings;
import android.util.Base64;

import androidx.core.content.ContextCompat;

import com.getcapacitor.JSArray;
import com.getcapacitor.JSObject;
import com.getcapacitor.Plugin;
import com.getcapacitor.PluginCall;
import com.getcapacitor.PluginMethod;
import com.getcapacitor.annotation.CapacitorPlugin;

import java.io.File;
import java.io.FileOutputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

@CapacitorPlugin(name = "LocalMusic")
public class LocalMusicPlugin extends Plugin {

    private boolean hasAudioPermission() {
        String perm = Build.VERSION.SDK_INT >= 33
                ? Manifest.permission.READ_MEDIA_AUDIO
                : Manifest.permission.READ_EXTERNAL_STORAGE;
        return ContextCompat.checkSelfPermission(getContext(), perm) == PackageManager.PERMISSION_GRANTED;
    }

    private boolean allFiles() {
        if (Build.VERSION.SDK_INT >= 30) return Environment.isExternalStorageManager();
        return ContextCompat.checkSelfPermission(getContext(), Manifest.permission.READ_EXTERNAL_STORAGE)
                == PackageManager.PERMISSION_GRANTED;
    }

    /** Scans every song in the phone's media index (no picking one by one). */
    @PluginMethod
    public void list(PluginCall call) {
        if (!hasAudioPermission()) {
            call.reject("Permission denied: Music and audio");
            return;
        }
        double minSeconds = call.getDouble("minSeconds", 0.0);
        JSArray songs = new JSArray();
        Cursor c = null;
        try {
            ContentResolver cr = getContext().getContentResolver();
            String[] proj = new String[]{
                    MediaStore.Audio.Media.TITLE,
                    MediaStore.Audio.Media.ARTIST,
                    MediaStore.Audio.Media.ALBUM,
                    MediaStore.Audio.Media.DURATION,
                    MediaStore.Audio.Media.DATA,
                    MediaStore.Audio.Media.DISPLAY_NAME
            };
            String sel = MediaStore.Audio.Media.IS_MUSIC + " != 0";
            c = cr.query(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, proj, sel, null,
                    MediaStore.Audio.Media.TITLE + " COLLATE NOCASE ASC");
            if (c != null) {
                while (c.moveToNext()) {
                    String path = c.getString(4);
                    if (path == null || path.length() == 0) continue;
                    long durMs = c.getLong(3);
                    if (durMs / 1000.0 < minSeconds) continue;
                    String artist = c.getString(1);
                    if (artist == null || "<unknown>".equals(artist)) artist = "";
                    String album = c.getString(2);
                    if (album == null || "<unknown>".equals(album)) album = "";
                    String folder = "";
                    File parent = new File(path).getParentFile();
                    if (parent != null) folder = parent.getName();
                    JSObject o = new JSObject();
                    o.put("path", path);
                    o.put("title", c.getString(0) == null ? "" : c.getString(0));
                    o.put("name", c.getString(5) == null ? "" : c.getString(5));
                    o.put("artist", artist);
                    o.put("album", album);
                    o.put("duration", durMs / 1000.0);
                    o.put("folder", folder);
                    songs.put(o);
                }
            }
        } catch (Exception e) {
            call.reject("Could not read music: " + e.getMessage());
            return;
        } finally {
            if (c != null) c.close();
        }
        JSObject ret = new JSObject();
        ret.put("songs", songs);
        call.resolve(ret);
    }

    @PluginMethod
    public void hasAllFiles(PluginCall call) {
        JSObject r = new JSObject();
        r.put("value", allFiles());
        call.resolve(r);
    }

    @PluginMethod
    public void requestAllFiles(PluginCall call) {
        try {
            if (Build.VERSION.SDK_INT >= 30) {
                Intent i;
                try {
                    i = new Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
                            Uri.parse("package:" + getContext().getPackageName()));
                    i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    getContext().startActivity(i);
                } catch (Exception e) {
                    i = new Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION);
                    i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    getContext().startActivity(i);
                }
            }
            call.resolve();
        } catch (Exception e) {
            call.reject(String.valueOf(e.getMessage()));
        }
    }

    @PluginMethod
    public void openAppSettings(PluginCall call) {
        try {
            Intent i = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                    Uri.parse("package:" + getContext().getPackageName()));
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            getContext().startActivity(i);
            call.resolve();
        } catch (Exception e) {
            call.reject(String.valueOf(e.getMessage()));
        }
    }

    @PluginMethod
    public void canOverlay(PluginCall call) {
        JSObject r = new JSObject();
        boolean ok = Build.VERSION.SDK_INT < 23 || Settings.canDrawOverlays(getContext());
        r.put("value", ok);
        call.resolve(r);
    }

    @PluginMethod
    public void requestOverlay(PluginCall call) {
        try {
            Intent i = new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:" + getContext().getPackageName()));
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            getContext().startActivity(i);
            call.resolve();
        } catch (Exception e) {
            call.reject(String.valueOf(e.getMessage()));
        }
    }

    // The notification-panel player is handled by MediaPlaybackService, so these are no-ops.
    @PluginMethod
    public void setLockEnabled(PluginCall call) {
        call.resolve();
    }

    @PluginMethod
    public void setNowPlaying(PluginCall call) {
        call.resolve();
    }

    @PluginMethod
    public void openUrl(PluginCall call) {
        try {
            String url = call.getString("url");
            if (url == null) {
                call.reject("No url");
                return;
            }
            Intent i = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            getContext().startActivity(i);
            call.resolve();
        } catch (Exception e) {
            call.reject(String.valueOf(e.getMessage()));
        }
    }

    /** Newest picture added to the phone since the given time (seconds). */
    @PluginMethod
    public void latestImage(PluginCall call) {
        JSObject r = new JSObject();
        Cursor c = null;
        try {
            double since = call.getDouble("since", 0.0);
            String[] proj = new String[]{MediaStore.Images.Media.DATA};
            c = getContext().getContentResolver().query(
                    MediaStore.Images.Media.EXTERNAL_CONTENT_URI, proj,
                    MediaStore.Images.Media.DATE_ADDED + " >= ?",
                    new String[]{String.valueOf((long) since)},
                    MediaStore.Images.Media.DATE_ADDED + " DESC");
            if (c != null && c.moveToFirst()) {
                String p = c.getString(0);
                if (p != null) r.put("path", p);
            }
        } catch (Exception ignored) {
        } finally {
            if (c != null) c.close();
        }
        call.resolve(r);
    }

    @PluginMethod
    public void listStorage(PluginCall call) {
        try {
            String path = call.getString("path", "/storage/emulated/0");
            File dir = new File(path);
            File[] files = dir.listFiles();
            JSArray entries = new JSArray();
            if (files != null) {
                for (File f : files) {
                    JSObject o = new JSObject();
                    o.put("name", f.getName());
                    o.put("path", f.getAbsolutePath());
                    o.put("directory", f.isDirectory());
                    o.put("size", f.length());
                    o.put("modified", f.lastModified());
                    entries.put(o);
                }
            }
            JSObject r = new JSObject();
            r.put("entries", entries);
            call.resolve(r);
        } catch (Exception e) {
            call.reject(String.valueOf(e.getMessage()));
        }
    }

    @PluginMethod
    public void listDir(PluginCall call) {
        try {
            String dir = call.getString("dir", "");
            final String ext = call.getString("ext", "");
            String[] names = new File(dir).list();
            List<String> out = new ArrayList<String>();
            if (names != null) {
                for (String n : names) {
                    if (ext.length() == 0 || n.endsWith(ext)) out.add(n);
                }
            }
            Collections.sort(out, Collections.reverseOrder());
            JSArray arr = new JSArray();
            for (String n : out) arr.put(n);
            JSObject r = new JSObject();
            r.put("names", arr);
            call.resolve(r);
        } catch (Exception e) {
            call.reject(String.valueOf(e.getMessage()));
        }
    }

    @PluginMethod
    public void writeFile(PluginCall call) {
        try {
            String path = call.getString("path");
            String data = call.getString("data", "");
            boolean keepTime = call.getBoolean("keepTime", false);
            if (path == null) {
                call.reject("No path");
                return;
            }
            File f = new File(path);
            long lm = f.exists() ? f.lastModified() : 0;
            File parent = f.getParentFile();
            if (parent != null && !parent.exists()) parent.mkdirs();
            byte[] bytes = Base64.decode(data, Base64.DEFAULT);
            FileOutputStream fos = new FileOutputStream(f);
            try {
                fos.write(bytes);
            } finally {
                fos.close();
            }
            if (keepTime && lm > 0) f.setLastModified(lm);
            call.resolve();
        } catch (Exception e) {
            call.reject(String.valueOf(e.getMessage()));
        }
    }

    @PluginMethod
    public void deleteFile(PluginCall call) {
        try {
            String path = call.getString("path");
            if (path == null) {
                call.reject("No path");
                return;
            }
            File f = new File(path);
            if (f.exists() && !f.delete()) {
                call.reject("Could not delete the file");
                return;
            }
            try {
                getContext().getContentResolver().delete(
                        MediaStore.Files.getContentUri("external"),
                        MediaStore.Files.FileColumns.DATA + "=?", new String[]{path});
            } catch (Exception ignored) {
            }
            call.resolve();
        } catch (Exception e) {
            call.reject(String.valueOf(e.getMessage()));
        }
    }
}
