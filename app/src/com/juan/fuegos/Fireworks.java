package com.juan.fuegos;

import java.util.Random;

/**
 * Simulación física de los fuegos artificiales. Coordenadas en píxeles de pantalla
 * (y hacia abajo). Todo se ejecuta en el hilo de render de OpenGL.
 */
public class Fireworks {

    // ------------------------------------------------------------ tipos de carcasa
    public static final int K_RANDOM = -1;
    public static final int K_PEONY = 0;
    public static final int K_CHRYS = 1;
    public static final int K_WILLOW = 2;
    public static final int K_PALM = 3;
    public static final int K_RING = 4;
    public static final int K_CROSSETTE = 5;
    public static final int K_STROBE = 6;
    public static final int K_CRACKLE = 7;
    public static final int K_KAMURO = 8;
    public static final int K_PISTIL = 9;
    public static final int K_SALUTE = 10;
    public static final int K_HEART = 11;
    public static final int KIND_COUNT = 12;

    public static final String[] KIND_NAMES = {
            "Peonía", "Crisantemo", "Sauce", "Palmera", "Anillo", "Crossette",
            "Estrobo", "Crepitante", "Kamuro", "Doble pistilo", "Trueno", "Corazón"
    };

    // ------------------------------------------------------------ colores reales
    // Estroncio rojo, bario verde, cobre azul, sodio dorado, magnesio blanco, etc.
    static final float[][] PALETTE = {
            {1.00f, 0.12f, 0.08f},   // 0 rojo
            {0.25f, 1.00f, 0.30f},   // 1 verde
            {0.22f, 0.42f, 1.00f},   // 2 azul
            {1.00f, 0.68f, 0.22f},   // 3 dorado
            {1.00f, 0.97f, 0.92f},   // 4 blanco
            {0.72f, 0.28f, 1.00f},   // 5 violeta
            {1.00f, 0.42f, 0.08f},   // 6 naranja
            {0.20f, 0.90f, 0.95f},   // 7 turquesa
            {1.00f, 0.35f, 0.65f},   // 8 rosa
            {0.85f, 0.90f, 1.00f},   // 9 plata
            {0.75f, 1.00f, 0.20f},   // 10 lima
    };
    static final int C_RED = 0, C_GREEN = 1, C_BLUE = 2, C_GOLD = 3, C_WHITE = 4, C_PURPLE = 5,
            C_ORANGE = 6, C_TEAL = 7, C_PINK = 8, C_SILVER = 9, C_LIME = 10;

    // ------------------------------------------------------------ partículas
    static final int T_SHELL = 0, T_STAR = 1, T_SPARK = 2, T_CRACK = 3, T_FLASH = 4;
    static final int F_STROBE = 1, F_SPLIT = 2, F_CRACKLE_END = 4, F_COLORCHANGE = 8,
            F_FLICKER = 16, F_WHISTLE = 32;
    static final int E_NONE = 0, E_GOLD = 1, E_SILVER = 2, E_OWN = 3, E_GOLD_HEAVY = 4;

    public static final int MAX = 36000;
    final float[] x = new float[MAX], y = new float[MAX], px = new float[MAX], py = new float[MAX];
    final float[] vx = new float[MAX], vy = new float[MAX];
    final float[] life = new float[MAX], maxLife = new float[MAX], age = new float[MAX];
    final float[] r = new float[MAX], g = new float[MAX], b = new float[MAX];
    final float[] r2 = new float[MAX], g2 = new float[MAX], b2 = new float[MAX];
    final float[] size = new float[MAX], drag = new float[MAX], grav = new float[MAX];
    final float[] emitRate = new float[MAX], emitAcc = new float[MAX], timer = new float[MAX];
    final float[] phase = new float[MAX], bright = new float[MAX];
    final int[] type = new int[MAX], flags = new int[MAX], emit = new int[MAX];
    final int[] shKind = new int[MAX], shC1 = new int[MAX], shC2 = new int[MAX];
    final float[] shScale = new float[MAX];
    final boolean[] shUser = new boolean[MAX];
    int count = 0;

    // ------------------------------------------------------------ humo
    public static final int SMAX = 1800;
    final float[] sx = new float[SMAX], sy = new float[SMAX], svx = new float[SMAX], svy = new float[SMAX];
    final float[] sLife = new float[SMAX], sMax = new float[SMAX], sSize = new float[SMAX],
            sGrow = new float[SMAX], sAlpha = new float[SMAX], sAng = new float[SMAX], sSpin = new float[SMAX];
    int sCount = 0;

    // ------------------------------------------------------------ luces (iluminan cielo y humo)
    public static final int LMAX = 16;
    final float[] lx = new float[LMAX], ly = new float[LMAX], li = new float[LMAX],
            lr = new float[LMAX], lg = new float[LMAX], lb = new float[LMAX], ltau = new float[LMAX];
    int lCount = 0;
    private final boolean[] lightUsed = new boolean[LMAX];

    // ------------------------------------------------------------ sonidos programados
    static final int QMAX = 96;
    final float[] qTime = new float[QMAX], qVol = new float[QMAX], qPan = new float[QMAX], qRate = new float[QMAX];
    final int[] qWhich = new int[QMAX];
    int qCount = 0;

    // ------------------------------------------------------------ lanzamientos programados
    static final int LQMAX = 400;
    final float[] lqTime = new float[LQMAX], lqX = new float[LQMAX], lqY = new float[LQMAX], lqScale = new float[LQMAX];
    final int[] lqKind = new int[LQMAX];
    int lqCount = 0;

    // ------------------------------------------------------------ estado
    final Random rnd = new Random();
    float W = 1, H = 1, S = 1, dp = 2;
    float ground = 1;     // línea del horizonte (base de los edificios / orilla)
    float time = 0;
    float wind;
    final SoundEngine sound;
    final Haptics haptics;

    // Juego
    public interface Listener {
        void onStats(int score, int combo, float meter, int best);
    }

    Listener listener;
    int score = 0, best = 0, combo = 0;
    float lastBurst = -10f, meter = 0f;
    boolean autoMode = false;
    float autoNext = 0f;
    float statsTimer = 0f;
    boolean statsDirty = true;

