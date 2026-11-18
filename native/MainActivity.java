package com.absolute.one;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.Settings;
import android.webkit.JavascriptInterface;
import android.webkit.WebView;

import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.getcapacitor.BridgeActivity;

import java.util.ArrayList;
import java.util.List;

public class MainActivity extends BridgeActivity {
    private static final int REQ_PERMS = 4242;
    private boolean askedAllFiles = false;
    private boolean waitingAllFiles = false;
    private MediaPlaybackService.Listener myListener;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        registerPlugin(LocalMusicPlugin.class);
        super.onCreate(savedInstanceState);

        final WebView wv = getBridge().getWebView();
        wv.getSettings().setMediaPlaybackRequiresUserGesture(false);
        wv.addJavascriptInterface(new NativeBridge(), "KD6Native");

        myListener = new MediaPlaybackService.Listener() {
            @Override
            public void onAction(final String action, final double seekSeconds) {
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        String js;
                        if ("seekto".equals(action)) {
                            js = "window.__kd6act&&window.__kd6act('seekto',{seekTime:" + seekSeconds + "})";
                        } else {
                            js = "window.__kd6act&&window.__kd6act('" + action + "',{})";
                        }
                        getBridge().getWebView().evaluateJavascript(js, null);
                    }
                });
            }
        };
        MediaPlaybackService.listener = myListener;

        requestRuntimePermissions();
    }

    @Override
    public void onDestroy() {
        if (MediaPlaybackService.listener == myListener) {
            MediaPlaybackService.listener = null;
        }
        super.onDestroy();
    }

    @Override
    public void onResume() {
        super.onResume();
        if (waitingAllFiles) {
            waitingAllFiles = false;
            triggerScan();
        }
    }

    private void triggerScan() {
        try {
            getBridge().getWebView().evaluateJavascript("window.__kd6scan&&window.__kd6scan()", null);
        } catch (Exception ignored) {
        }
    }

    private void requestRuntimePermissions() {
        List<String> need = new ArrayList<String>();
        if (Build.VERSION.SDK_INT >= 33) {
            add(need, Manifest.permission.READ_MEDIA_AUDIO);
            add(need, Manifest.permission.READ_MEDIA_IMAGES);
            add(need, Manifest.permission.READ_MEDIA_VIDEO);
            add(need, Manifest.permission.POST_NOTIFICATIONS);
        } else {
            add(need, Manifest.permission.READ_EXTERNAL_STORAGE);
            if (Build.VERSION.SDK_INT <= 29) {
                add(need, Manifest.permission.WRITE_EXTERNAL_STORAGE);
            }
        }
        add(need, Manifest.permission.RECORD_AUDIO);
        if (Build.VERSION.SDK_INT >= 31) {
            add(need, Manifest.permission.BLUETOOTH_CONNECT);
        }
        if (need.isEmpty()) {
            askAllFilesAccess();
        } else {
            ActivityCompat.requestPermissions(this, need.toArray(new String[0]), REQ_PERMS);
        }
    }

    private void add(List<String> list, String perm) {
        if (ContextCompat.checkSelfPermission(this, perm) != PackageManager.PERMISSION_GRANTED) {
            list.add(perm);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQ_PERMS) {
            triggerScan();
            askAllFilesAccess();
        }
    }

    /** "All files access" = real-time access to the whole internal storage. */
    private void askAllFilesAccess() {
        if (askedAllFiles) return;
        askedAllFiles = true;
        if (Build.VERSION.SDK_INT >= 30 && !Environment.isExternalStorageManager()) {
            try {
                Intent i = new Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
                        Uri.parse("package:" + getPackageName()));
                waitingAllFiles = true;
                startActivity(i);
            } catch (Exception e) {
                try {
                    waitingAllFiles = true;
                    startActivity(new Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION));
                } catch (Exception ignored) {
                    waitingAllFiles = false;
                }
            }
        }
    }

    /** Called from the web page as window.KD6Native */
    public class NativeBridge {
        @JavascriptInterface
        public void hello() {
        }

        @JavascriptInterface
        public void meta(String title, String artist, String album, String artDataUrl) {
            MediaPlaybackService.setMeta(getApplicationContext(), title, artist, album, artDataUrl);
        }

        @JavascriptInterface
        public void state(String st) {
            MediaPlaybackService.setState(getApplicationContext(), st);
        }

        @JavascriptInterface
        public void pos(double durationSec, double positionSec, double rate) {
            MediaPlaybackService.setPos(getApplicationContext(), durationSec, positionSec, rate);
        }
    }
}
