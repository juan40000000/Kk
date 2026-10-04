package com.juan.fuegos;

import java.util.Random;

/**
 * Modo Guerra 3D: dos ciudades enfrentadas a través de una bahía se atacan con
 * fuegos artificiales. Coordenadas del mundo en metros: x a lo ancho, y hacia
 * arriba, z hacia la cámara. Tu ciudad está cerca (z > 0) y la enemiga lejos (z < 0).
 * Todo se ejecuta en el hilo de render de OpenGL.
 */
public class War {

    public static final int PLAYER = 0, ENEMY = 1;

    public static final int W_ROCKET = 0, W_MEGA = 1, W_CLUSTER = 2, W_FIRE = 3;
    public static final int WCOUNT = 4;
    static final int W_BOMBLET = 10, W_INTERCEPT = 11, W_SHOW = 12;
    public static final String[] W_NAMES = {"Cohete", "Mega carcasa", "Racimo", "Incendiario"};
    public static final float[] W_COOLDOWN = {0.9f, 7f, 5f, 4.5f};

    public static final int S_MENU = 0, S_PLAY = 1, S_WON = 2, S_LOST = 3;

    // Zonas
    static final float PLAYER_Z0 = 14f, PLAYER_Z1 = 36f;
    static final float ENEMY_Z0 = -56f, ENEMY_Z1 = -32f;
    static final float SHORE_PLAYER = 12f, SHORE_ENEMY = -28f;
    static final float CITY_HALF_W = 30f;
    static final float G = 14f;
    static final float LOSE_AT = 0.25f;

    // ------------------------------------------------------------ edificios
    static final int BMAX = 260;
    final float[] bx0 = new float[BMAX], bx1 = new float[BMAX], bz0 = new float[BMAX], bz1 = new float[BMAX];
    final float[] bh = new float[BMAX], health = new float[BMAX], fire = new float[BMAX], charr = new float[BMAX];
    final float[] hs = new float[BMAX], collapseT = new float[BMAX], seed = new float[BMAX], flameAcc = new float[BMAX];
    final float[] bcr = new float[BMAX], bcg = new float[BMAX], bcb = new float[BMAX];
    final int[] team = new int[BMAX];
    final boolean[] pad = new boolean[BMAX], collapsing = new boolean[BMAX], down = new boolean[BMAX];
    int bCount = 0;
    final int[][] pads = new int[2][3];

    // ------------------------------------------------------------ partículas 3D
    static final int T_ROCKET = 0, T_INTERCEPT = 1, T_STAR = 2, T_SPARK = 3, T_FLAME = 4, T_FLASH = 5,
            T_BOMBLET = 6, T_CRACK = 7;
    static final int F_DEAD = 1, F_FLICKER = 2, F_CRACKLE = 4, F_SPLIT = 8, F_CHECKED = 16;
    static final int E_NONE = 0, E_GOLD = 1, E_TEAM = 2, E_OWN = 3, E_SILVER = 4;

    public static final int MAX = 24000;
    final float[] x = new float[MAX], y = new float[MAX], z = new float[MAX];
    final float[] px = new float[MAX], py = new float[MAX], pz = new float[MAX];
    final float[] vx = new float[MAX], vy = new float[MAX], vz = new float[MAX];
    final float[] life = new float[MAX], maxLife = new float[MAX], age = new float[MAX];
    final float[] r = new float[MAX], g = new float[MAX], b = new float[MAX];
    final float[] size = new float[MAX], drag = new float[MAX], grav = new float[MAX];
    final float[] emitRate = new float[MAX], emitAcc = new float[MAX], bright = new float[MAX], timer = new float[MAX];
    final int[] type = new int[MAX], flags = new int[MAX], emit = new int[MAX], pteam = new int[MAX],
            weapon = new int[MAX], pid = new int[MAX], target = new int[MAX];
    int count = 0;
    int nextId = 1;

    // ------------------------------------------------------------ humo / polvo
    public static final int SMAX = 1600;
    final float[] sx = new float[SMAX], sy = new float[SMAX], sz = new float[SMAX];
    final float[] svx = new float[SMAX], svy = new float[SMAX], svz = new float[SMAX];
    final float[] sLife = new float[SMAX], sMax = new float[SMAX], sSize = new float[SMAX], sGrow = new float[SMAX],
            sAlpha = new float[SMAX], sAng = new float[SMAX], sSpin = new float[SMAX], sDust = new float[SMAX];
    int sCount = 0;

    // ------------------------------------------------------------ luces
    static final int LMAX = 24;
    final float[] lx = new float[LMAX], ly = new float[LMAX], lz = new float[LMAX], li = new float[LMAX],
            lr = new float[LMAX], lg = new float[LMAX], lb = new float[LMAX], ltau = new float[LMAX];
    int lCount = 0;

    // ------------------------------------------------------------ sonidos con retardo
    static final int QMAX = 96;
    final float[] qTime = new float[QMAX], qVol = new float[QMAX], qPan = new float[QMAX], qRate = new float[QMAX];
    final int[] qWhich = new int[QMAX];
    int qCount = 0;

    // ------------------------------------------------------------ estado del juego
    final Random rnd = new Random();
    final SoundEngine sound;
    final Fireworks.Haptics haptics;
    float time = 0f;
    int state = S_MENU;
    int difficulty = 1;
    final float[][] cooldown = new float[2][WCOUNT];
    final float[] charges = {3f, 3f};
    int selectedWeapon = W_ROCKET;
    float aiTimer = 3f;
    float celebrateTimer = 0f;
    float wind = 0.6f;
    float shake = 0f;
    float menuTimer = 0f;
    int destroyedEnemy = 0, destroyedPlayer = 0;

    // Cámara
    final float[] eye = new float[3], look = new float[3];

    public interface Listener {
        void onStats(float playerInt, float enemyInt, float[] cooldownFrac, int charges, int selected);

        void onState(int state);

        void onMessage(String msg);
    }

    Listener listener;
    float statsTimer = 0f;
    private final float[] cdFrac = new float[WCOUNT];

    public War(SoundEngine sound, Fireworks.Haptics haptics) {
        this.sound = sound;
        this.haptics = haptics;
        buildCities(12345L);
        updateCamera(0f);
    }

    // ================================================================= ciudades

    void buildCities(long s) {
        Random cr = new Random(s);
        bCount = 0;
        buildCity(PLAYER, cr);
        buildCity(ENEMY, cr);
    }

    void buildCity(int t, Random cr) {
        float z0 = t == PLAYER ? PLAYER_Z0 : ENEMY_Z0;
        float z1 = t == PLAYER ? PLAYER_Z1 : ENEMY_Z1;
        float lot = 4.4f;
        // Plataformas de lanzamiento en la orilla
        float padZ = t == PLAYER ? SHORE_PLAYER + 1.2f : SHORE_ENEMY - 1.2f;
        for (int k = 0; k < 3; k++) {
            float px0 = -20f + k * 20f;
            int i = addBuilding(t, px0 - 1.1f, px0 + 1.1f, padZ - 1.1f, padZ + 1.1f, 1.1f);
            pad[i] = true;
            bcr[i] = 0.22f; bcg[i] = 0.22f; bcb[i] = 0.24f;
            seed[i] = -1f;
            pads[t][k] = i;
        }
        for (float zz = z0; zz < z1 - 1f; zz += lot) {
            for (float xx = -CITY_HALF_W; xx < CITY_HALF_W - 1f; xx += lot) {
                if (cr.nextFloat() < 0.13f) continue;           // calles / plazas
                if (bCount >= BMAX) return;
                float w = 2.2f + cr.nextFloat() * 1.4f;
                float d = 2.2f + cr.nextFloat() * 1.4f;
                float cx = xx + lot / 2 + (cr.nextFloat() - 0.5f) * (lot - w) * 0.6f;
                float cz = zz + lot / 2 + (cr.nextFloat() - 0.5f) * (lot - d) * 0.6f;
                float center = 1f - Math.abs(cx) / CITY_HALF_W;
                float h = 3f + (float) Math.pow(cr.nextFloat(), 1.8) * 10f * (0.6f + center * 0.9f);
                if (cr.nextFloat() < 0.06f) h += 8f;                 // algún rascacielos
                if (t == PLAYER) h *= 0.72f;                         // no tapar la vista de la bahía
                int i = addBuilding(t, cx - w / 2, cx + w / 2, cz - d / 2, cz + d / 2, h);
                float tone = 0.32f + cr.nextFloat() * 0.25f;
                if (t == PLAYER) {
                    bcr[i] = tone * 0.9f; bcg[i] = tone * 0.95f; bcb[i] = tone * 1.08f;
                } else {
                    bcr[i] = tone * 1.08f; bcg[i] = tone * 0.93f; bcb[i] = tone * 0.85f;
                }
                seed[i] = cr.nextFloat() * 100f;
            }
        }
    }