    // Toques mantenidos (ráfaga)
    static final int PMAX = 10;
    final boolean[] pDown = new boolean[PMAX];
    final float[] pX = new float[PMAX], pY = new float[PMAX], pNext = new float[PMAX];
    int selectedKind = K_RANDOM;

    public interface Haptics {
        void buzz(int ms, int amplitude);
    }

    public Fireworks(SoundEngine sound, Haptics haptics) {
        this.sound = sound;
        this.haptics = haptics;
        wind = (rnd.nextBoolean() ? 1 : -1) * (0.008f + rnd.nextFloat() * 0.012f);
    }

    public void resize(float w, float h, float density) {
        float oldW = W, oldH = H;
        W = w;
        H = h;
        S = Math.min(w, h);
        dp = density;
        ground = waterlineFraction(w, h) * h;
        if (oldW > 1 && (oldW != w || oldH != h)) {
            // Reubica lo existente para que nada quede fuera al girar la pantalla.
            float sxk = w / oldW, syk = h / oldH;
            for (int i = 0; i < count; i++) {
                x[i] *= sxk; px[i] *= sxk; y[i] *= syk; py[i] *= syk;
            }
            for (int i = 0; i < sCount; i++) {
                sx[i] *= sxk; sy[i] *= syk;
            }
            for (int i = 0; i < lCount; i++) {
                lx[i] *= sxk; ly[i] *= syk;
            }
        }
    }

    public static float waterlineFraction(float w, float h) {
        return w > h ? 0.80f : 0.84f;
    }

    // ================================================================= entrada

    public void touchDown(int id, float tx, float ty) {
        launchUser(tx, ty);
        if (id >= 0 && id < PMAX) {
            pDown[id] = true;
            pX[id] = tx;
            pY[id] = ty;
            pNext[id] = time + 0.45f;
        }
    }

    public void touchMove(int id, float tx, float ty) {
        if (id >= 0 && id < PMAX && pDown[id]) {
            pX[id] = tx;
            pY[id] = ty;
        }
    }

    public void touchUp(int id) {
        if (id >= 0 && id < PMAX) pDown[id] = false;
    }

    public void touchCancelAll() {
        for (int i = 0; i < PMAX; i++) pDown[i] = false;
    }

    void launchUser(float tx, float ty) {
        int k = selectedKind == K_RANDOM ? randomKind(false) : selectedKind;
        launch(tx, ty, k, 0.85f + rnd.nextFloat() * 0.35f, true);
    }

    public void setSelectedKind(int k) {
        selectedKind = k;
    }

    public void setAuto(boolean on) {
        autoMode = on;
        autoNext = time + 0.3f;
    }

    public void setBest(int b) {
        best = b;
        statsDirty = true;
    }

    public boolean finaleReady() {
        return meter >= 1f;
    }

    /** Gran final: decenas de carcasas en crescendo. */
    public void startFinale(boolean free) {
        if (!free && meter < 1f) return;
        if (!free) {
            meter = 0f;
            statsDirty = true;
        }
        float t0 = time;
        float dur = 9f;
        int n = 55;
        for (int i = 0; i < n && lqCount < LQMAX - 20; i++) {
            float u = (float) i / n;
            // densidad creciente: más lanzamientos hacia el final
            float t = t0 + dur * (float) Math.pow(u, 0.75);
            int kind = randomKind(true);
            if (u > 0.8f && rnd.nextFloat() < 0.35f) kind = rnd.nextBoolean() ? K_KAMURO : K_WILLOW;
            queueLaunch(t, 0.08f + rnd.nextFloat() * 0.84f, 0.12f + rnd.nextFloat() * 0.38f, kind,
                    0.9f + rnd.nextFloat() * 0.45f);
        }
        // Remate: abanico de sauces dorados + truenos
        float te = t0 + dur + 0.4f;
        for (int i = 0; i < 7; i++) {
            queueLaunch(te + i * 0.06f, 0.15f + i * 0.7f / 6f, 0.18f + Math.abs(i - 3) * 0.03f, K_KAMURO, 1.35f);
        }
        for (int i = 0; i < 6; i++) {
            queueLaunch(te + 1.2f + i * 0.18f, 0.2f + rnd.nextFloat() * 0.6f, 0.2f + rnd.nextFloat() * 0.25f,
                    K_SALUTE, 1f);
        }
    }

    void queueLaunch(float t, float fx, float fy, int kind, float scale) {
        if (lqCount >= LQMAX) return;
        lqTime[lqCount] = t;
        lqX[lqCount] = fx;
        lqY[lqCount] = fy;
        lqKind[lqCount] = kind;
        lqScale[lqCount] = scale;
        lqCount++;
    }

    int randomKind(boolean includeSalute) {
        int n = includeSalute ? 11 : 10;
        int k = rnd.nextInt(n);
        if (rnd.nextFloat() < 0.05f) k = K_HEART;
        return k;
    }

    // ================================================================= lanzamiento

    public void launch(float tx, float ty, int kind, float scale, boolean user) {
        float minY = 0.06f * H;
        float maxY = ground - 0.22f * S;
        if (ty < minY) ty = minY;
        if (ty > maxY) ty = maxY;
        float x0 = tx + (rnd.nextFloat() - 0.5f) * 0.10f * S;
        x0 = Math.max(0.02f * W, Math.min(0.98f * W, x0));
        float y0 = ground + 0.01f * S;
        float gShell = 0.55f * S;
        float h = y0 - ty;
        float T = (float) Math.sqrt(2 * h / gShell);
        int i = add();
        if (i < 0) return;
        type[i] = T_SHELL;
        x[i] = px[i] = x0;
        y[i] = py[i] = y0;
        vx[i] = (tx - x0) / T;
        vy[i] = -gShell * T;
        grav[i] = gShell;
        drag[i] = 0f;
        life[i] = maxLife[i] = T;
        size[i] = 3.6f * dp;
        float[] c = PALETTE[C_GOLD];
        r[i] = c[0]; g[i] = c[1] * 0.85f; b[i] = c[2] * 0.7f;
        emit[i] = kind == K_PALM ? E_GOLD_HEAVY : (rnd.nextFloat() < 0.3f ? E_SILVER : E_GOLD);
        emitRate[i] = kind == K_PALM ? 260f : 90f;
        flags[i] = 0;
        boolean whistle = kind != K_PALM && rnd.nextFloat() < 0.28f;
        if (whistle) {
            flags[i] |= F_WHISTLE;
            emitRate[i] = 30f;
        }
        shKind[i] = kind;
        shScale[i] = scale;
        shUser[i] = user;
        pickColors(i);
        bright[i] = 1f;

        float pan = (x0 / W) * 2f - 1f;
        sound.play(SoundEngine.LAUNCH, 0.55f + rnd.nextFloat() * 0.25f, pan, 0.85f + rnd.nextFloat() * 0.3f);
        if (whistle) {
            sound.play(SoundEngine.WHISTLE, 0.35f, pan, 0.8f + rnd.nextFloat() * 0.35f);
        }
        // fogonazo del mortero
        addLight(x0, y0, 0.35f, 1f, 0.6f, 0.3f, 0.08f);
        for (int k = 0; k < 3; k++) {
            addSmoke(x0 + (rnd.nextFloat() - 0.5f) * 0.02f * S, y0 - rnd.nextFloat() * 0.02f * S,
                    (rnd.nextFloat() - 0.5f) * 0.03f * S, -0.03f * S, 0.03f * S, 0.05f * S, 3f + rnd.nextFloat() * 2f, 0.20f);
        }
    }

