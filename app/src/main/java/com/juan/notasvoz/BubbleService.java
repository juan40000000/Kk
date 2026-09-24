package com.juan.notasvoz;

import android.animation.ValueAnimator;
import android.annotation.SuppressLint;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ServiceInfo;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.speech.RecognitionListener;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;
import android.util.DisplayMetrics;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.view.animation.OvershootInterpolator;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.Toast;

import java.util.ArrayList;

/**
 * Burbuja flotante con un micrófono que queda sobre cualquier app.
 * Tocarla graba una nota de voz y la guarda; arrastrarla hasta la X la cierra.
 */
public class BubbleService extends Service {
    public static final String ACTION_STOP = "com.juan.notasvoz.STOP_BUBBLE";
    private static final String CHANNEL = "burbuja";
    private static final int NOTIF_ID = 7;
    private static final String PREF_X = "bubble_x";
    private static final String PREF_Y = "bubble_y";

    public static boolean running;

    private final Handler main = new Handler(Looper.getMainLooper());
    private WindowManager wm;
    private SharedPreferences prefs;
    private NoteStore store;

    private FrameLayout bubble;
    private ImageView icon;
    private GradientDrawable circle;
    private WindowManager.LayoutParams params;
    private FrameLayout trash;
    private GradientDrawable trashCircle;
    private int size;

    private SpeechRecognizer recognizer;
    private boolean listening;
    private ValueAnimator pulse;

    public static void start(Context c) {
        c.startForegroundService(new Intent(c, BubbleService.class));
    }

    public static void stop(Context c) {
        c.stopService(new Intent(c, BubbleService.class));
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onCreate() {
        super.onCreate();
        wm = getSystemService(WindowManager.class);
        prefs = getSharedPreferences("notas", MODE_PRIVATE);
        store = new NoteStore(this);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null && ACTION_STOP.equals(intent.getAction())) {
            prefs.edit().putBoolean(MainActivity.PREF_BUBBLE, false).apply();
            stopSelf();
            return START_NOT_STICKY;
        }
        goForeground();
        if (bubble == null) showBubble();
        running = true;
        return START_NOT_STICKY;
    }

    @Override
    public void onDestroy() {
        running = false;
        stopListening();
        if (recognizer != null) recognizer.destroy();
        if (bubble != null) wm.removeView(bubble);
        hideTrash();
        super.onDestroy();
    }