    int addBuilding(int t, float x0, float x1, float z0, float z1, float h) {
        int i = bCount++;
        bx0[i] = x0; bx1[i] = x1; bz0[i] = z0; bz1[i] = z1; bh[i] = h;
        health[i] = 100f; fire[i] = 0f; charr[i] = 0f; hs[i] = 1f; collapseT[i] = 0f;
        team[i] = t; pad[i] = false; collapsing[i] = false; down[i] = false; flameAcc[i] = 0f;
        return i;
    }

    public float integrity(int t) {
        float sum = 0, n = 0;
        for (int i = 0; i < bCount; i++) {
            if (team[i] != t || pad[i]) continue;
            n += 100f;
            if (!down[i] && !collapsing[i]) sum += Math.max(0f, health[i]);
        }
        return n > 0 ? sum / n : 0f;
    }

    // ================================================================= partida

    public void newGame(int diff) {
        difficulty = diff;
        buildCities(rnd.nextLong());
        count = 0;
        sCount = 0;
        lCount = 0;
        qCount = 0;
        for (int t = 0; t < 2; t++) {
            for (int w = 0; w < WCOUNT; w++) cooldown[t][w] = 0f;
            charges[t] = 3f;
        }
        cooldown[ENEMY][W_MEGA] = 12f;
        aiTimer = 3.5f;
        destroyedEnemy = destroyedPlayer = 0;
        wind = (rnd.nextBoolean() ? 1 : -1) * (0.3f + rnd.nextFloat() * 0.6f);
        state = S_PLAY;
        if (listener != null) {
            listener.onState(state);
            listener.onMessage("¡Defiende tu ciudad!");
        }
    }

    public void toMenu() {
        state = S_MENU;
        if (listener != null) listener.onState(state);
    }

    public void selectWeapon(int w) {
        selectedWeapon = w;
        statsTimer = 0f;
    }

    // ================================================================= entrada

    /** Toque en pantalla: intercepta un cohete enemigo cercano o ataca donde se tocó. */
    public void tap(float sx, float sy, int W, int H, float[] vp, float[] invVp, float dp) {
        if (state != S_PLAY) return;
        // 1) ¿Cohete enemigo cerca del dedo?
        float bestD = 70f * dp;
        int best = -1;
        float[] clip = new float[4];
        for (int i = 0; i < count; i++) {
            if ((type[i] != T_ROCKET && type[i] != T_BOMBLET) || pteam[i] != ENEMY || (flags[i] & F_DEAD) != 0) continue;
            if (isTargeted(pid[i])) continue;
            project(vp, x[i], y[i], z[i], clip);
            if (clip[3] <= 0.01f) continue;
            float qx = (clip[0] / clip[3] * 0.5f + 0.5f) * W;
            float qy = (1f - (clip[1] / clip[3] * 0.5f + 0.5f)) * H;
            float d = (float) Math.hypot(qx - sx, qy - sy);
            if (d < bestD) {
                bestD = d;
                best = i;
            }
        }
        if (best >= 0) {
            if (charges[PLAYER] >= 1f) {
                charges[PLAYER] -= 1f;
                launchInterceptor(PLAYER, best);
                statsTimer = 0f;
            } else if (listener != null) {
                listener.onMessage("Sin interceptores: recargando…");
            }
            return;
        }

        // 2) Rayo desde la cámara
        float nx = sx / W * 2f - 1f, ny = 1f - sy / H * 2f;
        float[] p0 = unproject(invVp, nx, ny, -1f), p1 = unproject(invVp, nx, ny, 1f);
        float ox = p0[0], oy = p0[1], oz = p0[2];
        float dx = p1[0] - ox, dy = p1[1] - oy, dz = p1[2] - oz;
        float len = (float) Math.sqrt(dx * dx + dy * dy + dz * dz);
        dx /= len; dy /= len; dz /= len;

        float tBest = Float.MAX_VALUE;
        for (int i = 0; i < bCount; i++) {
            if (team[i] != ENEMY || down[i]) continue;
            float t = rayBox(ox, oy, oz, dx, dy, dz, bx0[i], 0f, bz0[i], bx1[i], bh[i] * hs[i], bz1[i]);
            if (t > 0 && t < tBest) tBest = t;
        }
        float tx, ty, tz;
        if (tBest < Float.MAX_VALUE) {
            tx = ox + dx * tBest; ty = oy + dy * tBest; tz = oz + dz * tBest;
        } else {
            float tg = dy < -1e-4f ? -oy / dy : -1f;
            float gz = oz + dz * tg;
            if (tg > 0 && gz < SHORE_ENEMY + 2f) {
                tx = ox + dx * tg; ty = 0.3f; tz = gz;
            } else {
                // Por encima de la ciudad enemiga: explosión aérea
                float pzPlane = (ENEMY_Z0 + ENEMY_Z1) * 0.5f;
                float tp = Math.abs(dz) > 1e-4f ? (pzPlane - oz) / dz : -1f;
                if (tp <= 0) return;
                tx = ox + dx * tp;
                ty = Math.max(1f, Math.min(22f, oy + dy * tp));
                tz = pzPlane;
                if (ty < 1f) return;
            }
        }
        if (tz > SHORE_ENEMY + 6f) {
            if (listener != null) listener.onMessage("Apunta a la ciudad enemiga");
            return;
        }
        tx = Math.max(-CITY_HALF_W - 8f, Math.min(CITY_HALF_W + 8f, tx));
        int w = selectedWeapon;
        if (cooldown[PLAYER][w] > 0f) {
            if (listener != null) listener.onMessage(W_NAMES[w] + " recargando…");
            return;
        }
        cooldown[PLAYER][w] = W_COOLDOWN[w];
        launchRocket(PLAYER, w, tx, ty, tz, 0.8f);
        statsTimer = 0f;
    }

    static void project(float[] m, float px, float py, float pz, float[] out) {
        for (int k = 0; k < 4; k++) {
            out[k] = m[k] * px + m[4 + k] * py + m[8 + k] * pz + m[12 + k];
        }
    }

    static float[] unproject(float[] inv, float nx, float ny, float nz) {
        float[] o = new float[4];
        for (int k = 0; k < 4; k++) {
            o[k] = inv[k] * nx + inv[4 + k] * ny + inv[8 + k] * nz + inv[12 + k];
        }
        return new float[]{o[0] / o[3], o[1] / o[3], o[2] / o[3]};
    }

    static float rayBox(float ox, float oy, float oz, float dx, float dy, float dz,
                        float x0, float y0, float z0, float x1, float y1, float z1) {
        float tmin = -Float.MAX_VALUE, tmax = Float.MAX_VALUE;
        float[] o = {ox, oy, oz}, d = {dx, dy, dz}, lo = {x0, y0, z0}, hi = {x1, y1, z1};
        for (int k = 0; k < 3; k++) {
            if (Math.abs(d[k]) < 1e-6f) {
                if (o[k] < lo[k] || o[k] > hi[k]) return -1f;
            } else {
                float t1 = (lo[k] - o[k]) / d[k], t2 = (hi[k] - o[k]) / d[k];
                if (t1 > t2) { float tt = t1; t1 = t2; t2 = tt; }
                tmin = Math.max(tmin, t1);
                tmax = Math.min(tmax, t2);
                if (tmin > tmax) return -1f;
            }
        }
        return tmin > 0 ? tmin : -1f;
    }

    boolean isTargeted(int id) {
        for (int i = 0; i < count; i++) {
            if (type[i] == T_INTERCEPT && target[i] == id && (flags[i] & F_DEAD) == 0) return true;
        }
        return false;
    }

    // ================================================================= alta de partículas

    int add() {
        if (count >= MAX) return -1;
        int i = count++;
        age[i] = 0f; emitAcc[i] = 0f; timer[i] = 0f; flags[i] = 0; emit[i] = E_NONE; emitRate[i] = 0f;
        bright[i] = 1f; pteam[i] = 0; weapon[i] = 0; target[i] = 0; pid[i] = nextId++;
        drag[i] = 0f; grav[i] = 0f;
        return i;
    }

    void remove(int i) {
        int l = --count;
        if (i == l) return;
        x[i] = x[l]; y[i] = y[l]; z[i] = z[l]; px[i] = px[l]; py[i] = py[l]; pz[i] = pz[l];
        vx[i] = vx[l]; vy[i] = vy[l]; vz[i] = vz[l];
        life[i] = life[l]; maxLife[i] = maxLife[l]; age[i] = age[l];
        r[i] = r[l]; g[i] = g[l]; b[i] = b[l];
        size[i] = size[l]; drag[i] = drag[l]; grav[i] = grav[l];
        emitRate[i] = emitRate[l]; emitAcc[i] = emitAcc[l]; bright[i] = bright[l]; timer[i] = timer[l];
        type[i] = type[l]; flags[i] = flags[l]; emit[i] = emit[l]; pteam[i] = pteam[l];
        weapon[i] = weapon[l]; pid[i] = pid[l]; target[i] = target[l];
    }

