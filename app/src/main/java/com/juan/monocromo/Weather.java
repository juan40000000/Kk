package com.juan.monocromo;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.util.Locale;

/** Clima desde Open-Meteo (gratis y sin clave). Las llamadas de red son bloqueantes. */
final class Weather {
    static final int UNKNOWN = -1;
    static final int CLEAR = 0;
    static final int PARTLY = 1;
    static final int CLOUDY = 2;
    static final int FOG = 3;
    static final int RAIN = 4;
    static final int SNOW = 5;
    static final int STORM = 6;

    static final class Data {
        double temp;
        double max;
        double min;
        int code;
        boolean day;
        long time;
    }

    static final class Place {
        double lat;
        double lon;
        String name;
    }

    private Weather() { }

    /** Código WMO de Open-Meteo -> escena de la animación. */
    static int scene(int code) {
        if (code == 0) return CLEAR;
        if (code == 1 || code == 2) return PARTLY;
        if (code == 3) return CLOUDY;
        if (code == 45 || code == 48) return FOG;
        if ((code >= 71 && code <= 77) || code == 85 || code == 86) return SNOW;
        if (code >= 95) return STORM;
        if (code >= 51 && code <= 82) return RAIN;
        return CLOUDY;
    }

    static String describe(int code) {
        switch (code) {
            case 0: return "despejado";
            case 1: return "casi despejado";
            case 2: return "parcialmente nublado";
            case 3: return "nublado";
            case 45: case 48: return "niebla";
            case 51: case 53: case 55: case 56: case 57: return "llovizna";
            case 61: case 63: case 66: return "lluvia";
            case 65: case 67: return "lluvia fuerte";
            case 71: case 73: case 75: case 77: return "nieve";
            case 80: case 81: case 82: return "chubascos";
            case 85: case 86: return "chubascos de nieve";
            case 95: return "tormenta";
            case 96: case 99: return "tormenta con granizo";
            default: return "—";
        }
    }

    static Data fetch(double lat, double lon) throws IOException, org.json.JSONException {
        String url = String.format(Locale.US,
                "https://api.open-meteo.com/v1/forecast?latitude=%.4f&longitude=%.4f"
                        + "&current=temperature_2m,weather_code,is_day"
                        + "&daily=temperature_2m_max,temperature_2m_min&timezone=auto&forecast_days=1",
                lat, lon);
        JSONObject json = new JSONObject(get(url));
        JSONObject cur = json.getJSONObject("current");
        JSONObject daily = json.getJSONObject("daily");
        Data d = new Data();
        d.temp = cur.getDouble("temperature_2m");
        d.code = cur.getInt("weather_code");
        d.day = cur.optInt("is_day", 1) == 1;
        d.max = daily.getJSONArray("temperature_2m_max").getDouble(0);
        d.min = daily.getJSONArray("temperature_2m_min").getDouble(0);
        d.time = System.currentTimeMillis();
        return d;
    }

    /** Busca una ciudad por nombre. Devuelve null si no la encuentra. */
    static Place geocode(String name) throws IOException, org.json.JSONException {
        String url = "https://geocoding-api.open-meteo.com/v1/search?count=1&language=es&name="
                + URLEncoder.encode(name.trim(), "UTF-8");
        JSONArray results = new JSONObject(get(url)).optJSONArray("results");
        if (results == null || results.length() == 0) return null;
        JSONObject r = results.getJSONObject(0);
        Place p = new Place();
        p.lat = r.getDouble("latitude");
        p.lon = r.getDouble("longitude");
        p.name = r.optString("name", name);
        return p;
    }

    private static String get(String url) throws IOException {
        HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
        c.setConnectTimeout(10_000);
        c.setReadTimeout(10_000);
        try (InputStream in = c.getInputStream()) {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buf = new byte[4096];
            int n;
            while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
            return out.toString("UTF-8");
        } finally {
            c.disconnect();
        }
    }
}