    void pickColors(int i) {
        int c1 = rnd.nextInt(PALETTE.length);
        int c2 = rnd.nextInt(PALETTE.length);
        if (c2 == c1) c2 = (c1 + 3) % PALETTE.length;
        shC1[i] = c1;
        shC2[i] = c2;
    }

    // ================================================================= alta de partículas

    int add() {
        if (count >= MAX) return -1;
        int i = count++;
        age[i] = 0f;
        emitAcc[i] = 0f;
        timer[i] = 0f;
        phase[i] = rnd.nextFloat() * 6.2831f;
        flags[i] = 0;
        emit[i] = E_NONE;
        emitRate[i] = 0f;
        bright[i] = 1f;
        return i;
    }

    int star(float x0, float y0, float vx0, float vy0, float lf, float[] c, float sz, float dr, float gr) {
        int i = add();
        if (i < 0) return -1;
        type[i] = T_STAR;
        x[i] = px[i] = x0;
        y[i] = py[i] = y0;
        vx[i] = vx0;
        vy[i] = vy0;
        life[i] = maxLife[i] = lf;
        r[i] = r2[i] = c[0];
        g[i] = g2[i] = c[1];
        b[i] = b2[i] = c[2];
        size[i] = sz;
        drag[i] = dr;
        grav[i] = gr;
        return i;
    }

    void spark(int src, float lf) {
        if (count >= MAX - 4000) return;   // reserva espacio para las estrellas
        int i = add();
        type[i] = T_SPARK;
        float j = 0.004f * S;
        x[i] = px[i] = x[src] + (rnd.nextFloat() - 0.5f) * j;
        y[i] = py[i] = y[src] + (rnd.nextFloat() - 0.5f) * j;
        float rv = 0.05f * S;
        vx[i] = vx[src] * 0.25f + (rnd.nextFloat() - 0.5f) * rv;
        vy[i] = vy[src] * 0.25f + (rnd.nextFloat() - 0.5f) * rv;
        drag[i] = 2.5f;
        grav[i] = 0.10f * S;
        life[i] = maxLife[i] = lf * (0.6f + rnd.nextFloat() * 0.8f);
        size[i] = 2.1f * dp * (0.8f + rnd.nextFloat() * 0.5f);
        int e = emit[src];
        if (e == E_GOLD || e == E_GOLD_HEAVY) {
            r[i] = 1.0f; g[i] = 0.55f + rnd.nextFloat() * 0.15f; b[i] = 0.18f;
            if (e == E_GOLD_HEAVY) size[i] *= 1.4f;
        } else if (e == E_SILVER) {
            r[i] = 0.92f; g[i] = 0.94f; b[i] = 1.0f;
        } else {
            r[i] = r[src] * 0.9f + 0.1f; g[i] = g[src] * 0.9f + 0.1f; b[i] = b[src] * 0.9f + 0.1f;
            life[i] *= 0.7f;
        }
        bright[i] = 0.75f;
    }

    void addSmoke(float x0, float y0, float vx0, float vy0, float sz, float grow, float lf, float alpha) {
        if (sCount >= SMAX) {
            // reemplaza el humo más viejo (el índice 0 suele ser antiguo)
            removeSmoke(0);
        }
        int i = sCount++;
        sx[i] = x0;
        sy[i] = y0;
        svx[i] = vx0;
        svy[i] = vy0;
        sLife[i] = 0f;
        sMax[i] = lf;
        sSize[i] = sz;
        sGrow[i] = grow;
        sAlpha[i] = alpha;
        sAng[i] = rnd.nextFloat() * 6.2831f;
        sSpin[i] = (rnd.nextFloat() - 0.5f) * 0.4f;
    }

    void removeSmoke(int i) {
        int l = --sCount;
        if (i != l) {
            sx[i] = sx[l]; sy[i] = sy[l]; svx[i] = svx[l]; svy[i] = svy[l];
            sLife[i] = sLife[l]; sMax[i] = sMax[l]; sSize[i] = sSize[l]; sGrow[i] = sGrow[l];
            sAlpha[i] = sAlpha[l]; sAng[i] = sAng[l]; sSpin[i] = sSpin[l];
        }
    }

    void addLight(float x0, float y0, float inten, float cr, float cg, float cb, float tau) {
        int i;
        if (lCount < LMAX) {
            i = lCount++;
        } else {
            // sustituye la más débil
            i = 0;
            for (int k = 1; k < LMAX; k++) if (li[k] < li[i]) i = k;
            if (li[i] > inten) return;
        }
        lx[i] = x0; ly[i] = y0; li[i] = inten;
        lr[i] = cr; lg[i] = cg; lb[i] = cb; ltau[i] = tau;
    }

    void queueSound(float delay, int which, float vol, float pan, float rate) {
        if (qCount >= QMAX) return;
        qTime[qCount] = time + delay;
        qWhich[qCount] = which;
        qVol[qCount] = vol;
        qPan[qCount] = pan;
        qRate[qCount] = rate;
        qCount++;
    }