    int find(int id) {
        for (int i = 0; i < count; i++) if (pid[i] == id) return i;
        return -1;
    }

    int star(float x0, float y0, float z0, float vx0, float vy0, float vz0, float lf, float[] c, float sz, float dr, float gr) {
        int i = add();
        if (i < 0) return -1;
        type[i] = T_STAR;
        x[i] = px[i] = x0; y[i] = py[i] = y0; z[i] = pz[i] = z0;
        vx[i] = vx0; vy[i] = vy0; vz[i] = vz0;
        life[i] = maxLife[i] = lf;
        r[i] = c[0]; g[i] = c[1]; b[i] = c[2];
        size[i] = sz; drag[i] = dr; grav[i] = gr;
        return i;
    }

    void spark(int src, float lf) {
        if (count >= MAX - 3000) return;
        int i = add();
        type[i] = T_SPARK;
        x[i] = px[i] = x[src] + (rnd.nextFloat() - 0.5f) * 0.15f;
        y[i] = py[i] = y[src] + (rnd.nextFloat() - 0.5f) * 0.15f;
        z[i] = pz[i] = z[src] + (rnd.nextFloat() - 0.5f) * 0.15f;
        float rv = 1.6f;
        vx[i] = vx[src] * 0.2f + (rnd.nextFloat() - 0.5f) * rv;
        vy[i] = vy[src] * 0.2f + (rnd.nextFloat() - 0.5f) * rv;
        vz[i] = vz[src] * 0.2f + (rnd.nextFloat() - 0.5f) * rv;
        drag[i] = 2.5f;
        grav[i] = 3.5f;
        life[i] = maxLife[i] = lf * (0.6f + rnd.nextFloat() * 0.8f);
        size[i] = 0.16f * (0.8f + rnd.nextFloat() * 0.5f);
        int e = emit[src];
        if (e == E_GOLD) {
            r[i] = 1f; g[i] = 0.55f + rnd.nextFloat() * 0.15f; b[i] = 0.18f;
        } else if (e == E_SILVER) {
            r[i] = 0.92f; g[i] = 0.94f; b[i] = 1f;
        } else if (e == E_TEAM) {
            if (pteam[src] == PLAYER) { r[i] = 0.55f; g[i] = 0.8f; b[i] = 1f; }
            else { r[i] = 1f; g[i] = 0.3f; b[i] = 0.15f; }
            size[i] *= 1.3f;
        } else {
            r[i] = r[src] * 0.9f + 0.1f; g[i] = g[src] * 0.9f + 0.1f; b[i] = b[src] * 0.9f + 0.1f;
            life[i] *= 0.7f;
        }
        bright[i] = 0.8f;
    }

    void flame(float x0, float y0, float z0, float strength) {
        if (count >= MAX - 1500) return;
        int i = add();
        type[i] = T_FLAME;
        x[i] = px[i] = x0; y[i] = py[i] = y0; z[i] = pz[i] = z0;
        vx[i] = (rnd.nextFloat() - 0.5f) * 0.6f + wind * 0.3f;
        vy[i] = 1.6f + rnd.nextFloat() * 2.2f * strength;
        vz[i] = (rnd.nextFloat() - 0.5f) * 0.6f;
        drag[i] = 1.2f;
        grav[i] = -1.5f;           // las llamas suben
        life[i] = maxLife[i] = 0.45f + rnd.nextFloat() * 0.6f;
        size[i] = (0.8f + rnd.nextFloat() * 1.0f) * (0.6f + strength * 0.6f);
        r[i] = 1f; g[i] = 0.75f; b[i] = 0.35f;
        bright[i] = 0.55f + rnd.nextFloat() * 0.3f;
    }

    void flash(float x0, float y0, float z0, float sz, float cr, float cg, float cb, float lf, float br) {
        int i = add();
        if (i < 0) return;
        type[i] = T_FLASH;
        x[i] = px[i] = x0; y[i] = py[i] = y0; z[i] = pz[i] = z0;
        vx[i] = vy[i] = vz[i] = 0f;
        life[i] = maxLife[i] = lf;
        size[i] = sz;
        r[i] = cr; g[i] = cg; b[i] = cb;
        bright[i] = br;
    }

    void addSmoke(float x0, float y0, float z0, float vx0, float vy0, float vz0, float sz0, float grow, float lf,
                  float alpha, float dust) {
        if (sCount >= SMAX) removeSmoke(0);
        int i = sCount++;
        sx[i] = x0; sy[i] = y0; sz[i] = z0;
        svx[i] = vx0; svy[i] = vy0; svz[i] = vz0;
        sLife[i] = 0f; sMax[i] = lf; sSize[i] = sz0; sGrow[i] = grow; sAlpha[i] = alpha;
        sAng[i] = rnd.nextFloat() * 6.283f; sSpin[i] = (rnd.nextFloat() - 0.5f) * 0.4f; sDust[i] = dust;
    }

    void removeSmoke(int i) {
        int l = --sCount;
        if (i == l) return;
        sx[i] = sx[l]; sy[i] = sy[l]; sz[i] = sz[l]; svx[i] = svx[l]; svy[i] = svy[l]; svz[i] = svz[l];
        sLife[i] = sLife[l]; sMax[i] = sMax[l]; sSize[i] = sSize[l]; sGrow[i] = sGrow[l];
        sAlpha[i] = sAlpha[l]; sAng[i] = sAng[l]; sSpin[i] = sSpin[l]; sDust[i] = sDust[l];
    }

    void addLight(float x0, float y0, float z0, float inten, float cr, float cg, float cb, float tau) {
        int i;
        if (lCount < LMAX) i = lCount++;
        else {
            i = 0;
            for (int k = 1; k < LMAX; k++) if (li[k] < li[i]) i = k;
            if (li[i] > inten) return;
        }
        lx[i] = x0; ly[i] = y0; lz[i] = z0; li[i] = inten;
        lr[i] = cr; lg[i] = cg; lb[i] = cb; ltau[i] = tau;
    }

    float distCam(float x0, float y0, float z0) {
        float dx = x0 - eye[0], dy = y0 - eye[1], dz = z0 - eye[2];
        return (float) Math.sqrt(dx * dx + dy * dy + dz * dz);
    }

    float pan(float x0) {
        return Math.max(-1f, Math.min(1f, x0 / 35f));
    }

    void queueSound(float delay, int which, float vol, float pn, float rate) {
        if (delay <= 0.001f) {
            sound.play(which, vol, pn, rate);
            return;
        }
        if (qCount >= QMAX) return;
        qTime[qCount] = time + delay; qWhich[qCount] = which; qVol[qCount] = vol;
        qPan[qCount] = pn; qRate[qCount] = rate;
        qCount++;
    }

    /** Sonido con volumen y retardo según la distancia a la cámara (la luz llega antes). */
    void soundAt(int which, float x0, float y0, float z0, float vol, float rate) {
        float d = distCam(x0, y0, z0);
        float v = vol * Math.min(1f, 40f / (d + 10f));
        queueSound(d / 300f, which, v, pan(x0), rate);
    }

    // ================================================================= cohetes

    int padFor(int t, float tx) {
        int best = pads[t][0];
        float bd = Float.MAX_VALUE;
        for (int k = 0; k < 3; k++) {
            int i = pads[t][k];
            float cx = (bx0[i] + bx1[i]) * 0.5f;
            float d = Math.abs(cx - tx) + rnd.nextFloat() * 6f;
            if (d < bd) { bd = d; best = i; }
        }
        return best;
    }

