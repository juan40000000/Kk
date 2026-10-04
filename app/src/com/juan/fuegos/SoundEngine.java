package com.juan.fuegos;

import android.content.Context;
import android.media.AudioAttributes;
import android.media.AudioManager;
import android.media.SoundPool;
import android.os.Build;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.Random;

/**
 * Sintetiza de forma procedural todos los sonidos (mortero, silbido, estruendos,
 * crepitado, salva) y los reproduce con SoundPool. No necesita archivos de audio.
 */
public class SoundEngine {

    public static final int LAUNCH = 0;
    public static final int WHISTLE = 1;
    public static final int BOOM_SMALL = 2;
    public static final int BOOM_MED = 3;
    public static final int BOOM_BIG = 4;
    public static final int CRACKLE = 5;
    public static final int SALUTE = 6;
    public static final int FIZZ = 7;
    private static final int COUNT = 8;

    private static final int SR = 22050;
    private static final int VERSION = 3;

    private SoundPool pool;
    private final int[] ids = new int[COUNT];
    private final boolean[] loaded = new boolean[COUNT];
    private volatile boolean enabled = true;
    private volatile float master = 1f;

    public void init(final Context ctx) {
        if (Build.VERSION.SDK_INT >= 21) {
            AudioAttributes aa = new AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_GAME)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build();
            pool = new SoundPool.Builder().setMaxStreams(24).setAudioAttributes(aa).build();
        } else {
            pool = new SoundPool(24, AudioManager.STREAM_MUSIC, 0);
        }
        pool.setOnLoadCompleteListener(new SoundPool.OnLoadCompleteListener() {
            @Override
            public void onLoadComplete(SoundPool soundPool, int sampleId, int status) {
                for (int i = 0; i < COUNT; i++) {
                    if (ids[i] == sampleId && status == 0) loaded[i] = true;
                }
            }
        });
        final File dir = new File(ctx.getCacheDir(), "sfx" + VERSION);
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    if (!dir.exists()) dir.mkdirs();
                    for (int i = 0; i < COUNT; i++) {
                        File f = new File(dir, "s" + i + ".wav");
                        if (!f.exists() || f.length() < 100) {
                            float[] data = synth(i);
                            File tmp = new File(dir, "s" + i + ".tmp");
                            writeWav(tmp, data);
                            tmp.renameTo(f);
                        }
                        SoundPool p = pool;
                        if (p == null) return;
                        ids[i] = p.load(f.getAbsolutePath(), 1);
                    }
                } catch (Throwable t) {
                    // Sin sonido si algo falla; el juego sigue funcionando.
                }
            }
        }, "sfx-synth").start();
    }

    public void setEnabled(boolean e) {
        enabled = e;
        if (!e && pool != null) pool.autoPause();
        if (e && pool != null) pool.autoResume();
    }

    public boolean isEnabled() {
        return enabled;
    }

    /** pan: -1 izquierda .. 1 derecha. */
    public void play(int which, float vol, float pan, float rate) {
        SoundPool p = pool;
        if (!enabled || p == null || !loaded[which]) return;
        vol *= master;
        if (vol <= 0.01f) return;
        if (vol > 1f) vol = 1f;
        if (pan < -1f) pan = -1f;
        if (pan > 1f) pan = 1f;
        float l = vol * Math.min(1f, 1f - pan * 0.8f);
        float r = vol * Math.min(1f, 1f + pan * 0.8f);
        if (rate < 0.5f) rate = 0.5f;
        if (rate > 2f) rate = 2f;
        p.play(ids[which], l, r, 1, 0, rate);
    }

    public void pause() {
        if (pool != null) pool.autoPause();
    }

    public void resume() {
        if (pool != null && enabled) pool.autoResume();
    }

    public void release() {
        SoundPool p = pool;
        pool = null;
        if (p != null) p.release();
    }

    // ------------------------------------------------------------------ síntesis

    private static float[] synth(int which) {
        Random rnd = new Random(1234 + which * 77L);
        switch (which) {
            case LAUNCH: return launch(rnd);
            case WHISTLE: return whistle(rnd);
            case BOOM_SMALL: return boom(rnd, 0.55f, 2.0f);
            case BOOM_MED: return boom(rnd, 1.0f, 3.2f);
            case BOOM_BIG: return boom(rnd, 1.6f, 4.5f);
            case CRACKLE: return crackle(rnd);
            case SALUTE: return salute(rnd);
            case FIZZ: return fizz(rnd);
        }
        return new float[SR / 10];
    }

    private static float lpCoef(float fc) {
        return (float) (1.0 - Math.exp(-2.0 * Math.PI * fc / SR));
    }

    private static float[] boom(Random rnd, float size, float seconds) {
        int n = (int) (seconds * SR);
        float[] o = new float[n];
        float lp1 = 0, lp2 = 0, lp3 = 0, lp4 = 0;
        float a1 = lpCoef(900), a2 = lpCoef(160), a3 = lpCoef(70);
        float rumbleT = 0.55f * size + 0.25f;
        for (int i = 0; i < n; i++) {
            float t = (float) i / SR;
            float w = rnd.nextFloat() * 2f - 1f;
            lp1 += a1 * (w - lp1);
            lp2 += a2 * (w - lp2);
            lp3 += a2 * (lp2 - lp3);
            lp4 += a3 * (lp3 - lp4);
            float crack = w * (float) Math.exp(-t / 0.010f) * 0.9f;
            float body = lp1 * (float) Math.exp(-t / (0.12f + 0.08f * size)) * 2.2f;
            float rumble = lp3 * (float) Math.exp(-t / rumbleT) * 7.0f * size;
            float sub = lp4 * (float) Math.exp(-t / (rumbleT * 1.4f)) * 14f * size;
            float thump = (float) Math.sin(2 * Math.PI * (48 + 30 * Math.exp(-t * 18)) * t)
                    * (float) Math.exp(-t / 0.16f) * 0.9f * size;
            float attack = Math.min(1f, t / 0.0015f);
            o[i] = attack * (crack + body + rumble + sub + thump);
        }
        // Ecos de edificios / colinas (filtrados).
        addEcho(o, 0.23f, 0.33f, lpCoef(1400));
        addEcho(o, 0.61f, 0.22f, lpCoef(800));
        addEcho(o, 1.07f, 0.13f, lpCoef(500));
        fadeOut(o, 0.4f);
        normalize(o, 0.95f);
        return o;
    }

    private static float[] salute(Random rnd) {
        int n = (int) (4.0f * SR);
        float[] o = new float[n];
        float lp1 = 0, lp2 = 0, lp3 = 0;
        float a1 = lpCoef(2500), a2 = lpCoef(140);
        for (int i = 0; i < n; i++) {
            float t = (float) i / SR;
            float w = rnd.nextFloat() * 2f - 1f;
            lp1 += a1 * (w - lp1);
            lp2 += a2 * (w - lp2);
            lp3 += a2 * (lp2 - lp3);
            float crack = w * (float) Math.exp(-t / 0.006f) * 1.4f;
            float body = lp1 * (float) Math.exp(-t / 0.07f) * 1.6f;
            float rumble = lp3 * (float) Math.exp(-t / 0.9f) * 12f;
            o[i] = crack + body + rumble;
        }
        addEcho(o, 0.19f, 0.45f, lpCoef(2000));
        addEcho(o, 0.47f, 0.30f, lpCoef(1200));
        addEcho(o, 0.92f, 0.20f, lpCoef(700));
        addEcho(o, 1.55f, 0.12f, lpCoef(400));
        fadeOut(o, 0.5f);
        normalize(o, 0.98f);
        return o;
    }

    private static float[] launch(Random rnd) {
        int n = (int) (1.3f * SR);
        float[] o = new float[n];
        float lp = 0, hp = 0, prev = 0;
        float a = lpCoef(380);
        double ph = 0;
        for (int i = 0; i < n; i++) {
            float t = (float) i / SR;
            float w = rnd.nextFloat() * 2f - 1f;
            lp += a * (w - lp);
            hp = 0.97f * (hp + w - prev);
            prev = w;
            double f = 45 + 110 * Math.exp(-t * 30);
            ph += 2 * Math.PI * f / SR;
            float thump = (float) Math.sin(ph) * (float) Math.exp(-t / 0.07f) * 1.2f;
            float puff = lp * (float) Math.exp(-t / 0.05f) * 3.5f;
            float whooshEnv = Math.min(1f, t / 0.06f) * (float) Math.exp(-t / 0.4f);
            float whoosh = hp * whooshEnv * 0.18f;
            o[i] = (thump + puff + whoosh) * Math.min(1f, t / 0.002f);
        }
        fadeOut(o, 0.2f);
        normalize(o, 0.9f);
        return o;
    }

    private static float[] whistle(Random rnd) {
        float len = 1.7f;
        int n = (int) (len * SR);
        float[] o = new float[n];
        double ph = 0;
        float hp = 0, prev = 0;
        for (int i = 0; i < n; i++) {
            float t = (float) i / SR;
            float k = t / len;
            double f = 700 + 1900 * k + 25 * Math.sin(2 * Math.PI * 9 * t);
            ph += 2 * Math.PI * f / SR;
            float w = rnd.nextFloat() * 2f - 1f;
            hp = 0.9f * (hp + w - prev);
            prev = w;
            float env = Math.min(1f, t / 0.08f) * Math.min(1f, (len - t) / 0.25f);
            float tone = (float) (Math.sin(ph) + 0.25 * Math.sin(2 * ph) + 0.08 * Math.sin(3 * ph));
            o[i] = env * (tone * 0.8f + hp * 0.12f);
        }
        normalize(o, 0.6f);
        return o;
    }

    private static float[] crackle(Random rnd) {
        float len = 2.4f;
        int n = (int) (len * SR);
        float[] o = new float[n];
        int pops = 260;
        for (int p = 0; p < pops; p++) {
            float t0 = 0.02f + 1.9f * (float) Math.pow(rnd.nextFloat(), 1.3);
            int start = (int) (t0 * SR);
            float amp = 0.25f + rnd.nextFloat() * 0.75f;
            float dec = 0.0008f + rnd.nextFloat() * 0.0025f;
            int dur = (int) (dec * 6 * SR);
            float prev = 0;
            for (int j = 0; j < dur && start + j < n; j++) {
                float t = (float) j / SR;
                float w = rnd.nextFloat() * 2f - 1f;
                float h = w - prev; // paso alto: chasquido seco
                prev = w;
                o[start + j] += h * amp * (float) Math.exp(-t / dec);
            }
        }
        addEcho(o, 0.21f, 0.2f, lpCoef(1800));
        fadeOut(o, 0.3f);
        normalize(o, 0.85f);
        return o;
    }

    private static float[] fizz(Random rnd) {
        float len = 2.5f;
        int n = (int) (len * SR);
        float[] o = new float[n];
        float hp = 0, prev = 0, lp = 0;
        float a = lpCoef(5000);
        for (int i = 0; i < n; i++) {
            float t = (float) i / SR;
            float w = rnd.nextFloat() * 2f - 1f;
            hp = 0.95f * (hp + w - prev);
            prev = w;
            lp += a * (hp - lp);
            float flutter = 0.6f + 0.4f * rnd.nextFloat();
            float env = Math.min(1f, t / 0.1f) * Math.min(1f, (len - t) / 1.2f);
            o[i] = lp * env * flutter;
        }
        normalize(o, 0.5f);
        return o;
    }

    private static void addEcho(float[] o, float delaySec, float gain, float lpA) {
        int d = (int) (delaySec * SR);
        float lp = 0;
        float[] src = o.clone();
        for (int i = d; i < o.length; i++) {
            lp += lpA * (src[i - d] - lp);
            o[i] += lp * gain;
        }
    }

    private static void fadeOut(float[] o, float sec) {
        int f = Math.min(o.length, (int) (sec * SR));
        for (int i = 0; i < f; i++) {
            o[o.length - 1 - i] *= (float) i / f;
        }
    }

    private static void normalize(float[] o, float peak) {
        float m = 1e-6f;
        for (float v : o) m = Math.max(m, Math.abs(v));
        float g = peak / m;
        for (int i = 0; i < o.length; i++) o[i] *= g;
    }

    private static void writeWav(File f, float[] data) throws IOException {
        OutputStream os = new BufferedOutputStream(new FileOutputStream(f), 65536);
        try {
            int dataLen = data.length * 2;
            writeStr(os, "RIFF");
            writeInt(os, 36 + dataLen);
            writeStr(os, "WAVE");
            writeStr(os, "fmt ");
            writeInt(os, 16);
            writeShort(os, 1);       // PCM
            writeShort(os, 1);       // mono
            writeInt(os, SR);
            writeInt(os, SR * 2);
            writeShort(os, 2);
            writeShort(os, 16);
            writeStr(os, "data");
            writeInt(os, dataLen);
            for (float v : data) {
                int s = (int) (Math.max(-1f, Math.min(1f, v)) * 32767);
                os.write(s & 0xff);
                os.write((s >> 8) & 0xff);
            }
        } finally {
            os.close();
        }
    }

    private static void writeStr(OutputStream os, String s) throws IOException {
        for (int i = 0; i < s.length(); i++) os.write(s.charAt(i));
    }

    private static void writeInt(OutputStream os, int v) throws IOException {
        os.write(v & 0xff);
        os.write((v >> 8) & 0xff);
        os.write((v >> 16) & 0xff);
        os.write((v >> 24) & 0xff);
    }

    private static void writeShort(OutputStream os, int v) throws IOException {
        os.write(v & 0xff);
        os.write((v >> 8) & 0xff);
    }
}