    void remove(int i) {
        int l = --count;
        if (i == l) return;
        x[i] = x[l]; y[i] = y[l]; px[i] = px[l]; py[i] = py[l];
        vx[i] = vx[l]; vy[i] = vy[l];
        life[i] = life[l]; maxLife[i] = maxLife[l]; age[i] = age[l];
        r[i] = r[l]; g[i] = g[l]; b[i] = b[l];
        r2[i] = r2[l]; g2[i] = g2[l]; b2[i] = b2[l];
        size[i] = size[l]; drag[i] = drag[l]; grav[i] = grav[l];
        emitRate[i] = emitRate[l]; emitAcc[i] = emitAcc[l]; timer[i] = timer[l];
        phase[i] = phase[l]; bright[i] = bright[l];
        type[i] = type[l]; flags[i] = flags[l]; emit[i] = emit[l];
        shKind[i] = shKind[l]; shC1[i] = shC1[l]; shC2[i] = shC2[l];
        shScale[i] = shScale[l]; shUser[i] = shUser[l];
    }

    // ================================================================= explosiones

    void explode(int s) {
        float ex = x[s], ey = y[s];
        float ivx = vx[s] * 0.3f, ivy = vy[s] * 0.3f;
        int kind = shKind[s];
        float sc = shScale[s];
        float[] c1 = PALETTE[shC1[s]];
        float[] c2 = PALETTE[shC2[s]];
        float R = 0.36f * S * sc;
        float gStar = 0.11f * S;
        float sz = 4.4f * dp * (0.9f + 0.2f * sc);
        float lightTau = 0.6f;
        float lr0 = c1[0], lg0 = c1[1], lb0 = c1[2];
        int boom = sc > 1.15f ? SoundEngine.BOOM_BIG : (sc < 0.9f ? SoundEngine.BOOM_SMALL : SoundEngine.BOOM_MED);
        float boomVol = 0.75f + 0.25f * Math.min(1f, sc);

        switch (kind) {
            case K_PEONY: {
                int n = (int) (110 * sc);
                float d = 2.6f;
                boolean change = rnd.nextFloat() < 0.4f;
                sphere(ex, ey, ivx, ivy, n, R * d, d, 1.5f, 2.1f, c1, sz, gStar, E_OWN, 28f,
                        change ? F_COLORCHANGE : 0, c2, 0.04f);
                break;
            }
            case K_CHRYS: {
                int n = (int) (100 * sc);
                float d = 2.4f;
                int e = rnd.nextBoolean() ? E_GOLD : E_SILVER;
                sphere(ex, ey, ivx, ivy, n, R * d, d, 1.7f, 2.4f, c1, sz, gStar, e, 45f, 0, c2, 0.04f);
                break;
            }
            case K_WILLOW: {
                int n = (int) (120 * sc);
                float d = 1.55f;
                float[] wc = {0.95f, 0.58f, 0.22f};
                sphere(ex, ey, ivx, ivy, n, R * d * 1.05f, d, 3.6f, 4.6f, wc, sz * 0.8f, 0.17f * S,
                        E_GOLD, 60f, F_FLICKER, c2, 0.03f);
                lr0 = 1f; lg0 = 0.6f; lb0 = 0.25f;
                lightTau = 1.4f;
                queueSound(0.6f, SoundEngine.FIZZ, 0.25f, ex / W * 2 - 1, 0.9f);
                break;
            }
            case K_PALM: {
                int n = 7 + rnd.nextInt(4);
                float d = 1.5f;
                float a0 = rnd.nextFloat() * 6.283f;
                for (int k = 0; k < n; k++) {
                    float a = a0 + k * 6.2832f / n + (rnd.nextFloat() - 0.5f) * 0.3f;
                    float sp = R * d * 1.15f * (0.9f + rnd.nextFloat() * 0.2f);
                    float zf = 0.6f + rnd.nextFloat() * 0.4f;
                    int i = star(ex, ey, ivx + (float) Math.cos(a) * sp * zf,
                            ivy + (float) Math.sin(a) * sp * zf - 0.05f * S,
                            2.0f + rnd.nextFloat() * 0.5f, PALETTE[C_GOLD], sz * 1.9f, d, 0.14f * S);
                    if (i >= 0) {
                        emit[i] = E_GOLD_HEAVY;
                        emitRate[i] = 170f;
                        flags[i] = F_FLICKER;
                    }
                }
                lr0 = 1f; lg0 = 0.65f; lb0 = 0.3f;
                lightTau = 1.0f;
                break;
            }
            case K_RING: {
                int n = (int) (64 * sc);
                float d = 2.6f;
                float tilt = (0.25f + rnd.nextFloat() * 0.95f);  // inclinación del plano
                float rot = rnd.nextFloat() * 3.1416f;
                float ct = (float) Math.cos(tilt), cr = (float) Math.cos(rot), sr = (float) Math.sin(rot);
                for (int k = 0; k < n; k++) {
                    float a = k * 6.2832f / n;
                    float ux = (float) Math.cos(a), uy = (float) Math.sin(a) * ct;
                    float rx = ux * cr - uy * sr, ry = ux * sr + uy * cr;
                    float sp = R * d * (0.97f + rnd.nextFloat() * 0.06f);
                    int i = star(ex, ey, ivx + rx * sp, ivy + ry * sp, 1.6f + rnd.nextFloat() * 0.4f, c1, sz, d, gStar);
                    if (i >= 0) { emit[i] = E_OWN; emitRate[i] = 26f; }
                }
                if (rnd.nextFloat() < 0.6f) {
                    sphere(ex, ey, ivx, ivy, 30, R * d * 0.35f, d, 1.2f, 1.6f, c2, sz * 0.9f, gStar, E_NONE, 0f, 0, c2, 0.06f);
                }
                break;
            }
            case K_CROSSETTE: {
                int n = (int) (22 * sc);
                float d = 2.0f;
                sphere(ex, ey, ivx, ivy, n, R * d * 0.75f, d, 0.75f, 0.95f, c1, sz * 1.2f, gStar,
                        E_GOLD, 50f, F_SPLIT, c1, 0.08f);
                break;
            }
            case K_STROBE: {
                int n = (int) (95 * sc);
                float d = 2.4f;
                float[] sc1 = rnd.nextFloat() < 0.7f ? PALETTE[C_WHITE] : c1;
                sphere(ex, ey, ivx, ivy, n, R * d, d, 2.6f, 3.4f, sc1, sz, gStar * 0.9f, E_NONE, 0f,
                        F_STROBE, c2, 0.05f);
                lightTau = 1.2f;
                break;
            }
            case K_CRACKLE: {
                int n = (int) (75 * sc);
                float d = 2.4f;
                sphere(ex, ey, ivx, ivy, n, R * d * 0.9f, d, 1.0f, 1.4f, PALETTE[C_GOLD], sz * 0.9f, gStar,
                        E_GOLD, 35f, F_CRACKLE_END, c2, 0.05f);
                float delay = soundDelay(ey);
                queueSound(delay + 1.05f, SoundEngine.CRACKLE, 0.8f, ex / W * 2 - 1, 0.9f + rnd.nextFloat() * 0.2f);
                lr0 = 1f; lg0 = 0.7f; lb0 = 0.3f;
                break;
            }
            case K_KAMURO: {
                int n = (int) (170 * sc);
                float d = 1.9f;
                float[] kc = rnd.nextFloat() < 0.75f ? new float[]{1.0f, 0.70f, 0.38f} : new float[]{0.9f, 0.9f, 0.95f};
                sphere(ex, ey, ivx, ivy, n, R * d * 1.05f, d, 3.1f, 3.9f, kc, sz * 0.85f, 0.13f * S,
                        kc[2] > 0.8f ? E_SILVER : E_GOLD, 70f, F_FLICKER, c2, 0.03f);
                lr0 = kc[0]; lg0 = kc[1]; lb0 = kc[2];
                lightTau = 1.5f;
                boom = SoundEngine.BOOM_BIG;
                queueSound(0.9f, SoundEngine.FIZZ, 0.3f, ex / W * 2 - 1, 0.8f);
                break;
            }
            case K_PISTIL: {
                int n = (int) (110 * sc);
                float d = 2.6f;
                sphere(ex, ey, ivx, ivy, n, R * d, d, 1.6f, 2.1f, c1, sz, gStar, E_OWN, 28f, 0, c1, 0.04f);
                sphere(ex, ey, ivx, ivy, (int) (50 * sc), R * d * 0.45f, d, 1.4f, 1.8f, c2, sz * 0.95f, gStar,
                        E_NONE, 0f, 0, c2, 0.05f);
                break;
            }
            case K_SALUTE: {
                // Trueno: destello blanco cegador y estruendo seco
                for (int k = 0; k < 50; k++) {
                    float a = rnd.nextFloat() * 6.283f;
                    float sp = (0.3f + rnd.nextFloat() * 0.7f) * 0.6f * S;
                    int i = star(ex, ey, (float) Math.cos(a) * sp, (float) Math.sin(a) * sp, 0.12f + rnd.nextFloat() * 0.2f,
                            PALETTE[C_WHITE], sz * 0.8f, 7f, gStar);
                    if (i >= 0) bright[i] = 1.4f;
                }
                flash(ex, ey, 0.45f * S, 1f, 0.97f, 0.9f, 0.12f, 0.9f);
                addLight(ex, ey, 3.2f, 1f, 0.95f, 0.85f, 0.18f);
                boom = SoundEngine.SALUTE;
                boomVol = 1f;
                lr0 = -1f; // ya añadida
                for (int k = 0; k < 10; k++) {
                    addSmoke(ex + (rnd.nextFloat() - 0.5f) * 0.08f * S, ey + (rnd.nextFloat() - 0.5f) * 0.08f * S,
                            (rnd.nextFloat() - 0.5f) * 0.06f * S, (rnd.nextFloat() - 0.5f) * 0.06f * S,
                            0.05f * S, 0.06f * S, 6f + rnd.nextFloat() * 3f, 0.26f);
                }
                if (haptics != null) haptics.buzz(45, 255);
                break;
            }
            case K_HEART: {
                int n = (int) (80 * sc);
                float d = 2.6f;
                float rot = (rnd.nextFloat() - 0.5f) * 0.5f;
                float cr = (float) Math.cos(rot), sr = (float) Math.sin(rot);
                float[] hc = rnd.nextBoolean() ? PALETTE[C_RED] : PALETTE[C_PINK];
                for (int k = 0; k < n; k++) {
                    float t = k * 6.2832f / n;
                    float hx = (float) (16 * Math.pow(Math.sin(t), 3)) / 17f;
                    float hy = -(float) (13 * Math.cos(t) - 5 * Math.cos(2 * t) - 2 * Math.cos(3 * t) - Math.cos(4 * t)) / 17f;
                    float rx = hx * cr - hy * sr, ry = hx * sr + hy * cr;
                    float sp = R * d * 0.95f;
                    int i = star(ex, ey, ivx + rx * sp, ivy + ry * sp, 1.6f + rnd.nextFloat() * 0.3f, hc, sz, d, gStar * 0.6f);
                    if (i >= 0) { emit[i] = E_OWN; emitRate[i] = 24f; }
                }
                lr0 = hc[0]; lg0 = hc[1]; lb0 = hc[2];
                break;
            }
        }

        if (kind != K_SALUTE) {
            flash(ex, ey, 0.20f * S * sc, lr0 * 0.5f + 0.5f, lg0 * 0.5f + 0.5f, lb0 * 0.5f + 0.5f, 0.10f, 0.5f);
            addLight(ex, ey, 1.25f * sc, lr0, lg0, lb0, lightTau);
            int puffs = 8 + rnd.nextInt(6);
            for (int k = 0; k < puffs; k++) {
                float a = rnd.nextFloat() * 6.283f, rr = (float) Math.sqrt(rnd.nextFloat()) * R * 0.55f;
                addSmoke(ex + (float) Math.cos(a) * rr, ey + (float) Math.sin(a) * rr,
                        (float) Math.cos(a) * 0.03f * S, (float) Math.sin(a) * 0.03f * S,
                        0.06f * S * sc, 0.07f * S, 7f + rnd.nextFloat() * 4f, 0.15f);
            }
            if (haptics != null && sc > 1.0f) haptics.buzz(18, 120);
        }

        // Sonido retardado por la distancia (la luz llega antes que el sonido).
        float delay = soundDelay(ey);
        float pan = ex / W * 2f - 1f;
        queueSound(delay, boom, boomVol, pan, 0.88f + rnd.nextFloat() * 0.24f);

        if (shUser[s]) scoreBurst(kind, sc);
    }

