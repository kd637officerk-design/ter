package com.absolute.one;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.media.MediaMetadata;
import android.media.session.MediaSession;
import android.media.session.PlaybackState;
import android.os.Build;
import android.os.IBinder;
import android.os.PowerManager;
import android.util.Base64;

/**
 * Foreground service that shows the media card in the notification panel / lock screen
 * (title, artwork, seek bar, previous / play-pause / next / close) and keeps the app alive
 * while music plays in the background. The real audio is played by the web page.
 */
public class MediaPlaybackService extends Service {

    public interface Listener {
        void onAction(String action, double seekSeconds);
    }

    static final String CHANNEL = "absolute_playback";
    static final int NOTIF_ID = 7771;
    static final String A_PREV = "com.absolute.one.PREV";
    static final String A_NEXT = "com.absolute.one.NEXT";
    static final String A_TOGGLE = "com.absolute.one.TOGGLE";
    static final String A_CLOSE = "com.absolute.one.CLOSE";
    static final String A_UPDATE = "com.absolute.one.UPDATE";

    static volatile Listener listener;
    static MediaPlaybackService instance;

    static volatile String title = "";
    static volatile String artist = "";
    static volatile String album = "";
    static volatile Bitmap art = null;
    static volatile String state = "none";
    static volatile long durMs = 0;
    static volatile long posMs = 0;
    static volatile float rate = 1f;

    private MediaSession session;
    private PowerManager.WakeLock wake;

    // ---------- static entry points used by the JS bridge ----------

    static void setMeta(Context ctx, String t, String a, String al, String artDataUrl) {
        title = t == null ? "" : t;
        artist = a == null ? "" : a;
        album = al == null ? "" : al;
        art = decode(artDataUrl);
        push(ctx);
    }

    static void setState(Context ctx, String st) {
        state = st == null ? "none" : st;
        push(ctx);
    }

    static void setPos(Context ctx, double dur, double pos, double r) {
        durMs = (long) (dur * 1000);
        posMs = (long) (pos * 1000);
        rate = r <= 0 ? 1f : (float) r;
        push(ctx);
    }

    private static Bitmap decode(String dataUrl) {
        try {
            if (dataUrl == null || dataUrl.length() < 30) return null;
            int i = dataUrl.indexOf(',');
            String b64 = i >= 0 ? dataUrl.substring(i + 1) : dataUrl;
            byte[] bytes = Base64.decode(b64, Base64.DEFAULT);
            return BitmapFactory.decodeByteArray(bytes, 0, bytes.length);
        } catch (Throwable t) {
            return null;
        }
    }

    private static void push(Context ctx) {
        try {
            MediaPlaybackService s = instance;
            if (s != null) {
                s.refresh();
            } else if ("playing".equals(state)) {
                Intent i = new Intent(ctx, MediaPlaybackService.class);
                i.setAction(A_UPDATE);
                if (Build.VERSION.SDK_INT >= 26) {
                    ctx.startForegroundService(i);
                } else {
                    ctx.startService(i);
                }
            }
        } catch (Throwable ignored) {
        }
    }

    private static void fire(String action, double seek) {
        Listener l = listener;
        if (l != null) l.onAction(action, seek);
    }

    // ---------- service lifecycle ----------

    @Override
    public void onCreate() {
        super.onCreate();
        instance = this;
        if (Build.VERSION.SDK_INT >= 26) {
            NotificationChannel ch = new NotificationChannel(CHANNEL, "Music playback",
                    NotificationManager.IMPORTANCE_LOW);
            ch.setShowBadge(false);
            ch.setLockscreenVisibility(Notification.VISIBILITY_PUBLIC);
            NotificationManager nm = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
            nm.createNotificationChannel(ch);
        }
        session = new MediaSession(this, "ABSOLUTE #1");
        session.setFlags(MediaSession.FLAG_HANDLES_MEDIA_BUTTONS | MediaSession.FLAG_HANDLES_TRANSPORT_CONTROLS);
        session.setCallback(new MediaSession.Callback() {
            @Override public void onPlay() { fire("play", 0); }
            @Override public void onPause() { fire("pause", 0); }
            @Override public void onStop() { fire("pause", 0); }
            @Override public void onSkipToNext() { fire("nexttrack", 0); }
            @Override public void onSkipToPrevious() { fire("previoustrack", 0); }
            @Override public void onSeekTo(long pos) { fire("seekto", pos / 1000.0); }
        });
        session.setActive(true);
        PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
        if (pm != null) {
            wake = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "absolute:playback");
            wake.setReferenceCounted(false);
        }
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        String a = intent == null ? null : intent.getAction();
        if (A_PREV.equals(a)) {
            fire("previoustrack", 0);
        } else if (A_NEXT.equals(a)) {
            fire("nexttrack", 0);
        } else if (A_TOGGLE.equals(a)) {
            fire("playing".equals(state) ? "pause" : "play", 0);
        } else if (A_CLOSE.equals(a)) {
            fire("pause", 0);
            state = "none";
        }
        refresh();
        return START_NOT_STICKY;
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onDestroy() {
        instance = null;
        try {
            if (wake != null && wake.isHeld()) wake.release();
        } catch (Throwable ignored) {
        }
        try {
            session.setActive(false);
            session.release();
        } catch (Throwable ignored) {
        }
        super.onDestroy();
    }