    void launchRocket(int t, int w, float tx, float ty, float tz, float spread) {
        int p = padFor(t, tx);
        float x0 = (bx0[p] + bx1[p]) * 0.5f, z0 = (bz0[p] + bz1[p]) * 0.5f, y0 = bh[p] + 0.3f;
        tx += (float) rnd.nextGaussian() * spread;
        tz += (float) rnd.nextGaussian() * spread;
        if (w == W_SHOW) {
            tx = x0 + (rnd.nextFloat() - 0.5f) * 30f;
            tz = z0 + (t == PLAYER ? 10f : -10f) + (rnd.nextFloat() - 0.5f) * 10f;
            ty = 18f + rnd.nextFloat() * 12f;
        }
        float dx = tx - x0, dz = tz - z0;
        float dist = (float) Math.sqrt(dx * dx + dz * dz);
        float T = 1.9f + dist / 55f;
        if (w == W_MEGA) T *= 1.15f;
        if (w == W_SHOW) T = 1.6f + rnd.nextFloat() * 0.6f;
        int i = add();
        if (i < 0) return;
        type[i] = T_ROCKET;
        x[i] = px[i] = x0; y[i] = py[i] = y0; z[i] = pz[i] = z0;
        vx[i] = dx / T;
        vz[i] = dz / T;
        vy[i] = (ty - y0 + 0.5f * G * T * T) / T;
        grav[i] = G;
        life[i] = maxLife[i] = T;
        pteam[i] = t;
        weapon[i] = w;
        size[i] = w == W_MEGA ? 0.75f : 0.5f;
        if (t == PLAYER) { r[i] = 0.7f; g[i] = 0.85f; b[i] = 1f; } else { r[i] = 1f; g[i] = 0.45f; b[i] = 0.2f; }
        emit[i] = w == W_SHOW ? E_GOLD : E_TEAM;
        emitRate[i] = w == W_MEGA ? 140f : 90f;
        if (w == W_CLUSTER) { flags[i] |= F_SPLIT; timer[i] = T * 0.55f; }

        soundAt(SoundEngine.LAUNCH, x0, y0, z0, 0.9f, 0.85f + rnd.nextFloat() * 0.3f);
        if (t == ENEMY && w != W_SHOW) {
            // aviso: silbido del cohete enemigo que se acerca
            queueSound(T * 0.35f, SoundEngine.WHISTLE, 0.35f, pan(tx), 0.75f + rnd.nextFloat() * 0.2f);
        }
        addLight(x0, y0 + 1f, z0, 0.8f, 1f, 0.6f, 0.3f, 0.1f);
        for (int k = 0; k < 3; k++) {
            addSmoke(x0, y0, z0, (rnd.nextFloat() - 0.5f) * 1.5f, 0.8f, (rnd.nextFloat() - 0.5f) * 1.5f,
                    1.2f, 1.5f, 3f + rnd.nextFloat() * 2f, 0.22f, 0f);
        }
    }

    void launchInterceptor(int t, int tgtIdx) {
        int p = padFor(t, x[tgtIdx]);
        float x0 = (bx0[p] + bx1[p]) * 0.5f, z0 = (bz0[p] + bz1[p]) * 0.5f, y0 = bh[p] + 0.3f;
        int i = add();
        if (i < 0) return;
        type[i] = T_INTERCEPT;
        x[i] = px[i] = x0; y[i] = py[i] = y0; z[i] = pz[i] = z0;
        vx[i] = 0f; vy[i] = 30f; vz[i] = t == PLAYER ? -8f : 8f;
        life[i] = maxLife[i] = 4f;
        pteam[i] = t;
        weapon[i] = W_INTERCEPT;
        target[i] = pid[tgtIdx];
        size[i] = 0.45f;
        r[i] = 1f; g[i] = 1f; b[i] = 1f;
        emit[i] = E_SILVER;
        emitRate[i] = 120f;
        soundAt(SoundEngine.LAUNCH, x0, y0, z0, 0.7f, 1.4f);
    }

    // ================================================================= explosiones y daño

    void explode(float ex, float ey, float ez, int t, int w, float ivx, float ivy, float ivz) {
        float[] c1 = Fireworks.PALETTE[rnd.nextInt(Fireworks.PALETTE.length)];
        if (t == ENEMY && w != W_SHOW) {
            int[] warm = {Fireworks.C_RED, Fireworks.C_ORANGE, Fireworks.C_GOLD, Fireworks.C_PURPLE};
            c1 = Fireworks.PALETTE[warm[rnd.nextInt(warm.length)]];
        }
        float radius, maxDmg, fireAdd;
        int boom;
        float lightI;
        switch (w) {
            case W_MEGA: {
                float[] kc = {1f, 0.7f, 0.38f};
                sphere(ex, ey, ez, ivx, ivy, ivz, 180, 11f, 1.9f, 2.8f, 3.6f, kc, 0.42f, 3.2f, E_GOLD, 55f, F_FLICKER);
                sphere(ex, ey, ez, ivx, ivy, ivz, 60, 5f, 2.4f, 1.6f, 2.0f, c1, 0.45f, 3f, E_NONE, 0f, 0);
                radius = 11f; maxDmg = 75f; fireAdd = 0.5f; boom = SoundEngine.SALUTE; lightI = 4f;
                flash(ex, ey, ez, 14f, 1f, 0.95f, 0.85f, 0.15f, 0.9f);
                shakeAt(ex, ey, ez, 1.6f);
                break;
            }
            case W_BOMBLET: {
                sphere(ex, ey, ez, ivx * 0.2f, 0, ivz * 0.2f, 30, 3.5f, 2.6f, 0.8f, 1.1f, c1, 0.35f, 3f, E_GOLD, 30f, F_CRACKLE);
                radius = 5f; maxDmg = 24f; fireAdd = 0.25f; boom = SoundEngine.BOOM_SMALL; lightI = 1.6f;
                shakeAt(ex, ey, ez, 0.4f);
                break;
            }
            case W_FIRE: {
                float[] wc = {0.95f, 0.58f, 0.22f};
                sphere(ex, ey, ez, ivx, ivy, ivz, 120, 8f, 1.6f, 3.0f, 3.8f, wc, 0.32f, 4f, E_GOLD, 50f, F_FLICKER);
                radius = 9f; maxDmg = 10f; fireAdd = 0.8f; boom = SoundEngine.BOOM_MED; lightI = 2.6f;
                queueSound(0.5f, SoundEngine.FIZZ, 0.3f, pan(ex), 0.8f);
                shakeAt(ex, ey, ez, 0.6f);
                break;
            }
            case W_INTERCEPT: {
                sphere(ex, ey, ez, 0, 0, 0, 45, 4f, 2.6f, 0.7f, 1.0f, Fireworks.PALETTE[Fireworks.C_WHITE], 0.35f, 3f,
                        E_SILVER, 25f, F_CRACKLE);
                flash(ex, ey, ez, 6f, 1f, 1f, 1f, 0.1f, 0.8f);
                radius = 0f; maxDmg = 0f; fireAdd = 0f; boom = SoundEngine.BOOM_SMALL; lightI = 1.8f;
                soundAt(SoundEngine.CRACKLE, ex, ey, ez, 0.5f, 1.2f);
                break;
            }
            case W_SHOW: {
                int kind = rnd.nextInt(3);
                if (kind == 0) sphere(ex, ey, ez, 0, 0, 0, 120, 8f, 2.6f, 1.6f, 2.2f, c1, 0.4f, 3f, E_OWN, 20f, 0);
                else if (kind == 1) sphere(ex, ey, ez, 0, 0, 0, 110, 8f, 2.4f, 1.8f, 2.4f, c1, 0.4f, 3f, E_GOLD, 40f, 0);
                else sphere(ex, ey, ez, 0, 0, 0, 140, 9f, 1.8f, 3f, 3.6f, new float[]{1f, 0.7f, 0.38f}, 0.35f, 3.4f,
                            E_GOLD, 50f, F_FLICKER);
                radius = 0f; maxDmg = 0f; fireAdd = 0f; boom = SoundEngine.BOOM_MED; lightI = 2.2f;
                break;
            }
            default: { // W_ROCKET
                boolean gold = rnd.nextFloat() < 0.3f;
                sphere(ex, ey, ez, ivx, ivy, ivz, 80, 6.5f, 2.6f, 1.4f, 2.0f, c1, 0.42f, 3f,
                        gold ? E_GOLD : E_OWN, gold ? 40f : 20f, 0);
                radius = 6.5f; maxDmg = 34f; fireAdd = 0.3f; boom = SoundEngine.BOOM_MED; lightI = 2.4f;
                shakeAt(ex, ey, ez, 0.7f);
                break;
            }
        }
        if (w != W_INTERCEPT && w != W_MEGA) flash(ex, ey, ez, radius > 0 ? radius * 0.9f : 6f,
                c1[0] * 0.5f + 0.5f, c1[1] * 0.5f + 0.5f, c1[2] * 0.5f + 0.5f, 0.1f, 0.55f);
        addLight(ex, ey, ez, lightI, c1[0] * 0.6f + 0.4f, c1[1] * 0.6f + 0.4f, c1[2] * 0.6f + 0.4f, 0.5f);
        soundAt(boom, ex, ey, ez, 1f, 0.85f + rnd.nextFloat() * 0.3f);
        int puffs = radius > 0 ? 6 + (int) radius : 4;
        for (int k = 0; k < puffs; k++) {
            float a = rnd.nextFloat() * 6.283f, rr = rnd.nextFloat() * Math.max(2f, radius) * 0.5f;
            addSmoke(ex + (float) Math.cos(a) * rr, ey + (rnd.nextFloat() - 0.5f) * 2f, ez + (float) Math.sin(a) * rr,
                    (float) Math.cos(a) * 0.6f, 0.3f, (float) Math.sin(a) * 0.6f, 2f, 1.6f, 6f + rnd.nextFloat() * 4f,
                    0.16f, 0f);
        }
        if (maxDmg > 0) damage(ex, ey, ez, 1 - t, radius, maxDmg, fireAdd);
    }