    float soundDelay(float ey) {
        float height = (ground - ey) / S; // 0.2 .. 1+
        return 0.12f + height * 0.35f;
    }

    void flash(float x0, float y0, float sz, float cr, float cg, float cb, float lf, float br) {
        int i = add();
        if (i < 0) return;
        type[i] = T_FLASH;
        x[i] = px[i] = x0;
        y[i] = py[i] = y0;
        vx[i] = vy[i] = 0;
        life[i] = maxLife[i] = lf;
        size[i] = sz;
        r[i] = cr; g[i] = cg; b[i] = cb;
        drag[i] = 0; grav[i] = 0;
        bright[i] = br;
    }

    /** Explosión esférica con distribución de Fibonacci (como las carcasas reales). */
    void sphere(float ex, float ey, float ivx, float ivy, int n, float speed, float d, float lifeMin, float lifeMax,
                float[] c, float sz, float gr, int emitKind, float rate, int fl, float[] cAlt, float jitter) {
        float golden = 2.39996323f;
        float rot = rnd.nextFloat() * 6.283f;
        for (int k = 0; k < n; k++) {
            float u = 1f - 2f * (k + 0.5f) / n;
            float rr = (float) Math.sqrt(1f - u * u);
            float th = k * golden + rot;
            float dx = (float) Math.cos(th) * rr + (rnd.nextFloat() - 0.5f) * jitter * 2;
            float dy = u + (rnd.nextFloat() - 0.5f) * jitter * 2;
            float dz = (float) Math.sin(th) * rr;
            float sp = speed * (0.94f + rnd.nextFloat() * 0.12f);
            float lf = lifeMin + rnd.nextFloat() * (lifeMax - lifeMin);
            int i = star(ex, ey, ivx + dx * sp, ivy + dy * sp, lf, c, sz * (1f + 0.18f * dz), d, gr);
            if (i < 0) return;
            emit[i] = emitKind;
            emitRate[i] = rate * (0.8f + rnd.nextFloat() * 0.4f);
            flags[i] = fl;
            bright[i] = 0.85f + 0.15f * dz;
            if ((fl & F_COLORCHANGE) != 0 || cAlt != c) {
                r2[i] = cAlt[0]; g2[i] = cAlt[1]; b2[i] = cAlt[2];
            }
            if ((fl & F_STROBE) != 0) timer[i] = 0.35f + rnd.nextFloat() * 0.3f;
            if ((fl & F_SPLIT) != 0) timer[i] = lf;
        }
    }