    // ---------- notification + session ----------

    void refresh() {
        boolean playing = "playing".equals(state);

        if ("none".equals(state)) {
            try {
                if (wake != null && wake.isHeld()) wake.release();
            } catch (Throwable ignored) {
            }
            stopForeground(true);
            stopSelf();
            return;
        }

        try {
            if (playing) {
                if (wake != null && !wake.isHeld()) wake.acquire();
            } else if (wake != null && wake.isHeld()) {
                wake.release();
            }
        } catch (Throwable ignored) {
        }

        MediaMetadata.Builder mb = new MediaMetadata.Builder()
                .putString(MediaMetadata.METADATA_KEY_TITLE, title)
                .putString(MediaMetadata.METADATA_KEY_ARTIST, artist)
                .putString(MediaMetadata.METADATA_KEY_ALBUM, album)
                .putLong(MediaMetadata.METADATA_KEY_DURATION, durMs);
        Bitmap b = art;
        if (b != null) mb.putBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART, b);
        session.setMetadata(mb.build());

        long actions = PlaybackState.ACTION_PLAY | PlaybackState.ACTION_PAUSE
                | PlaybackState.ACTION_PLAY_PAUSE | PlaybackState.ACTION_SKIP_TO_NEXT
                | PlaybackState.ACTION_SKIP_TO_PREVIOUS | PlaybackState.ACTION_SEEK_TO
                | PlaybackState.ACTION_STOP;
        PlaybackState ps = new PlaybackState.Builder()
                .setActions(actions)
                .setState(playing ? PlaybackState.STATE_PLAYING : PlaybackState.STATE_PAUSED,
                        posMs, playing ? rate : 0f)
                .build();
        session.setPlaybackState(ps);

        Notification n = buildNotification(playing);
        try {
            if (Build.VERSION.SDK_INT >= 29) {
                startForeground(NOTIF_ID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK);
            } else {
                startForeground(NOTIF_ID, n);
            }
        } catch (Throwable t) {
            try {
                NotificationManager nm = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
                nm.notify(NOTIF_ID, n);
            } catch (Throwable ignored) {
            }
        }
    }

    private PendingIntent actionIntent(String action, int code) {
        Intent i = new Intent(this, MediaPlaybackService.class);
        i.setAction(action);
        int flags = PendingIntent.FLAG_UPDATE_CURRENT;
        if (Build.VERSION.SDK_INT >= 23) flags |= PendingIntent.FLAG_IMMUTABLE;
        return PendingIntent.getService(this, code, i, flags);
    }

    private Notification buildNotification(boolean playing) {
        Notification.Builder nb;
        if (Build.VERSION.SDK_INT >= 26) {
            nb = new Notification.Builder(this, CHANNEL);
        } else {
            nb = new Notification.Builder(this);
        }

        Intent open = getPackageManager().getLaunchIntentForPackage(getPackageName());
        int flags = PendingIntent.FLAG_UPDATE_CURRENT;
        if (Build.VERSION.SDK_INT >= 23) flags |= PendingIntent.FLAG_IMMUTABLE;
        PendingIntent contentPi = PendingIntent.getActivity(this, 0, open, flags);

        String text = artist.length() > 0 ? artist : album;
        nb.setSmallIcon(playing ? android.R.drawable.ic_media_play : android.R.drawable.ic_media_pause)
                .setContentTitle(title)
                .setContentText(text)
                .setContentIntent(contentPi)
                .setVisibility(Notification.VISIBILITY_PUBLIC)
                .setOnlyAlertOnce(true)
                .setShowWhen(false)
                .setOngoing(playing)
                .addAction(android.R.drawable.ic_media_previous, "Previous", actionIntent(A_PREV, 1))
                .addAction(playing ? android.R.drawable.ic_media_pause : android.R.drawable.ic_media_play,
                        playing ? "Pause" : "Play", actionIntent(A_TOGGLE, 2))
                .addAction(android.R.drawable.ic_media_next, "Next", actionIntent(A_NEXT, 3))
                .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Close", actionIntent(A_CLOSE, 4))
                .setStyle(new Notification.MediaStyle()
                        .setMediaSession(session.getSessionToken())
                        .setShowActionsInCompactView(0, 1, 2));
        Bitmap b = art;
        if (b != null) nb.setLargeIcon(b);
        return nb.build();
    }
}