    void damage(float ex, float ey, float ez, int defender, float radius, float maxDmg, float fireAdd) {
        boolean hit = false;
        for (int i = 0; i < bCount; i++) {
            if (team[i] != defender || pad[i] || down[i] || collapsing[i]) continue;
            float top = bh[i] * hs[i];
            float cx = Math.max(bx0[i], Math.min(bx1[i], ex));
            float cy = Math.max(0f, Math.min(top, ey));
            float cz = Math.max(bz0[i], Math.min(bz1[i], ez));
            float d = (float) Math.sqrt((cx - ex) * (cx - ex) + (cy - ey) * (cy - ey) + (cz - ez) * (cz - ez));
            if (d >= radius) continue;
            float k = 1f - d / radius;
            health[i] -= maxDmg * k * (0.8f + rnd.nextFloat() * 0.4f);
            fire[i] = Math.min(1f, fire[i] + fireAdd * k * (0.7f + rnd.nextFloat() * 0.6f));
            charr[i] = Math.min(1f, charr[i] + 0.15f * k);
            hit = true;
        }
        if (hit && defender == PLAYER && haptics != null) haptics.buzz(35, 200);
    }

    void shakeAt(float ex, float ey, float ez, float amount) {
        float d = distCam(ex, ey, ez);
        shake = Math.min(1.5f, shake + amount * Math.min(1f, 30f / d));
    }

    void sphere(float ex, float ey, float ez, float ivx, float ivy, float ivz, int n, float R, float d,
                float lifeMin, float lifeMax, float[] c, float sz, float gr, int emitKind, float rate, int fl) {
        float golden = 2.39996323f;
        float speed = R * d;
        // orientación aleatoria de la esfera
        float rot = rnd.nextFloat() * 6.283f;
        for (int k = 0; k < n; k++) {
            float u = 1f - 2f * (k + 0.5f) / n;
            float rr = (float) Math.sqrt(1f - u * u);
            float th = k * golden + rot;
            float dx = (float) Math.cos(th) * rr, dy = u, dz = (float) Math.sin(th) * rr;
            float sp = speed * (0.94f + rnd.nextFloat() * 0.12f);
            float lf = lifeMin + rnd.nextFloat() * (lifeMax - lifeMin);
            int i = star(ex, ey, ez, ivx + dx * sp, ivy + dy * sp, ivz + dz * sp, lf, c, sz, d, gr);
            if (i < 0) return;
            emit[i] = emitKind;
            emitRate[i] = rate * (0.8f + rnd.nextFloat() * 0.4f);
            flags[i] = fl;
        }
    }

    void crackle(int i) {
        int n = 3 + rnd.nextInt(4);
        for (int k = 0; k < n; k++) {
            int j = add();
            if (j < 0) return;
            type[j] = T_CRACK;
            x[j] = px[j] = x[i] + (rnd.nextFloat() - 0.5f) * 1.2f;
            y[j] = py[j] = y[i] + (rnd.nextFloat() - 0.5f) * 1.2f;
            z[j] = pz[j] = z[i] + (rnd.nextFloat() - 0.5f) * 1.2f;
            vx[j] = vx[i] * 0.3f; vy[j] = vy[i] * 0.3f; vz[j] = vz[i] * 0.3f;
            drag[j] = 2f; grav[j] = 1.5f;
            timer[j] = rnd.nextFloat() * 0.4f;
            life[j] = maxLife[j] = 0.04f + rnd.nextFloat() * 0.05f;
            size[j] = 0.5f + rnd.nextFloat() * 0.4f;
            r[j] = 1f; g[j] = 0.92f; b[j] = 0.75f;
            bright[j] = 1.3f;
        }
    }

    void splitCluster(int i) {
        float ex = x[i], ey = y[i], ez = z[i];
        int t = pteam[i];
        for (int k = 0; k < 6; k++) {
            int j = add();
            if (j < 0) return;
            type[j] = T_BOMBLET;
            x[j] = px[j] = ex; y[j] = py[j] = ey; z[j] = pz[j] = ez;
            float a = k * 1.047f + rnd.nextFloat() * 0.5f;
            float sp = 3f + rnd.nextFloat() * 3f;
            vx[j] = vx[i] + (float) Math.cos(a) * sp;
            vz[j] = vz[i] + (float) Math.sin(a) * sp;
            vy[j] = vy[i] + (rnd.nextFloat() - 0.3f) * 3f;
            grav[j] = G;
            life[j] = maxLife[j] = 10f;
            pteam[j] = t;
            weapon[j] = W_BOMBLET;
            size[j] = 0.4f;
            r[j] = 1f; g[j] = 0.8f; b[j] = 0.4f;
            emit[j] = E_GOLD;
            emitRate[j] = 50f;
        }
        flash(ex, ey, ez, 3f, 1f, 0.9f, 0.7f, 0.08f, 0.8f);
        soundAt(SoundEngine.CRACKLE, ex, ey, ez, 0.3f, 1.5f);
    }

    /** ¿El punto está dentro de un edificio en pie? Devuelve el índice o -1. */
    int insideBuilding(float qx, float qy, float qz) {
        if (qy > 30f) return -1;
        for (int i = 0; i < bCount; i++) {
            if (down[i]) continue;
            if (qx >= bx0[i] && qx <= bx1[i] && qz >= bz0[i] && qz <= bz1[i] && qy <= bh[i] * hs[i]) return i;
        }
        return -1;
    }

    // ================================================================= paso de simulación

    public void update(float dt) {
        time += dt;
        updateCamera(dt);

        for (int k = 0; k < qCount; ) {
            if (qTime[k] <= time) {
                sound.play(qWhich[k], qVol[k], qPan[k], qRate[k]);
                int l = --qCount;
                qTime[k] = qTime[l]; qWhich[k] = qWhich[l]; qVol[k] = qVol[l]; qPan[k] = qPan[l]; qRate[k] = qRate[l];
            } else k++;
        }

        if (state == S_PLAY) {
            for (int t = 0; t < 2; t++) {
                for (int w = 0; w < WCOUNT; w++) cooldown[t][w] = Math.max(0f, cooldown[t][w] - dt);
                charges[t] = Math.min(3f, charges[t] + dt / 4f);
            }
            updateAI(dt);
        } else if (state == S_WON || state == S_LOST) {
            celebrateTimer -= dt;
            if (celebrateTimer <= 0f) {
                launchRocket(state == S_WON ? PLAYER : ENEMY, W_SHOW, 0, 0, 0, 0f);
                celebrateTimer = 0.35f + rnd.nextFloat() * 0.5f;
            }
        } else {
            // Menú: un espectáculo tranquilo de fondo
            menuTimer -= dt;
            if (menuTimer <= 0f) {
                launchRocket(rnd.nextBoolean() ? PLAYER : ENEMY, W_SHOW, 0, 0, 0, 0f);
                menuTimer = 0.8f + rnd.nextFloat() * 1.2f;
            }
        }

        updateBuildings(dt);
        updateParticles(dt);
        updateSmoke(dt);

        for (int k = 0; k < lCount; ) {
            li[k] *= (float) Math.exp(-dt / ltau[k]);
            if (li[k] < 0.02f) {
                int l = --lCount;
                lx[k] = lx[l]; ly[k] = ly[l]; lz[k] = lz[l]; li[k] = li[l];
                lr[k] = lr[l]; lg[k] = lg[l]; lb[k] = lb[l]; ltau[k] = ltau[l];
            } else k++;
        }
        shake *= (float) Math.exp(-dt * 4f);

        if (state == S_PLAY) {
            float pi = integrity(PLAYER), ei = integrity(ENEMY);
            if (ei <= LOSE_AT || pi <= LOSE_AT) {
                state = ei <= LOSE_AT ? S_WON : S_LOST;
                celebrateTimer = 1.5f;
                if (listener != null) listener.onState(state);
            }
        }

        statsTimer -= dt;
        if (statsTimer <= 0f && listener != null) {
            statsTimer = 0.12f;
            for (int w = 0; w < WCOUNT; w++) cdFrac[w] = cooldown[PLAYER][w] / W_COOLDOWN[w];
            listener.onStats(integrity(PLAYER), integrity(ENEMY), cdFrac, (int) charges[PLAYER], selectedWeapon);
        }
    }

