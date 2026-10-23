package com.pulsoglobal.app;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.ServiceInfo;
import android.graphics.drawable.Icon;
import android.media.AudioAttributes;
import android.media.AudioFocusRequest;
import android.media.AudioManager;
import android.media.MediaMetadata;
import android.media.MediaPlayer;
import android.media.session.MediaSession;
import android.media.session.PlaybackState;
import android.net.wifi.WifiManager;
import android.os.Build;
import android.os.IBinder;
import android.os.PowerManager;

/**
 * Reproduce la radio en un servicio en primer plano: sigue sonando con la app
 * cerrada y muestra controles en la notificación y en la pantalla de bloqueo
 * (MediaSession + MediaStyle).
 */
public class RadioService extends Service implements MediaPlayer.OnPreparedListener,
        MediaPlayer.OnErrorListener, MediaPlayer.OnInfoListener, AudioManager.OnAudioFocusChangeListener {

    public static final String ACTION_PLAY = "com.pulsoglobal.app.radio.PLAY";
    public static final String ACTION_TOGGLE = "com.pulsoglobal.app.radio.TOGGLE";
    public static final String ACTION_STOP = "com.pulsoglobal.app.radio.STOP";

    private static final String CHANNEL = "radio";
    private static final int NOTIF_ID = 42;

    /** Escucha opcional (la Activity) para reflejar el estado en la interfaz. */
    public interface Listener {
        void onState(String state, String name);
    }

    public static volatile Listener listener;
    public static volatile String state = "stopped";
    public static volatile String stationName;

    private MediaPlayer player;
    private MediaSession session;
    private AudioManager audio;
    private AudioFocusRequest focusRequest;
    private WifiManager.WifiLock wifiLock;
    private String url;
    private boolean pausedByFocus;
    private boolean noisyRegistered;

    private final BroadcastReceiver noisy = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (AudioManager.ACTION_AUDIO_BECOMING_NOISY.equals(intent.getAction())) pause();
        }
    };

    @Override
    public void onCreate() {
        super.onCreate();
        audio = (AudioManager) getSystemService(AUDIO_SERVICE);
        if (Build.VERSION.SDK_INT >= 26) {
            NotificationChannel ch = new NotificationChannel(CHANNEL, "Radio en vivo", NotificationManager.IMPORTANCE_LOW);
            ch.setShowBadge(false);
            ((NotificationManager) getSystemService(NOTIFICATION_SERVICE)).createNotificationChannel(ch);
        }
        session = new MediaSession(this, "PulsoRadio");
        session.setCallback(new MediaSession.Callback() {
            @Override
            public void onPlay() {
                resume();
            }

            @Override
            public void onPause() {
                pause();
            }

            @Override
            public void onStop() {
                stopAll();
            }
        });
        session.setActive(true);
        WifiManager wm = (WifiManager) getApplicationContext().getSystemService(WIFI_SERVICE);
        if (wm != null) wifiLock = wm.createWifiLock(WifiManager.WIFI_MODE_FULL_HIGH_PERF, "pulso:radio");
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        String action = intent == null ? null : intent.getAction();
        if (ACTION_PLAY.equals(action)) {
            url = intent.getStringExtra("url");
            stationName = intent.getStringExtra("name");
            start();
        } else if (ACTION_TOGGLE.equals(action)) {
            if ("playing".equals(state) || "buffering".equals(state)) pause();
            else resume();
        } else if (ACTION_STOP.equals(action)) {
            stopAll();
        } else if (player == null) {
            stopSelf();
        }
        return START_NOT_STICKY;
    }

    private void start() {
        releasePlayer();
        setState("buffering");
        goForeground(); // siempre antes de cualquier salida: startForegroundService lo exige
        if (url == null || url.length() == 0) {
            stopAll();
            return;
        }
        if (!requestFocus()) {
            setState("paused");
            return;
        }
        try {
            player = new MediaPlayer();
            player.setAudioAttributes(new AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build());
            player.setWakeMode(getApplicationContext(), PowerManager.PARTIAL_WAKE_LOCK);
            player.setOnPreparedListener(this);
            player.setOnErrorListener(this);
            player.setOnInfoListener(this);
            player.setDataSource(url);
            player.prepareAsync();
            if (wifiLock != null && !wifiLock.isHeld()) wifiLock.acquire();
            if (!noisyRegistered) {
                IntentFilter f = new IntentFilter(AudioManager.ACTION_AUDIO_BECOMING_NOISY);
                if (Build.VERSION.SDK_INT >= 33) registerReceiver(noisy, f, Context.RECEIVER_NOT_EXPORTED);
                else registerReceiver(noisy, f);
                noisyRegistered = true;
            }
        } catch (Exception e) {
            setState("error");
            stopAll();
        }
    }

    @Override
    public void onPrepared(MediaPlayer mp) {
        mp.start();
        setState("playing");
        notifyUpdate();
    }

    @Override
    public boolean onError(MediaPlayer mp, int what, int extra) {
        setState("error");
        stopAll();
        return true;
    }

    @Override
    public boolean onInfo(MediaPlayer mp, int what, int extra) {
        if (what == MediaPlayer.MEDIA_INFO_BUFFERING_START) setState("buffering");
        else if (what == MediaPlayer.MEDIA_INFO_BUFFERING_END) setState("playing");
        notifyUpdate();
        return false;
    }

    private void pause() {
        // una radio en vivo no se "pausa": se corta el stream y al reanudar se reconecta al presente
        releasePlayer();
        setState("paused");
        notifyUpdate();
    }

    private void resume() {
        if ("playing".equals(state)) return;
        start();
    }

    private void stopAll() {
        releasePlayer();
        abandonFocus();
        if (!"error".equals(state)) setState("stopped");
        state = "stopped";
        if (Build.VERSION.SDK_INT >= 24) stopForeground(STOP_FOREGROUND_REMOVE);
        else stopForeground(true);
        stopSelf();
    }

    private void releasePlayer() {
        if (player != null) {
            try {
                player.reset();
                player.release();
            } catch (Exception ignored) {
            }
            player = null;
        }
        if (wifiLock != null && wifiLock.isHeld()) wifiLock.release();
    }

    private boolean requestFocus() {
        if (Build.VERSION.SDK_INT >= 26) {
            focusRequest = new AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
                    .setAudioAttributes(new AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA)
                            .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build())
                    .setOnAudioFocusChangeListener(this).build();
            return audio.requestAudioFocus(focusRequest) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED;
        }
        return audio.requestAudioFocus(this, AudioManager.STREAM_MUSIC, AudioManager.AUDIOFOCUS_GAIN)
                == AudioManager.AUDIOFOCUS_REQUEST_GRANTED;
    }

    private void abandonFocus() {
        if (Build.VERSION.SDK_INT >= 26 && focusRequest != null) audio.abandonAudioFocusRequest(focusRequest);
        else audio.abandonAudioFocus(this);
    }

    @Override
    public void onAudioFocusChange(int change) {
        if (change == AudioManager.AUDIOFOCUS_LOSS || change == AudioManager.AUDIOFOCUS_LOSS_TRANSIENT) {
            pausedByFocus = change == AudioManager.AUDIOFOCUS_LOSS_TRANSIENT && "playing".equals(state);
            pause();
        } else if (change == AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK) {
            if (player != null) player.setVolume(0.3f, 0.3f);
        } else if (change == AudioManager.AUDIOFOCUS_GAIN) {
            if (player != null) player.setVolume(1f, 1f);
            if (pausedByFocus) {
                pausedByFocus = false;
                resume();
            }
        }
    }

    private void setState(String s) {
        state = s;
        int ps = "playing".equals(s) ? PlaybackState.STATE_PLAYING
                : "buffering".equals(s) ? PlaybackState.STATE_BUFFERING
                : "paused".equals(s) ? PlaybackState.STATE_PAUSED
                : "error".equals(s) ? PlaybackState.STATE_ERROR : PlaybackState.STATE_STOPPED;
        session.setPlaybackState(new PlaybackState.Builder()
                .setActions(PlaybackState.ACTION_PLAY | PlaybackState.ACTION_PAUSE
                        | PlaybackState.ACTION_PLAY_PAUSE | PlaybackState.ACTION_STOP)
                .setState(ps, PlaybackState.PLAYBACK_POSITION_UNKNOWN, 1f).build());
        session.setMetadata(new MediaMetadata.Builder()
                .putString(MediaMetadata.METADATA_KEY_TITLE, stationName == null ? "Radio" : stationName)
                .putString(MediaMetadata.METADATA_KEY_ARTIST, "Pulso Global · En vivo").build());
        Listener l = listener;
        if (l != null) l.onState(s, stationName);
    }

    private Notification buildNotification() {
        boolean playing = "playing".equals(state) || "buffering".equals(state);
        int piFlags = PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE;
        PendingIntent open = PendingIntent.getActivity(this, 0,
                new Intent(this, MainActivity.class).setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP), piFlags);
        PendingIntent toggle = PendingIntent.getService(this, 1,
                new Intent(this, RadioService.class).setAction(ACTION_TOGGLE), piFlags);
        PendingIntent stop = PendingIntent.getService(this, 2,
                new Intent(this, RadioService.class).setAction(ACTION_STOP), piFlags);
        Notification.Builder b = Build.VERSION.SDK_INT >= 26 ? new Notification.Builder(this, CHANNEL) : new Notification.Builder(this);
        b.setSmallIcon(R.drawable.ic_stat_radio)
                .setContentTitle(stationName == null ? "Radio" : stationName)
                .setContentText("buffering".equals(state) ? "Conectando…" : playing ? "En vivo" : "En pausa")
                .setContentIntent(open)
                .setDeleteIntent(stop)
                .setOngoing(playing)
                .setShowWhen(false)
                .setVisibility(Notification.VISIBILITY_PUBLIC)
                .addAction(new Notification.Action.Builder(
                        Icon.createWithResource(this, playing ? R.drawable.ic_pause : R.drawable.ic_play),
                        playing ? "Pausar" : "Escuchar", toggle).build())
                .addAction(new Notification.Action.Builder(
                        Icon.createWithResource(this, R.drawable.ic_stop), "Cerrar", stop).build())
                .setStyle(new Notification.MediaStyle()
                        .setMediaSession(session.getSessionToken())
                        .setShowActionsInCompactView(0, 1));
        return b.build();
    }

    private void goForeground() {
        Notification n = buildNotification();
        if (Build.VERSION.SDK_INT >= 29) startForeground(NOTIF_ID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK);
        else startForeground(NOTIF_ID, n);
    }

    private void notifyUpdate() {
        if ("stopped".equals(state)) return;
        ((NotificationManager) getSystemService(NOTIFICATION_SERVICE)).notify(NOTIF_ID, buildNotification());
    }

    @Override
    public void onDestroy() {
        releasePlayer();
        abandonFocus();
        if (noisyRegistered) {
            unregisterReceiver(noisy);
            noisyRegistered = false;
        }
        session.setActive(false);
        session.release();
        state = "stopped";
        Listener l = listener;
        if (l != null) l.onState("stopped", stationName);
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
