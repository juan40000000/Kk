package com.juan.monocromo;

import android.content.Context;
import android.content.SharedPreferences;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/** Ajustes persistentes: inicio, apps distractoras, ocultas y contadores del día. */
final class Prefs {
    private static final String COUNT = "c:";
    private final SharedPreferences p;
    private final SharedPreferences counts;

    Prefs(Context c) {
        p = c.getSharedPreferences("launcher", Context.MODE_PRIVATE);
        counts = c.getSharedPreferences("counts", Context.MODE_PRIVATE);
    }

    // ---- inicio ----

    List<String> pinned() {
        String s = p.getString("pinned", "");
        List<String> out = new ArrayList<>();
        if (!s.isEmpty()) out.addAll(Arrays.asList(s.split("\n")));
        return out;
    }

    void setPinned(List<String> keys) {
        p.edit().putString("pinned", android.text.TextUtils.join("\n", keys)).apply();
    }

    boolean isPinned(String key) {
        return pinned().contains(key);
    }

    void togglePinned(String key) {
        List<String> l = pinned();
        if (!l.remove(key)) l.add(key);
        setPinned(l);
    }

    static final int SMALL = 0;
    static final int MEDIUM = 1;
    static final int WIDE = 2;
    static final int LARGE = 3;

    int tileSize(String key) {
        // Compatibilidad: la primera versión solo guardaba qué mosaicos eran anchos.
        int legacy = set("wide").contains(key) ? WIDE : MEDIUM;
        return p.getInt("size:" + key, legacy);
    }

    void setTileSize(String key, int size) {
        p.edit().putInt("size:" + key, size).apply();
    }

    boolean isBlackTile(String key) {
        return set("black").contains(key);
    }

    void toggleBlackTile(String key) {
        toggle("black", key);
    }

    boolean isHintSeen() {
        return p.getBoolean("hint_resize", false);
    }

    void setHintSeen() {
        p.edit().putBoolean("hint_resize", true).apply();
    }

    // ---- anti-adicción ----

    boolean isDistracting(String key) {
        return set("distracting").contains(key);
    }

    void toggleDistracting(String key) {
        toggle("distracting", key);
    }

    boolean isHidden(String key) {
        return set("hidden").contains(key);
    }

    Set<String> hidden() {
        return set("hidden");
    }

    void toggleHidden(String key) {
        toggle("hidden", key);
    }

    /** Hasta cuándo está bloqueada la app (ms), o 0 si no lo está. */
    long blockedUntil(String key) {
        long until = p.getLong("block:" + key, 0);
        return until > System.currentTimeMillis() ? until : 0;
    }

    void blockUntil(String key, long until) {
        p.edit().putLong("block:" + key, until).apply();
    }

    void unblock(String key) {
        p.edit().remove("block:" + key).apply();
    }

    /** Segundos que hay que esperar mirando la pantalla para desbloquear antes de tiempo. */
    int unblockWaitSeconds() {
        return p.getInt("unblock_wait", 60);
    }

    void setUnblockWaitSeconds(int s) {
        p.edit().putInt("unblock_wait", s).apply();
    }

    boolean liveTiles() {
        return p.getBoolean("live", true);
    }

    void setLiveTiles(boolean on) {
        p.edit().putBoolean("live", on).apply();
    }

    int pauseSeconds() {
        return p.getInt("pause", 10);
    }

    void setPauseSeconds(int s) {
        p.edit().putInt("pause", s).apply();
    }

    // ---- clima ----

    /** "" sin configurar, "gps" ubicación del teléfono, "city" ciudad escrita. */
    String weatherMode() {
        return p.getString("w_mode", "");
    }

    double weatherLat() {
        return Double.longBitsToDouble(p.getLong("w_lat", 0));
    }

    double weatherLon() {
        return Double.longBitsToDouble(p.getLong("w_lon", 0));
    }

    String weatherCity() {
        return p.getString("w_city", "");
    }

    void setWeatherPlace(String mode, double lat, double lon) {
        p.edit().putString("w_mode", mode)
                .putLong("w_lat", Double.doubleToLongBits(lat))
                .putLong("w_lon", Double.doubleToLongBits(lon))
                .apply();
    }

    void setWeatherCity(String city) {
        p.edit().putString("w_city", city).apply();
    }

    void clearWeatherData() {
        p.edit().remove("w_time").apply();
    }

    void saveWeather(Weather.Data d) {
        p.edit().putFloat("w_temp", (float) d.temp)
                .putFloat("w_max", (float) d.max)
                .putFloat("w_min", (float) d.min)
                .putInt("w_code", d.code)
                .putBoolean("w_day", d.day)
                .putLong("w_time", d.time)
                .apply();
    }

    Weather.Data loadWeather() {
        if (!p.contains("w_time")) return null;
        Weather.Data d = new Weather.Data();
        d.temp = p.getFloat("w_temp", 0);
        d.max = p.getFloat("w_max", 0);
        d.min = p.getFloat("w_min", 0);
        d.code = p.getInt("w_code", 0);
        d.day = p.getBoolean("w_day", true);
        d.time = p.getLong("w_time", 0);
        return d;
    }

    // ---- contadores de hoy ----

    private void rollDay() {
        String today = new SimpleDateFormat("yyyyMMdd", Locale.US).format(new Date());
        if (!today.equals(counts.getString("_day", ""))) {
            counts.edit().clear().putString("_day", today).apply();
        }
    }

    void countLaunch(String key) {
        rollDay();
        counts.edit()
                .putInt(COUNT + key, counts.getInt(COUNT + key, 0) + 1)
                .putInt("_total", counts.getInt("_total", 0) + 1)
                .apply();
    }

    void countAvoided() {
        rollDay();
        counts.edit().putInt("_avoided", counts.getInt("_avoided", 0) + 1).apply();
    }

    int launches(String key) {
        rollDay();
        return counts.getInt(COUNT + key, 0);
    }

    int totalLaunches() {
        rollDay();
        return counts.getInt("_total", 0);
    }

    int avoided() {
        rollDay();
        return counts.getInt("_avoided", 0);
    }

    Map<String, Integer> allLaunches() {
        rollDay();
        Map<String, Integer> out = new HashMap<>();
        for (Map.Entry<String, ?> e : counts.getAll().entrySet()) {
            if (e.getKey().startsWith(COUNT) && e.getValue() instanceof Integer) {
                out.put(e.getKey().substring(COUNT.length()), (Integer) e.getValue());
            }
        }
        return out;
    }

    // ---- util ----

    private Set<String> set(String name) {
        return new HashSet<>(p.getStringSet(name, new HashSet<>()));
    }

    private void toggle(String name, String key) {
        Set<String> s = set(name);
        if (!s.remove(key)) s.add(key);
        p.edit().putStringSet(name, s).apply();
    }
}