    void updateCamera(float dt) {
        float sway = (float) Math.sin(time * 0.13f) * 2.5f;
        eye[0] = sway;
        eye[1] = 40f;
        eye[2] = 66f;
        look[0] = sway * 0.3f;
        look[1] = 0f;
        look[2] = -36f;
        if (shake > 0.01f) {
            eye[0] += (rnd.nextFloat() - 0.5f) * shake * 0.8f;
            eye[1] += (rnd.nextFloat() - 0.5f) * shake * 0.8f;
            look[1] += (rnd.nextFloat() - 0.5f) * shake * 0.5f;
        }
    }

    void updateAI(float dt) {
        float interval = difficulty == 0 ? 3.4f : (difficulty == 1 ? 2.3f : 1.5f);
        float spread = difficulty == 0 ? 5.5f : (difficulty == 1 ? 3.2f : 1.8f);
        float interceptP = difficulty == 0 ? 0.12f : (difficulty == 1 ? 0.3f : 0.5f);

        aiTimer -= dt;
        if (aiTimer <= 0f) {
            aiTimer = interval * (0.7f + rnd.nextFloat() * 0.6f);
            int w = W_ROCKET;
            float roll = rnd.nextFloat();
            if (roll < 0.22f && cooldown[ENEMY][W_MEGA] <= 0) w = W_MEGA;
            else if (roll < 0.42f && cooldown[ENEMY][W_CLUSTER] <= 0) w = W_CLUSTER;
            else if (roll < 0.60f && cooldown[ENEMY][W_FIRE] <= 0) w = W_FIRE;
            // Elige un edificio en pie (prefiere los más altos y sanos)
            int bestB = -1;
            float bestScore = -1;
            for (int k = 0; k < 12; k++) {
                int i = rnd.nextInt(bCount);
                if (team[i] != PLAYER || pad[i] || down[i] || collapsing[i]) continue;
                float s = bh[i] * 0.3f + health[i] * 0.02f + rnd.nextFloat() * 2f;
                if (s > bestScore) { bestScore = s; bestB = i; }
            }
            if (bestB >= 0) {
                cooldown[ENEMY][w] = W_COOLDOWN[w] * 1.3f;
                float tx = (bx0[bestB] + bx1[bestB]) * 0.5f, tz = (bz0[bestB] + bz1[bestB]) * 0.5f;
                float ty = bh[bestB] * hs[bestB] * (0.4f + rnd.nextFloat() * 0.5f);
                if (w == W_CLUSTER) ty += 6f;
                if (w == W_FIRE || w == W_MEGA) ty += 2f;
                launchRocket(ENEMY, w, tx, ty, tz, spread);
            }
        }

        // Intercepta algunos cohetes del jugador a mitad de vuelo
        for (int i = 0; i < count; i++) {
            if (type[i] != T_ROCKET || pteam[i] != PLAYER || (flags[i] & F_CHECKED) != 0) continue;
            if (life[i] < maxLife[i] * 0.5f) {
                flags[i] |= F_CHECKED;
                if (charges[ENEMY] >= 1f && rnd.nextFloat() < interceptP) {
                    charges[ENEMY] -= 1f;
                    launchInterceptor(ENEMY, i);
                }
            }
        }
    }

    void updateBuildings(float dt) {
        for (int i = 0; i < bCount; i++) {
            if (pad[i]) continue;
            float w = bx1[i] - bx0[i], d = bz1[i] - bz0[i];
            float cx = (bx0[i] + bx1[i]) * 0.5f, cz = (bz0[i] + bz1[i]) * 0.5f;
            if (collapsing[i]) {
                collapseT[i] += dt;
                float k = Math.min(1f, collapseT[i] / 2.2f);
                float e = k * k * (3 - 2 * k);
                hs[i] = 1f - e * 0.86f;
                float top = bh[i] * hs[i];
                if (rnd.nextFloat() < dt * 25f) {
                    addSmoke(bx0[i] + rnd.nextFloat() * w, rnd.nextFloat() * Math.max(1f, top), bz0[i] + rnd.nextFloat() * d,
                            (rnd.nextFloat() - 0.5f) * 3f, 1f + rnd.nextFloat(), (rnd.nextFloat() - 0.5f) * 3f,
                            2.5f, 2.5f, 7f + rnd.nextFloat() * 5f, 0.28f, 1f);
                }
                if (k >= 1f) {
                    collapsing[i] = false;
                    down[i] = true;
                }
            } else if (!down[i] && health[i] <= 0f) {
                collapsing[i] = true;
                collapseT[i] = 0f;
                charr[i] = Math.max(charr[i], 0.7f);
                float top = bh[i];
                for (int k = 0; k < 14; k++) {
                    addSmoke(cx + (rnd.nextFloat() - 0.5f) * w * 1.5f, rnd.nextFloat() * top * 0.6f,
                            cz + (rnd.nextFloat() - 0.5f) * d * 1.5f,
                            (rnd.nextFloat() - 0.5f) * 4f, 0.5f + rnd.nextFloat(), (rnd.nextFloat() - 0.5f) * 4f,
                            3f, 3f, 9f + rnd.nextFloat() * 5f, 0.3f, 1f);
                }
                addLight(cx, top * 0.5f, cz, 1.5f, 1f, 0.55f, 0.2f, 0.8f);
                soundAt(SoundEngine.BOOM_BIG, cx, top * 0.5f, cz, 1f, 0.6f);
                shakeAt(cx, top * 0.5f, cz, 1.2f);
                if (team[i] == ENEMY) {
                    destroyedEnemy++;
                    if (listener != null && state == S_PLAY) listener.onMessage("¡Edificio enemigo derribado!");
                } else {
                    destroyedPlayer++;
                    if (haptics != null) haptics.buzz(60, 255);
                }
            }

            // Fuego
            if (fire[i] > 0f) {
                float f = fire[i];
                boolean standing = !down[i] && !collapsing[i];
                if (standing) {
                    health[i] -= f * 3.5f * dt;
                    fire[i] += (f > 0.3f ? 0.02f : -0.06f) * dt;
                } else {
                    fire[i] -= 0.06f * dt;
                }
                charr[i] = Math.min(1f, charr[i] + f * dt * 0.12f);
                fire[i] = Math.max(0f, Math.min(1f, fire[i]));

                // Propagación a edificios vecinos
                if (standing && f > 0.45f && rnd.nextFloat() < f * dt * 0.15f) {
                    for (int j = 0; j < bCount; j++) {
                        if (j == i || team[j] != team[i] || pad[j] || down[j]) continue;
                        float gx = Math.max(0f, Math.max(bx0[j] - bx1[i], bx0[i] - bx1[j]));
                        float gz = Math.max(0f, Math.max(bz0[j] - bz1[i], bz0[i] - bz1[j]));
                        if (gx + gz < 2.0f && rnd.nextFloat() < 0.3f) {
                            fire[j] = Math.min(1f, fire[j] + 0.22f);
                        }
                    }
                }

                // Llamas y humo
                float top = bh[i] * hs[i];
                flameAcc[i] += f * (6f + (w + d) * 2.5f) * dt;
                while (flameAcc[i] >= 1f) {
                    flameAcc[i] -= 1f;
                    float fx, fy, fz;
                    if (rnd.nextFloat() < 0.55f) {          // azotea
                        fx = bx0[i] + rnd.nextFloat() * w; fy = top; fz = bz0[i] + rnd.nextFloat() * d;
                    } else {                                 // ventanas en llamas
                        fy = top * (0.35f + rnd.nextFloat() * 0.65f);
                        if (rnd.nextBoolean()) { fx = rnd.nextBoolean() ? bx0[i] : bx1[i]; fz = bz0[i] + rnd.nextFloat() * d; }
                        else { fz = rnd.nextBoolean() ? bz0[i] : bz1[i]; fx = bx0[i] + rnd.nextFloat() * w; }
                    }
                    flame(fx, fy, fz, f);
                }
                if (rnd.nextFloat() < f * dt * 3f) {
                    addSmoke(cx + (rnd.nextFloat() - 0.5f) * w, top + 0.5f, cz + (rnd.nextFloat() - 0.5f) * d,
                            wind * 0.5f, 1.4f + rnd.nextFloat(), 0f, 1.8f, 2.2f, 8f + rnd.nextFloat() * 4f,
                            0.22f * Math.min(1f, f + 0.3f), 0f);
                }
            }
        }
    }