    void split(int i) {
        float a0 = rnd.nextFloat() * 1.57f;
        float sp = 0.30f * S;
        float[] c = {r[i], g[i], b[i]};
        float ex = x[i], ey = y[i], evx = vx[i], evy = vy[i];
        for (int k = 0; k < 4; k++) {
            float a = a0 + k * 1.5708f;
            int j = star(ex, ey, evx + (float) Math.cos(a) * sp, evy + (float) Math.sin(a) * sp,
                    0.8f + rnd.nextFloat() * 0.3f, c, size[i] * 0.85f, 2.4f, 0.11f * S);
            if (j >= 0) {
                emit[j] = E_GOLD;
                emitRate[j] = 45f;
            }
        }
        flash(ex, ey, 0.03f * S, 1f, 0.95f, 0.85f, 0.06f, 0.8f);
        if (rnd.nextFloat() < 0.3f) {
            queueSound(soundDelay(ey), SoundEngine.CRACKLE, 0.15f, ex / W * 2 - 1, 1.6f);
        }
    }

    void crackleBurst(int i) {
        int n = 4 + rnd.nextInt(5);
        for (int k = 0; k < n; k++) {
            int j = add();
            if (j < 0) return;
            type[j] = T_CRACK;
            float rr = 0.025f * S;
            x[j] = px[j] = x[i] + (rnd.nextFloat() - 0.5f) * rr * 2;
            y[j] = py[j] = y[i] + (rnd.nextFloat() - 0.5f) * rr * 2;
            vx[j] = vx[i] * 0.3f;
            vy[j] = vy[i] * 0.3f;
            drag[j] = 2f;
            grav[j] = 0.05f * S;
            timer[j] = rnd.nextFloat() * 0.45f;     // espera antes de estallar
            life[j] = maxLife[j] = 0.04f + rnd.nextFloat() * 0.05f;
            size[j] = (4f + rnd.nextFloat() * 3f) * dp;
            r[j] = 1f; g[j] = 0.92f; b[j] = 0.75f;
            bright[j] = 1.3f;
        }
    }

    void scoreBurst(int kind, float sc) {
        if (time - lastBurst < 1.6f) {
            combo = Math.min(combo + 1, 15);
        } else {
            combo = 1;
        }
        lastBurst = time;
        int base = (int) (10 * sc + (kind == K_SALUTE ? 5 : 0) + (kind == K_KAMURO || kind == K_PALM ? 4 : 0));
        score += base * combo;
        if (!autoMode) meter = Math.min(1f, meter + 0.012f * combo + 0.01f);
        if (score > best) best = score;
        statsDirty = true;
    }

    // ================================================================= paso de simulación

    public void update(float dt) {
        time += dt;

        // Lanzamientos programados (gran final / modo automático)
        for (int k = 0; k < lqCount; ) {
            if (lqTime[k] <= time) {
                launch(lqX[k] * W, lqY[k] * H, lqKind[k], lqScale[k], false);
                int l = --lqCount;
                lqTime[k] = lqTime[l]; lqX[k] = lqX[l]; lqY[k] = lqY[l];
                lqKind[k] = lqKind[l]; lqScale[k] = lqScale[l];
            } else k++;
        }

        // Ráfaga al mantener pulsado
        for (int p = 0; p < PMAX; p++) {
            if (pDown[p] && time >= pNext[p]) {
                launchUser(pX[p], pY[p]);
                pNext[p] = time + 0.28f;
            }
        }

        if (autoMode && time >= autoNext) autoShow();

        // Sonidos programados
        for (int k = 0; k < qCount; ) {
            if (qTime[k] <= time) {
                sound.play(qWhich[k], qVol[k], qPan[k], qRate[k]);
                int l = --qCount;
                qTime[k] = qTime[l]; qWhich[k] = qWhich[l]; qVol[k] = qVol[l];
                qPan[k] = qPan[l]; qRate[k] = qRate[l];
            } else k++;
        }

        updateParticles(dt);
        updateSmoke(dt);

        for (int k = 0; k < lCount; ) {
            li[k] *= (float) Math.exp(-dt / ltau[k]);
            if (li[k] < 0.01f) {
                int l = --lCount;
                lx[k] = lx[l]; ly[k] = ly[l]; li[k] = li[l];
                lr[k] = lr[l]; lg[k] = lg[l]; lb[k] = lb[l]; ltau[k] = ltau[l];
            } else k++;
        }

        if (combo > 0 && time - lastBurst > 1.6f) {
            combo = 0;
            statsDirty = true;
        }
        statsTimer -= dt;
        if (statsDirty && statsTimer <= 0 && listener != null) {
            statsDirty = false;
            statsTimer = 0.1f;
            listener.onStats(score, combo, meter, best);
        }
    }