    private void goForeground() {
        NotificationManager nm = getSystemService(NotificationManager.class);
        NotificationChannel ch = new NotificationChannel(CHANNEL, "Burbuja de notas",
                NotificationManager.IMPORTANCE_MIN);
        ch.setShowBadge(false);
        nm.createNotificationChannel(ch);

        PendingIntent open = PendingIntent.getActivity(this, 0,
                new Intent(this, MainActivity.class), PendingIntent.FLAG_IMMUTABLE);
        PendingIntent stop = PendingIntent.getService(this, 1,
                new Intent(this, BubbleService.class).setAction(ACTION_STOP),
                PendingIntent.FLAG_IMMUTABLE);

        Notification n = new Notification.Builder(this, CHANNEL)
                .setSmallIcon(R.drawable.ic_mic)
                .setContentTitle("Burbuja de notas activa")
                .setContentText("Toca el micrófono flotante para dictar una nota")
                .setColor(store.accent())
                .setContentIntent(open)
                .addAction(new Notification.Action.Builder(null, "Ocultar burbuja", stop).build())
                .setOngoing(true)
                .build();

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIF_ID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE);
        } else {
            startForeground(NOTIF_ID, n);
        }
    }

    private int dp(float v) {
        return Metro.dp(this, v);
    }

    @SuppressLint("ClickableViewAccessibility")
    private void showBubble() {
        size = dp(60);
        bubble = new FrameLayout(this);
        circle = new GradientDrawable();
        circle.setShape(GradientDrawable.OVAL);
        circle.setColor(store.accent());
        circle.setStroke(dp(2), Color.WHITE);
        bubble.setBackground(circle);
        bubble.setElevation(dp(6));

        icon = new ImageView(this);
        icon.setImageResource(R.drawable.ic_mic);
        int pad = dp(15);
        icon.setPadding(pad, pad, pad, pad);
        bubble.addView(icon, new FrameLayout.LayoutParams(size, size));

        DisplayMetrics dm = getResources().getDisplayMetrics();
        params = new WindowManager.LayoutParams(size, size,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                        | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                PixelFormat.TRANSLUCENT);
        params.gravity = Gravity.TOP | Gravity.START;
        params.x = prefs.getInt(PREF_X, dm.widthPixels - size - dp(8));
        params.y = prefs.getInt(PREF_Y, dm.heightPixels / 3);
        clampToScreen();

        bubble.setOnTouchListener(new View.OnTouchListener() {
            private float downX, downY;
            private int startX, startY;
            private boolean dragging;

            @Override
            public boolean onTouch(View v, MotionEvent e) {
                switch (e.getActionMasked()) {
                    case MotionEvent.ACTION_DOWN:
                        downX = e.getRawX();
                        downY = e.getRawY();
                        startX = params.x;
                        startY = params.y;
                        dragging = false;
                        bubble.animate().scaleX(0.9f).scaleY(0.9f).setDuration(80).start();
                        return true;
                    case MotionEvent.ACTION_MOVE:
                        float dx = e.getRawX() - downX;
                        float dy = e.getRawY() - downY;
                        if (!dragging && Math.hypot(dx, dy) > dp(8)) {
                            dragging = true;
                            showTrash();
                        }
                        if (dragging) {
                            params.x = startX + (int) dx;
                            params.y = startY + (int) dy;
                            wm.updateViewLayout(bubble, params);
                            highlightTrash(overTrash(e));
                        }
                        return true;
                    case MotionEvent.ACTION_UP:
                    case MotionEvent.ACTION_CANCEL:
                        bubble.animate().scaleX(1f).scaleY(1f).setDuration(120).start();
                        if (dragging) {
                            boolean close = overTrash(e);
                            hideTrash();
                            if (close) {
                                prefs.edit().putBoolean(MainActivity.PREF_BUBBLE, false).apply();
                                stopSelf();
                            } else {
                                snapToEdge();
                            }
                        } else if (e.getActionMasked() == MotionEvent.ACTION_UP) {
                            toggleListening();
                        }
                        return true;
                }
                return false;
            }
        });

        wm.addView(bubble, params);
        bubble.setScaleX(0f);
        bubble.setScaleY(0f);
        bubble.animate().scaleX(1f).scaleY(1f).setDuration(300)
                .setInterpolator(new OvershootInterpolator()).start();
    }

    private void clampToScreen() {
        DisplayMetrics dm = getResources().getDisplayMetrics();
        params.x = Math.max(0, Math.min(params.x, dm.widthPixels - size));
        params.y = Math.max(dp(24), Math.min(params.y, dm.heightPixels - size - dp(24)));
    }

    /** Al soltarla, la burbuja se pega al borde más cercano. */
    private void snapToEdge() {
        DisplayMetrics dm = getResources().getDisplayMetrics();
        clampToScreen();
        int from = params.x;
        int to = from + size / 2 < dm.widthPixels / 2 ? dp(8) : dm.widthPixels - size - dp(8);
        ValueAnimator a = ValueAnimator.ofInt(from, to);
        a.setDuration(220);
        a.addUpdateListener(an -> {
            if (bubble == null || !bubble.isAttachedToWindow()) return;
            params.x = (int) an.getAnimatedValue();
            wm.updateViewLayout(bubble, params);
        });
        a.start();
        prefs.edit().putInt(PREF_X, to).putInt(PREF_Y, params.y).apply();
    }

    private void showTrash() {
        if (trash != null) return;
        int t = dp(64);
        trash = new FrameLayout(this);
        trashCircle = new GradientDrawable();
        trashCircle.setShape(GradientDrawable.OVAL);
        trashCircle.setColor(0xCC000000);
        trashCircle.setStroke(dp(2), Color.WHITE);
        trash.setBackground(trashCircle);
        ImageView x = new ImageView(this);
        x.setImageResource(R.drawable.ic_close);
        int pad = dp(18);
        x.setPadding(pad, pad, pad, pad);
        trash.addView(x, new FrameLayout.LayoutParams(t, t));

        WindowManager.LayoutParams lp = new WindowManager.LayoutParams(t, t,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                        | WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE,
                PixelFormat.TRANSLUCENT);
        lp.gravity = Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL;
        lp.y = dp(48);
        wm.addView(trash, lp);
        trash.setAlpha(0f);
        trash.animate().alpha(1f).setDuration(150).start();
    }

    private void hideTrash() {
        if (trash == null) return;
        wm.removeView(trash);
        trash = null;
    }

    private boolean overTrash(MotionEvent e) {
        DisplayMetrics dm = getResources().getDisplayMetrics();
        float cx = dm.widthPixels / 2f;
        float cy = dm.heightPixels - dp(48) - dp(32);
        return Math.hypot(e.getRawX() - cx, e.getRawY() - cy) < dp(70);
    }

    private void highlightTrash(boolean over) {
        if (trash == null) return;
        trashCircle.setColor(over ? Metro.RED : 0xCC000000);
        float s = over ? 1.2f : 1f;
        trash.animate().scaleX(s).scaleY(s).setDuration(100).start();
    }

    // ---- Dictado ----

    private void toggleListening() {
        if (listening) {
            recognizer.stopListening();
            return;
        }
        if (!SpeechRecognizer.isRecognitionAvailable(this)) {
            Toast.makeText(this, "No hay reconocimiento de voz (instala la app de Google)",
                    Toast.LENGTH_LONG).show();
            return;
        }
        if (recognizer == null) {
            recognizer = SpeechRecognizer.createSpeechRecognizer(this);
            recognizer.setRecognitionListener(listener);
        }
        Intent i = Metro.speechIntent("Dicta tu nota");
        i.putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, getPackageName());
        listening = true;
        setListeningLook(true);
        recognizer.startListening(i);
    }

    private void stopListening() {
        if (listening && recognizer != null) recognizer.cancel();
        listening = false;
        setListeningLook(false);
    }

    /** Mientras escucha, la burbuja se pone roja y late con la voz. */
    private void setListeningLook(boolean on) {
        if (bubble == null) return;
        if (pulse != null) pulse.cancel();
        if (on) {
            circle.setColor(Metro.RED);
            pulse = ValueAnimator.ofFloat(1f, 1.12f);
            pulse.setDuration(500);
            pulse.setRepeatMode(ValueAnimator.REVERSE);
            pulse.setRepeatCount(ValueAnimator.INFINITE);
            pulse.addUpdateListener(a -> {
                float s = (float) a.getAnimatedValue();
                icon.setScaleX(s);
                icon.setScaleY(s);
            });
            pulse.start();
        } else {
            circle.setColor(store.accent());
            icon.setScaleX(1f);
            icon.setScaleY(1f);
            icon.setImageResource(R.drawable.ic_mic);
        }
    }

    private void flashSaved() {
        circle.setColor(Metro.GREEN);
        icon.setScaleX(1f);
        icon.setScaleY(1f);
        icon.setImageResource(R.drawable.ic_check);
        main.postDelayed(() -> {
            if (bubble != null && !listening) setListeningLook(false);
        }, 1200);
    }

    private final RecognitionListener listener = new RecognitionListener() {
        @Override public void onReadyForSpeech(Bundle params) { }
        @Override public void onBeginningOfSpeech() { }
        @Override public void onBufferReceived(byte[] buffer) { }
        @Override public void onEndOfSpeech() { }
        @Override public void onPartialResults(Bundle partialResults) { }
        @Override public void onEvent(int eventType, Bundle params) { }

        @Override
        public void onRmsChanged(float rmsdB) {
            float s = 1f + Math.max(0f, Math.min(rmsdB, 10f)) / 40f;
            bubble.setScaleX(s);
            bubble.setScaleY(s);
        }

        @Override
        public void onResults(Bundle results) {
            listening = false;
            if (pulse != null) pulse.cancel();
            bubble.setScaleX(1f);
            bubble.setScaleY(1f);
            ArrayList<String> r = results.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
            String text = r == null || r.isEmpty() ? "" : r.get(0).trim();
            if (text.isEmpty()) {
                setListeningLook(false);
                Toast.makeText(BubbleService.this, "no te entendí, prueba otra vez",
                        Toast.LENGTH_SHORT).show();
                return;
            }
            store.createFromSpeech(text);
            flashSaved();
            Toast.makeText(BubbleService.this, "nota guardada: " + text, Toast.LENGTH_SHORT).show();
        }

        @Override
        public void onError(int error) {
            listening = false;
            bubble.setScaleX(1f);
            bubble.setScaleY(1f);
            setListeningLook(false);
            String msg;
            switch (error) {
                case SpeechRecognizer.ERROR_NO_MATCH:
                case SpeechRecognizer.ERROR_SPEECH_TIMEOUT:
                    msg = "no te escuché, toca la burbuja y habla";
                    break;
                case SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS:
                    msg = "falta el permiso de micrófono: abre Notas y activa la burbuja otra vez";
                    break;
                case SpeechRecognizer.ERROR_NETWORK:
                case SpeechRecognizer.ERROR_NETWORK_TIMEOUT:
                    msg = "sin conexión para reconocer la voz";
                    break;
                case SpeechRecognizer.ERROR_RECOGNIZER_BUSY:
                    msg = "el reconocimiento de voz está ocupado, prueba de nuevo";
                    break;
                default:
                    msg = "no se pudo grabar (error " + error + ")";
            }
            Toast.makeText(BubbleService.this, msg, Toast.LENGTH_SHORT).show();
        }
    };
}