    void updateParticles(float dt) {
        float load = (float) count / MAX;
        float emitScale = load < 0.4f ? 1f : Math.max(0.15f, 1f - (load - 0.4f) / 0.45f);
        for (int i = 0; i < count; ) {
            if ((flags[i] & F_DEAD) != 0) {
                remove(i);
                continue;
            }
            int t = type[i];
            if (t == T_CRACK && timer[i] > 0) {
                timer[i] -= dt;
                x[i] += vx[i] * dt; y[i] += vy[i] * dt; z[i] += vz[i] * dt;
                px[i] = x[i]; py[i] = y[i]; pz[i] = z[i];
                i++;
                continue;
            }
            age[i] += dt;
            life[i] -= dt;
            px[i] = x[i]; py[i] = y[i]; pz[i] = z[i];

            if (t == T_INTERCEPT) {
                int j = find(target[i]);
                if (j >= 0) {
                    float dx = x[j] - x[i], dy = y[j] - y[i], dz = z[j] - z[i];
                    float dist = (float) Math.sqrt(dx * dx + dy * dy + dz * dz);
                    if (dist < 2.8f) {
                        explode((x[i] + x[j]) * 0.5f, (y[i] + y[j]) * 0.5f, (z[i] + z[j]) * 0.5f, pteam[i],
                                W_INTERCEPT, 0, 0, 0);
                        flags[j] |= F_DEAD;
                        if (listener != null && pteam[i] == PLAYER) listener.onMessage("¡Interceptado!");
                        if (listener != null && pteam[i] == ENEMY && state == S_PLAY)
                            listener.onMessage("El enemigo interceptó tu cohete");
                        remove(i);
                        continue;
                    }
                    float lead = Math.min(0.5f, dist / 50f);
                    float ax = dx + vx[j] * lead, ay = dy + vy[j] * lead, az = dz + vz[j] * lead;
                    float al = (float) Math.sqrt(ax * ax + ay * ay + az * az) + 1e-4f;
                    float sp = 48f;
                    float k = Math.min(1f, 5f * dt);
                    vx[i] += (ax / al * sp - vx[i]) * k;
                    vy[i] += (ay / al * sp - vy[i]) * k;
                    vz[i] += (az / al * sp - vz[i]) * k;
                } else if (life[i] > 0.3f) {
                    life[i] = 0.3f;
                }
                if (life[i] <= 0f) {
                    explode(x[i], y[i], z[i], pteam[i], W_INTERCEPT, 0, 0, 0);
                    remove(i);
                    continue;
                }
            }

            if (life[i] <= 0f && t != T_INTERCEPT) {
                if (t == T_ROCKET) {
                    explode(x[i], y[i], z[i], pteam[i], weapon[i], vx[i] * 0.15f, 0f, vz[i] * 0.15f);
                } else if (t == T_STAR && (flags[i] & F_CRACKLE) != 0) {
                    crackle(i);
                }
                remove(i);
                continue;
            }

            if (drag[i] > 0f) {
                float k = (float) Math.exp(-drag[i] * dt);
                vx[i] *= k; vy[i] *= k; vz[i] *= k;
            }
            vy[i] -= grav[i] * dt;
            vx[i] += wind * dt * (t == T_SPARK || t == T_FLAME ? 0.5f : 0.1f);
            x[i] += vx[i] * dt;
            y[i] += vy[i] * dt;
            z[i] += vz[i] * dt;

            if (t == T_ROCKET && (flags[i] & F_SPLIT) != 0 && age[i] >= timer[i]) {
                splitCluster(i);
                remove(i);
                continue;
            }
            if ((t == T_ROCKET || t == T_BOMBLET) && weapon[i] != W_SHOW) {
                // Impacto contra el suelo o un edificio antes de tiempo
                boolean hitGround = y[i] <= 0.2f && age[i] > 0.3f;
                int hb = age[i] > 0.4f ? insideBuilding(x[i], y[i], z[i]) : -1;
                if (hb >= 0 && team[hb] == pteam[i] && pad[hb]) hb = -1;
                if (hitGround || hb >= 0) {
                    if (y[i] < 0.2f) y[i] = 0.2f;
                    explode(x[i], y[i], z[i], pteam[i], weapon[i], 0f, 0f, 0f);
                    if (y[i] < 1f && z[i] > SHORE_ENEMY && z[i] < SHORE_PLAYER) {
                        // cayó al agua: columna de vapor
                        for (int k = 0; k < 5; k++)
                            addSmoke(x[i], 0.5f, z[i], 0, 2f, 0, 1.5f, 2f, 4f, 0.25f, 0.5f);
                    }
                    remove(i);
                    continue;
                }
            }

            if (emitRate[i] > 0f && emit[i] != E_NONE) {
                float lifeFrac = life[i] / maxLife[i];
                if (t != T_STAR || lifeFrac > 0.08f) {
                    emitAcc[i] += emitRate[i] * dt * emitScale;
                    float sl = t == T_ROCKET || t == T_INTERCEPT ? 0.45f : (emit[i] == E_OWN ? 0.4f : 0.75f);
                    while (emitAcc[i] >= 1f) {
                        emitAcc[i] -= 1f;
                        spark(i, sl);
                    }
                }
            }
            if (t == T_ROCKET || t == T_INTERCEPT) {
                timer[i] += t == T_INTERCEPT ? dt : 0f;
                if (rnd.nextFloat() < dt * 14f) {
                    addSmoke(x[i], y[i], z[i], 0, 0, 0, 0.5f, 0.9f, 2.5f + rnd.nextFloat() * 1.5f, 0.12f, 0f);
                }
            }
            i++;
        }
    }

    void updateSmoke(float dt) {
        for (int i = 0; i < sCount; ) {
            sLife[i] += dt;
            if (sLife[i] >= sMax[i]) {
                removeSmoke(i);
                continue;
            }
            float k = (float) Math.exp(-0.8f * dt);
            svx[i] = svx[i] * k + wind * (1f - k);
            svy[i] = svy[i] * k + 0.6f * (1f - k) * (1f - sDust[i] * 0.7f);
            svz[i] = svz[i] * k;
            sx[i] += svx[i] * dt;
            sy[i] += svy[i] * dt;
            sz[i] += svz[i] * dt;
            sSize[i] += sGrow[i] * dt / (1f + sLife[i] * 0.3f);
            sAng[i] += sSpin[i] * dt;
            i++;
        }
    }

    // ================================================================= salida para el render

    /** Partículas: x, y, z, tamaño (m), r, g, b, a. */
    public int fillPoints(float[] out) {
        int n = 0;
        int cap = out.length / 8 - 6;
        for (int i = 0; i < count && n < cap; i++) {
            int t = type[i];
            float a, sz = size[i];
            float cr = r[i], cg = g[i], cb = b[i];
            float f = life[i] / maxLife[i];
            if (t == T_CRACK) {
                if (timer[i] > 0) continue;
                a = bright[i] * f;
            } else if (t == T_FLASH) {
                a = bright[i] * f * f;
                sz *= 0.6f + 0.4f * f;
            } else if (t == T_SPARK) {
                a = bright[i] * f * (0.55f + 0.45f * rnd.nextFloat());
                cg *= 0.6f + 0.4f * f;
                cb *= 0.4f + 0.6f * f;
            } else if (t == T_FLAME) {
                // de blanco-amarillo a naranja y rojo oscuro
                float k = 1f - f;
                cr = 1f;
                cg = 0.85f - 0.6f * k;
                cb = 0.45f - 0.4f * k;
                a = bright[i] * Math.min(1f, age[i] / 0.08f) * f * (0.7f + 0.3f * rnd.nextFloat());
                sz *= 0.7f + 0.6f * k;
            } else if (t == T_ROCKET || t == T_INTERCEPT || t == T_BOMBLET) {
                a = 1f;
            } else {
                a = bright[i] * Math.min(1f, age[i] / 0.05f);
                if (age[i] < 0.12f) {
                    float w = 1f - age[i] / 0.12f;
                    cr += (1 - cr) * w * 0.6f; cg += (1 - cg) * w * 0.6f; cb += (1 - cb) * w * 0.6f;
                }
                if (f < 0.25f) {
                    a *= f / 0.25f;
                    if ((flags[i] & F_FLICKER) != 0) a *= 0.4f + 0.6f * rnd.nextFloat();
                }
            }
            if (a <= 0.01f) continue;
            if (t == T_STAR || t == T_ROCKET || t == T_INTERCEPT || t == T_BOMBLET) {
                float dx = x[i] - px[i], dy = y[i] - py[i], dz = z[i] - pz[i];
                float dist = (float) Math.sqrt(dx * dx + dy * dy + dz * dz);
                int steps = Math.min(4, (int) (dist / (sz * 0.6f + 0.01f)) + 1);
                for (int s = 1; s <= steps; s++) {
                    float k = (float) s / steps;
                    int o = n * 8;
                    out[o] = px[i] + dx * k; out[o + 1] = py[i] + dy * k; out[o + 2] = pz[i] + dz * k;
                    out[o + 3] = sz; out[o + 4] = cr; out[o + 5] = cg; out[o + 6] = cb;
                    out[o + 7] = a * (0.55f + 0.45f * k);
                    n++;
                }
            } else {
                int o = n * 8;
                out[o] = x[i]; out[o + 1] = y[i]; out[o + 2] = z[i];
                out[o + 3] = sz; out[o + 4] = cr; out[o + 5] = cg; out[o + 6] = cb; out[o + 7] = a;
                n++;
            }
        }
        return n;
    }