    void autoShow() {
        float roll = rnd.nextFloat();
        if (roll < 0.18f) {
            // Abanico de carcasas iguales
            int k = randomKind(false);
            int n = 3 + rnd.nextInt(3);
            float y0 = 0.15f + rnd.nextFloat() * 0.2f;
            for (int i = 0; i < n; i++) {
                float fx = 0.15f + 0.7f * i / (n - 1f);
                queueLaunch(time + i * 0.12f, fx, y0 + Math.abs(i - (n - 1) / 2f) * 0.04f, k, 0.85f);
            }
            autoNext = time + 2.6f + rnd.nextFloat();
        } else if (roll < 0.30f) {
            // Par simétrico
            int k = randomKind(false);
            float fx = 0.15f + rnd.nextFloat() * 0.25f;
            float fy = 0.12f + rnd.nextFloat() * 0.3f;
            queueLaunch(time, fx, fy, k, 1f);
            queueLaunch(time, 1f - fx, fy, k, 1f);
            autoNext = time + 1.6f + rnd.nextFloat();
        } else {
            launch((0.1f + rnd.nextFloat() * 0.8f) * W, (0.1f + rnd.nextFloat() * 0.4f) * H,
                    randomKind(rnd.nextFloat() < 0.15f), 0.85f + rnd.nextFloat() * 0.45f, false);
            autoNext = time + 0.5f + rnd.nextFloat() * 1.3f;
        }
    }

    void updateParticles(float dt) {
        // Con mucha carga se reduce la emisión de chispas para mantener la fluidez.
        float load = (float) count / MAX;
        float emitScale = load < 0.4f ? 1f : Math.max(0.15f, 1f - (load - 0.4f) / 0.45f);
        for (int i = 0; i < count; ) {
            int t = type[i];
            if (t == T_CRACK && timer[i] > 0) {
                timer[i] -= dt;
                float k = (float) Math.exp(-drag[i] * dt);
                vx[i] *= k; vy[i] = vy[i] * k + grav[i] * dt;
                x[i] += vx[i] * dt; y[i] += vy[i] * dt;
                px[i] = x[i]; py[i] = y[i];
                if (timer[i] <= 0 && rnd.nextFloat() < 0.5f) {
                    addLight(x[i], y[i], 0.15f, 1f, 0.9f, 0.7f, 0.05f);
                }
                i++;
                continue;
            }
            age[i] += dt;
            life[i] -= dt;
            if (life[i] <= 0) {
                if (t == T_SHELL) {
                    explode(i);
                } else if (t == T_STAR) {
                    if ((flags[i] & F_SPLIT) != 0) split(i);
                    if ((flags[i] & F_CRACKLE_END) != 0) crackleBurst(i);
                }
                remove(i);
                continue;
            }
            px[i] = x[i];
            py[i] = y[i];
            float dr = drag[i];
            if (dr > 0) {
                float k = (float) Math.exp(-dr * dt);
                vx[i] *= k;
                vy[i] *= k;
            }
            vy[i] += grav[i] * dt;
            vx[i] += wind * S * dt * (t == T_SPARK ? 0.6f : 0.15f);
            x[i] += vx[i] * dt;
            y[i] += vy[i] * dt;

            if (emitRate[i] > 0 && emit[i] != E_NONE) {
                float lifeFrac = life[i] / maxLife[i];
                if (t != T_STAR || lifeFrac > 0.08f) {
                    emitAcc[i] += emitRate[i] * dt * emitScale;
                    float sparkLife = emit[i] == E_GOLD_HEAVY ? 0.9f : (emit[i] == E_OWN ? 0.45f : 0.7f);
                    if (t == T_SHELL) sparkLife = emit[i] == E_GOLD_HEAVY ? 0.8f : 0.4f;
                    while (emitAcc[i] >= 1f) {
                        emitAcc[i] -= 1f;
                        spark(i, sparkLife);
                    }
                }
            }
            if (t == T_SHELL) {
                timer[i] += dt;
                if (timer[i] > 0.06f) {
                    timer[i] = 0f;
                    addSmoke(x[i], y[i], 0, 0, 0.012f * S, 0.03f * S, 2.5f + rnd.nextFloat() * 1.5f, 0.10f);
                }
            }
            i++;
        }
    }

    void updateSmoke(float dt) {
        float drift = wind * S;
        for (int i = 0; i < sCount; ) {
            sLife[i] += dt;
            if (sLife[i] >= sMax[i]) {
                removeSmoke(i);
                continue;
            }
            float k = (float) Math.exp(-1.2f * dt);
            svx[i] = svx[i] * k + drift * (1f - k);
            svy[i] = svy[i] * k - 0.006f * S * (1f - k); // sube muy despacio
            sx[i] += svx[i] * dt;
            sy[i] += svy[i] * dt;
            sSize[i] += sGrow[i] * dt * (1f / (1f + sLife[i] * 0.4f));
            sAng[i] += sSpin[i] * dt;
            i++;
        }
    }

    // ================================================================= salida para el render

    /** Rellena los vértices de partículas: x, y, tamaño, r, g, b, a. Devuelve nº de vértices. */
    public int fillPoints(float[] out, float maxPoint) {
        int n = 0;
        int cap = out.length / 7;
        for (int i = 0; i < count && n < cap - 5; i++) {
            int t = type[i];
            float a;
            float cr = r[i], cg = g[i], cb = b[i];
            float sz = size[i];
            if (t == T_CRACK) {
                if (timer[i] > 0) continue;
                a = bright[i] * (life[i] / maxLife[i]);
            } else if (t == T_FLASH) {
                float f = life[i] / maxLife[i];
                a = bright[i] * f * f;
                sz *= 0.6f + 0.4f * f;
            } else if (t == T_SPARK) {
                float f = life[i] / maxLife[i];
                a = bright[i] * f * (0.55f + 0.45f * rnd.nextFloat());
                // las chispas de carbón se enfrían: pasan de amarillo a rojo
                cg *= 0.6f + 0.4f * f;
                cb *= 0.4f + 0.6f * f;
            } else if (t == T_SHELL) {
                a = 0.9f;
            } else {
                float f = life[i] / maxLife[i];
                float ageS = age[i];
                a = bright[i] * Math.min(1f, ageS / 0.06f);
                if (ageS < 0.12f) {
                    // estrellas recién encendidas: más blancas y brillantes
                    float w = 1f - ageS / 0.12f;
                    cr += (1 - cr) * w * 0.6f; cg += (1 - cg) * w * 0.6f; cb += (1 - cb) * w * 0.6f;
                    a *= 1f + w * 0.5f;
                }
                if ((flags[i] & F_COLORCHANGE) != 0) {
                    float m = Math.max(0f, Math.min(1f, (0.5f - f) / 0.06f));
                    cr += (r2[i] - cr) * m; cg += (g2[i] - cg) * m; cb += (b2[i] - cb) * m;
                }
                if (f < 0.25f) {
                    a *= f / 0.25f;
                    if ((flags[i] & F_FLICKER) != 0) a *= 0.4f + 0.6f * rnd.nextFloat();
                }
                if ((flags[i] & F_STROBE) != 0 && ageS > timer[i]) {
                    float s = (float) Math.sin(phase[i] + ageS * 6.2832f * 11f);
                    if (s < 0.3f) continue;
                    a *= 1.4f;
                    sz *= 1.25f;
                }
                if ((flags[i] & F_FLICKER) != 0) a *= 0.8f + 0.2f * rnd.nextFloat();
            }
            if (a <= 0.01f) continue;
            if (sz > maxPoint) sz = maxPoint;

            if (t == T_STAR || t == T_SHELL) {
                // Sub-pasos para trazos continuos (desenfoque de movimiento)
                float dx = x[i] - px[i], dy = y[i] - py[i];
                float dist = (float) Math.sqrt(dx * dx + dy * dy);
                int steps = (int) (dist / (sz * 0.5f + 0.5f)) + 1;
                if (steps > 5) steps = 5;
                for (int s = 1; s <= steps; s++) {
                    float k = (float) s / steps;
                    int o = n * 7;
                    out[o] = px[i] + dx * k;
                    out[o + 1] = py[i] + dy * k;
                    out[o + 2] = sz;
                    out[o + 3] = cr;
                    out[o + 4] = cg;
                    out[o + 5] = cb;
                    out[o + 6] = a * (0.55f + 0.45f * k);
                    n++;
                }
            } else {
                int o = n * 7;
                out[o] = x[i];
                out[o + 1] = y[i];
                out[o + 2] = sz;
                out[o + 3] = cr;
                out[o + 4] = cg;
                out[o + 5] = cb;
                out[o + 6] = a;
                n++;
            }
        }
        return n;
    }

    /** Humo: x, y, tamaño, r, g, b, a, ángulo. Iluminado por las explosiones. */
    public int fillSmoke(float[] out, float maxPoint) {
        int n = 0;
        int cap = out.length / 8;
        float fall = 0.28f * S;
        float fall2 = 1f / (fall * fall);
        for (int i = 0; i < sCount && n < cap; i++) {
            float f = sLife[i] / sMax[i];
            float a = sAlpha[i] * Math.min(1f, sLife[i] / 0.25f) * (float) Math.pow(1f - f, 1.6);
            if (a < 0.004f) continue;
            float cr = 0.05f, cg = 0.05f, cb = 0.065f;
            // resplandor de la ciudad desde abajo
            float hz = Math.max(0f, 1f - (ground - sy[i]) / (0.6f * S));
            cr += 0.05f * hz; cg += 0.03f * hz; cb += 0.02f * hz;
            for (int k = 0; k < lCount; k++) {
                float dx = sx[i] - lx[k], dy = sy[i] - ly[k];
                float fl = li[k] / (1f + (dx * dx + dy * dy) * fall2);
                cr += lr[k] * fl * 0.45f;
                cg += lg[k] * fl * 0.45f;
                cb += lb[k] * fl * 0.45f;
            }
            int o = n * 8;
            out[o] = sx[i];
            out[o + 1] = sy[i];
            out[o + 2] = Math.min(maxPoint, sSize[i]);
            out[o + 3] = Math.min(1f, cr);
            out[o + 4] = Math.min(1f, cg);
            out[o + 5] = Math.min(1f, cb);
            out[o + 6] = a;
            out[o + 7] = sAng[i];
            n++;
        }
        return n;
    }

    /** Las 8 luces más intensas para el shader del cielo. */
    public int fillLights(float[] pos, float[] col) {
        int n = Math.min(8, lCount);
        // selección simple de las más intensas
        boolean[] used = lightUsed;
        java.util.Arrays.fill(used, false);
        for (int j = 0; j < n; j++) {
            int best = -1;
            for (int k = 0; k < lCount; k++) {
                if (!used[k] && (best < 0 || li[k] > li[best])) best = k;
            }
            used[best] = true;
            pos[j * 4] = lx[best] / W;
            pos[j * 4 + 1] = ly[best] / H;
            pos[j * 4 + 2] = Math.min(3f, li[best]);
            pos[j * 4 + 3] = 0f;
            col[j * 3] = lr[best];
            col[j * 3 + 1] = lg[best];
            col[j * 3 + 2] = lb[best];
        }
        for (int j = n; j < 8; j++) {
            pos[j * 4] = pos[j * 4 + 1] = pos[j * 4 + 2] = pos[j * 4 + 3] = 0f;
            col[j * 3] = col[j * 3 + 1] = col[j * 3 + 2] = 0f;
        }
        return n;
    }
}