    /** Humo: x, y, z, tamaño (m), r, g, b, a, ángulo. Iluminado por explosiones y fuegos. */
    public int fillSmoke(float[] out, float[] lightPos, float[] lightCol, int nl) {
        int n = 0;
        int cap = out.length / 9;
        for (int i = 0; i < sCount && n < cap; i++) {
            float f = sLife[i] / sMax[i];
            float a = sAlpha[i] * Math.min(1f, sLife[i] / 0.3f) * (float) Math.pow(1f - f, 1.5);
            if (a < 0.004f) continue;
            float dust = sDust[i];
            float cr = 0.05f + dust * 0.07f, cg = 0.05f + dust * 0.055f, cb = 0.06f + dust * 0.035f;
            for (int k = 0; k < nl; k++) {
                float dx = sx[i] - lightPos[k * 4], dy = sy[i] - lightPos[k * 4 + 1], dz = sz[i] - lightPos[k * 4 + 2];
                float fl = lightPos[k * 4 + 3] * 30f / (30f + dx * dx + dy * dy + dz * dz);
                cr += lightCol[k * 3] * fl * 0.35f;
                cg += lightCol[k * 3 + 1] * fl * 0.35f;
                cb += lightCol[k * 3 + 2] * fl * 0.35f;
            }
            int o = n * 9;
            out[o] = sx[i]; out[o + 1] = sy[i]; out[o + 2] = sz[i];
            out[o + 3] = sSize[i];
            out[o + 4] = Math.min(1f, cr); out[o + 5] = Math.min(1f, cg); out[o + 6] = Math.min(1f, cb);
            out[o + 7] = a;
            out[o + 8] = sAng[i];
            n++;
        }
        return n;
    }

    private final float[] candX = new float[LMAX + BMAX], candY = new float[LMAX + BMAX], candZ = new float[LMAX + BMAX],
            candI = new float[LMAX + BMAX], candR = new float[LMAX + BMAX], candG = new float[LMAX + BMAX],
            candB = new float[LMAX + BMAX];
    private final boolean[] candUsed = new boolean[LMAX + BMAX];

    /** Las 8 luces más intensas (explosiones e incendios): pos(x,y,z,intensidad), color. */
    public int fillLights(float[] pos, float[] col) {
        int c = 0;
        for (int k = 0; k < lCount; k++) {
            candX[c] = lx[k]; candY[c] = ly[k]; candZ[c] = lz[k]; candI[c] = li[k];
            candR[c] = lr[k]; candG[c] = lg[k]; candB[c] = lb[k];
            c++;
        }
        for (int i = 0; i < bCount; i++) {
            if (fire[i] < 0.05f) continue;
            float flick = 0.8f + 0.2f * (float) Math.sin(time * 11f + i * 3.1f) + 0.1f * rnd.nextFloat();
            candX[c] = (bx0[i] + bx1[i]) * 0.5f;
            candY[c] = bh[i] * hs[i] + 0.8f;
            candZ[c] = (bz0[i] + bz1[i]) * 0.5f;
            candI[c] = fire[i] * 1.3f * flick;
            candR[c] = 1f; candG[c] = 0.5f; candB[c] = 0.15f;
            c++;
        }
        int n = Math.min(8, c);
        java.util.Arrays.fill(candUsed, 0, c, false);
        for (int j = 0; j < n; j++) {
            int best = -1;
            for (int k = 0; k < c; k++) if (!candUsed[k] && (best < 0 || candI[k] > candI[best])) best = k;
            candUsed[best] = true;
            pos[j * 4] = candX[best]; pos[j * 4 + 1] = candY[best]; pos[j * 4 + 2] = candZ[best];
            pos[j * 4 + 3] = Math.min(5f, candI[best]);
            col[j * 3] = candR[best]; col[j * 3 + 1] = candG[best]; col[j * 3 + 2] = candB[best];
        }
        for (int j = n; j < 8; j++) {
            pos[j * 4] = pos[j * 4 + 1] = pos[j * 4 + 2] = pos[j * 4 + 3] = 0f;
            col[j * 3] = col[j * 3 + 1] = col[j * 3 + 2] = 0f;
        }
        return n;
    }

    /** Geometría de edificios: pos(3) normal(3) uv(2) extra(carbonizado, semilla, fuego) color(3) = 14 floats. */
    public int fillBuildings(float[] v, short[] idx) {
        int nv = 0, ni = 0;
        for (int i = 0; i < bCount; i++) {
            float h = Math.max(0.15f, bh[i] * hs[i]);
            float sink = bh[i] - h;            // al derrumbarse el edificio se hunde
            float x0 = bx0[i], x1 = bx1[i], z0 = bz0[i], z1 = bz1[i];
            float sd = pad[i] ? -1f : seed[i];
            float ch = charr[i], fr = fire[i];
            float cr = bcr[i], cg = bcg[i], cb = bcb[i];
            if (down[i]) { cr *= 0.5f; cg *= 0.45f; cb *= 0.4f; }
            // +X
            nv = face(v, idx, nv, ni, x1, 0, z1, x1, 0, z0, x1, h, z0, x1, h, z1, 1, 0, 0,
                    0, z1 - z0, sink, h + sink, ch, sd, fr, cr, cg, cb); ni += 6;
            // -X
            nv = face(v, idx, nv, ni, x0, 0, z0, x0, 0, z1, x0, h, z1, x0, h, z0, -1, 0, 0,
                    0, z1 - z0, sink, h + sink, ch, sd, fr, cr, cg, cb); ni += 6;
            // +Z
            nv = face(v, idx, nv, ni, x0, 0, z1, x1, 0, z1, x1, h, z1, x0, h, z1, 0, 0, 1,
                    0, x1 - x0, sink, h + sink, ch, sd, fr, cr, cg, cb); ni += 6;
            // -Z
            nv = face(v, idx, nv, ni, x1, 0, z0, x0, 0, z0, x0, h, z0, x1, h, z0, 0, 0, -1,
                    0, x1 - x0, sink, h + sink, ch, sd, fr, cr, cg, cb); ni += 6;
            // techo
            nv = face(v, idx, nv, ni, x0, h, z1, x1, h, z1, x1, h, z0, x0, h, z0, 0, 1, 0,
                    0, 1, 0, 1, ch, sd, fr, cr * 0.7f, cg * 0.7f, cb * 0.7f); ni += 6;
        }
        return ni;
    }

    private static final float WIN_W = 1.15f, WIN_H = 1.25f;

    private static int face(float[] v, short[] idx, int nv, int ni,
                            float ax, float ay, float az, float bx, float by, float bz,
                            float cx, float cy, float cz, float dx, float dy, float dz,
                            float nx, float ny, float nz, float u0, float u1, float v0, float v1,
                            float ch, float sd, float fr, float cr, float cg, float cb) {
        float uu0 = u0 / WIN_W, uu1 = u1 / WIN_W, vv0 = v0 / WIN_H, vv1 = v1 / WIN_H;
        int base = nv;
        nv = vert(v, nv, ax, ay, az, nx, ny, nz, uu0, vv0, ch, sd, fr, cr, cg, cb);
        nv = vert(v, nv, bx, by, bz, nx, ny, nz, uu1, vv0, ch, sd, fr, cr, cg, cb);
        nv = vert(v, nv, cx, cy, cz, nx, ny, nz, uu1, vv1, ch, sd, fr, cr, cg, cb);
        nv = vert(v, nv, dx, dy, dz, nx, ny, nz, uu0, vv1, ch, sd, fr, cr, cg, cb);
        idx[ni] = (short) base; idx[ni + 1] = (short) (base + 1); idx[ni + 2] = (short) (base + 2);
        idx[ni + 3] = (short) base; idx[ni + 4] = (short) (base + 2); idx[ni + 5] = (short) (base + 3);
        return nv;
    }

    private static int vert(float[] v, int nv, float px0, float py0, float pz0, float nx, float ny, float nz,
                            float u, float vv, float ch, float sd, float fr, float cr, float cg, float cb) {
        int o = nv * 14;
        v[o] = px0; v[o + 1] = py0; v[o + 2] = pz0;
        v[o + 3] = nx; v[o + 4] = ny; v[o + 5] = nz;
        v[o + 6] = u; v[o + 7] = vv;
        v[o + 8] = ch; v[o + 9] = sd; v[o + 10] = fr;
        v[o + 11] = cr; v[o + 12] = cg; v[o + 13] = cb;
        return nv + 1;
    }
}
