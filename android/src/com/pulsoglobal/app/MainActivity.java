package com.pulsoglobal.app;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.Dialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Base64;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.webkit.JavascriptInterface;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.FrameLayout;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.zip.GZIPInputStream;

/**
 * Contenedor nativo: un WebView a pantalla completa que carga la interfaz
 * desde assets y le ofrece un puente con:
 *  - HTTP sin restricciones CORS (noticias, desastres, clips, cotizaciones),
 *  - reproductor de video a pantalla completa,
 *  - radio en segundo plano mediante {@link RadioService}.
 */
public class MainActivity extends Activity {

    private static final String UA =
            "Mozilla/5.0 (Linux; Android 14; Mobile) AppleWebKit/537.36 "
                    + "(KHTML, like Gecko) Chrome/124.0 Mobile Safari/537.36";
    private static final int MAX_BYTES = 4 * 1024 * 1024;
    // Origen con el que se incrusta el reproductor de YouTube (YouTube exige un Referer)
    private static final String VIDEO_ORIGIN = "https://com.pulsoglobal.app";

    private WebView web;
    private Dialog videoDialog;
    private final ExecutorService pool = Executors.newFixedThreadPool(6);
    private final Handler main = new Handler(Looper.getMainLooper());

    @SuppressLint({"SetJavaScriptEnabled", "AddJavascriptInterface"})
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Window w = getWindow();
        w.setStatusBarColor(Color.parseColor("#070B14"));
        w.setNavigationBarColor(Color.parseColor("#070B14"));

        web = new WebView(this);
        web.setBackgroundColor(Color.parseColor("#070B14"));
        web.setOverScrollMode(View.OVER_SCROLL_NEVER);
        WebSettings s = web.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setDatabaseEnabled(true);
        s.setAllowFileAccess(true);
        s.setMediaPlaybackRequiresUserGesture(false);
        s.setMixedContentMode(WebSettings.MIXED_CONTENT_ALWAYS_ALLOW);
        s.setTextZoom(100);
        s.setCacheMode(WebSettings.LOAD_DEFAULT);
        if (Build.VERSION.SDK_INT >= 26) s.setSafeBrowsingEnabled(true);

        web.addJavascriptInterface(new Bridge(), "Native");
        web.setWebChromeClient(new WebChromeClient());
        web.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest req) {
                Uri u = req.getUrl();
                if ("file".equals(u.getScheme())) return false;
                openExternal(u.toString());
                return true;
            }
        });
        setContentView(web);
        web.loadUrl("file:///android_asset/web/index.html");
        prefs().edit().remove("claude_key").apply(); // la versión 3.0 podía guardar una clave de API: se borra

        RadioService.listener = new RadioService.Listener() {
            @Override
            public void onState(final String state, final String name) {
                main.post(new Runnable() {
                    @Override
                    public void run() {
                        if (web != null) web.evaluateJavascript("window.onRadioState && window.onRadioState("
                                + jsString(state) + "," + jsString(name) + ")", null);
                    }
                });
            }
        };
    }

    private void openExternal(String url) {
        try {
            startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url)));
        } catch (Exception ignored) {
        }
    }

    @Override
    public void onBackPressed() {
        if (videoDialog != null && videoDialog.isShowing()) {
            videoDialog.dismiss();
            return;
        }
        web.evaluateJavascript("window.handleBack ? window.handleBack() : false",
                new ValueCallback<String>() {
                    @Override
                    public void onReceiveValue(String handled) {
                        if (!"true".equals(handled)) MainActivity.super.onBackPressed();
                    }
                });
    }

    @Override
    protected void onPause() {
        super.onPause();
        web.evaluateJavascript("window.onAppPause && window.onAppPause()", null);
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (web != null) web.evaluateJavascript("window.onAppResume && window.onAppResume()", null);
    }

    @Override
    protected void onDestroy() {
        RadioService.listener = null;
        if (videoDialog != null) videoDialog.dismiss();
        pool.shutdownNow();
        web.destroy();
        super.onDestroy();
    }

    private SharedPreferences prefs() {
        return getSharedPreferences("pulso", MODE_PRIVATE);
    }

    private static String jsString(String s) {
        if (s == null) return "null";
        StringBuilder b = new StringBuilder("'");
        for (char c : s.toCharArray()) {
            if (c == '\'' || c == '\\') b.append('\\').append(c);
            else if (c < 32 || c == '<' || c == '>') b.append(String.format("\\u%04x", (int) c));
            else b.append(c);
        }
        return b.append("'").toString();
    }

    private void deliver(final String id, final boolean ok, final byte[] body, final String err) {
        final String script = ok
                ? "window.__nativeDone('" + id + "',true,'" + Base64.encodeToString(body, Base64.NO_WRAP) + "')"
                : "window.__nativeDone('" + id + "',false," + jsString(err) + ")";
        main.post(new Runnable() {
            @Override
            public void run() {
                if (web != null) web.evaluateJavascript(script, null);
            }
        });
    }

    /** Métodos expuestos a JavaScript como window.Native. */
    private class Bridge {
        @JavascriptInterface
        public void fetch(final String id, final String url) {
            pool.execute(new Runnable() {
                @Override
                public void run() {
                    try {
                        deliver(id, true, download(url), null);
                    } catch (Exception e) {
                        deliver(id, false, null, String.valueOf(e.getMessage()));
                    }
                }
            });
        }

        @JavascriptInterface
        public void openUrl(final String url) {
            main.post(new Runnable() {
                @Override
                public void run() {
                    openExternal(url);
                }
            });
        }

        @JavascriptInterface
        public void share(final String text) {
            main.post(new Runnable() {
                @Override
                public void run() {
                    Intent i = new Intent(Intent.ACTION_SEND);
                    i.setType("text/plain");
                    i.putExtra(Intent.EXTRA_TEXT, text);
                    try {
                        startActivity(Intent.createChooser(i, "Compartir"));
                    } catch (Exception ignored) {
                    }
                }
            });
        }

        // ---------- video ----------
        @JavascriptInterface
        public void playVideo(final String videoId, final String title) {
            if (videoId == null || !videoId.matches("[A-Za-z0-9_-]{6,20}")) return;
            main.post(new Runnable() {
                @Override
                public void run() {
                    showVideo(videoId, title);
                }
            });
        }

        // ---------- radio ----------
        @JavascriptInterface
        public void radioPlay(String url, String name, String logo) {
            if (Build.VERSION.SDK_INT >= 33
                    && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
                    && !prefs().getBoolean("asked_notif", false)) {
                prefs().edit().putBoolean("asked_notif", true).apply();
                requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, 7);
            }
            Intent i = new Intent(MainActivity.this, RadioService.class)
                    .setAction(RadioService.ACTION_PLAY)
                    .putExtra("url", url).putExtra("name", name);
            if (Build.VERSION.SDK_INT >= 26) startForegroundService(i);
            else startService(i);
        }

        @JavascriptInterface
        public void radioToggle() {
            sendRadio(RadioService.ACTION_TOGGLE);
        }

        @JavascriptInterface
        public void radioStop() {
            sendRadio(RadioService.ACTION_STOP);
        }

        @JavascriptInterface
        public String radioState() {
            return RadioService.state + "|" + (RadioService.stationName == null ? "" : RadioService.stationName);
        }
    }

    private void sendRadio(String action) {
        if ("stopped".equals(RadioService.state)) return;
        startService(new Intent(this, RadioService.class).setAction(action));
    }

    @SuppressLint("SetJavaScriptEnabled")
    private void showVideo(final String videoId, String title) {
        if (videoDialog != null) videoDialog.dismiss();
        final Dialog d = new Dialog(this, android.R.style.Theme_Black_NoTitleBar_Fullscreen);
        final FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(Color.BLACK);
        final WebView v = new WebView(this);
        WebSettings s = v.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setMediaPlaybackRequiresUserGesture(false);
        v.setBackgroundColor(Color.BLACK);
        v.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest req) {
                String u = req.getUrl().toString();
                if (u.startsWith("pulso://close")) {
                    d.dismiss();
                } else if (u.startsWith("pulso://youtube")) {
                    d.dismiss();
                    openExternal("https://www.youtube.com/watch?v=" + videoId);
                } else if (!u.startsWith(VIDEO_ORIGIN)) {
                    openExternal(u);
                }
                return true;
            }
        });
        v.setWebChromeClient(new WebChromeClient() {
            private View custom;

            @Override
            public void onShowCustomView(View view, CustomViewCallback cb) {
                custom = view;
                root.addView(view, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
            }

            @Override
            public void onHideCustomView() {
                if (custom != null) root.removeView(custom);
                custom = null;
            }
        });
        String safeTitle = title == null ? "" : title.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;");
        String html = "<!doctype html><html><head><meta name='viewport' content='width=device-width,initial-scale=1'>"
                + "<meta name='referrer' content='strict-origin-when-cross-origin'><style>"
                + "html,body{margin:0;height:100%;background:#000;color:#fff;font-family:sans-serif}"
                + ".bar{display:flex;align-items:center;gap:10px;padding:12px 14px;background:#0C1222}"
                + ".bar b{flex:1;font-size:14px;line-height:1.3;overflow:hidden;display:-webkit-box;-webkit-line-clamp:2;-webkit-box-orient:vertical}"
                + ".bar a{color:#fff;text-decoration:none;background:rgba(255,255,255,.12);padding:8px 11px;border-radius:10px;font-size:13px;white-space:nowrap}"
                + ".wrap{position:relative;width:100%;padding-top:56.25%;margin-top:18vh}"
                + "iframe{position:absolute;inset:0;width:100%;height:100%;border:0}"
                + "p{color:#8A95B4;font-size:12px;text-align:center;margin:14px}</style></head><body>"
                + "<div class='bar'><b>" + safeTitle + "</b><a href='pulso://youtube'>YouTube</a><a href='pulso://close'>✕</a></div>"
                + "<div class='wrap'><iframe src='https://www.youtube.com/embed/" + videoId
                + "?autoplay=1&playsinline=1&rel=0&modestbranding=1&origin=" + VIDEO_ORIGIN + "' "
                + "allow='autoplay; encrypted-media; picture-in-picture; fullscreen' allowfullscreen "
                + "referrerpolicy='strict-origin-when-cross-origin'></iframe></div>"
                + "<p>Si el canal no permite verlo acá, tocá «YouTube».</p></body></html>";
        v.loadDataWithBaseURL(VIDEO_ORIGIN + "/", html, "text/html", "utf-8", null);
        root.addView(v, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        d.setContentView(root);
        d.setOnDismissListener(new android.content.DialogInterface.OnDismissListener() {
            @Override
            public void onDismiss(android.content.DialogInterface di) {
                v.loadUrl("about:blank");
                v.destroy();
                if (videoDialog == d) videoDialog = null;
            }
        });
        if (d.getWindow() != null) d.getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        videoDialog = d;
        d.show();
    }

    private static byte[] readAll(InputStream in) throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buf = new byte[16384];
        int n;
        while ((n = in.read(buf)) > 0) {
            out.write(buf, 0, n);
            if (out.size() > MAX_BYTES) break;
        }
        in.close();
        return out.toByteArray();
    }

    private static byte[] download(String spec) throws Exception {
        URL url = new URL(spec);
        int redirects = 0;
        while (true) {
            if (!"https".equals(url.getProtocol())) throw new Exception("solo https");
            HttpURLConnection c = (HttpURLConnection) url.openConnection();
            c.setConnectTimeout(12000);
            c.setReadTimeout(15000);
            c.setInstanceFollowRedirects(false);
            c.setRequestProperty("User-Agent", UA);
            c.setRequestProperty("Accept", "*/*");
            c.setRequestProperty("Accept-Language", "es-419,es;q=0.9,en;q=0.8");
            c.setRequestProperty("Accept-Encoding", "gzip");
            int code = c.getResponseCode();
            if (code >= 300 && code < 400 && redirects < 5) {
                String loc = c.getHeaderField("Location");
                c.disconnect();
                if (loc == null) throw new Exception("redireccion sin destino");
                url = new URL(url, loc);
                redirects++;
                continue;
            }
            if (code < 200 || code >= 300) {
                c.disconnect();
                throw new Exception("HTTP " + code);
            }
            InputStream in = c.getInputStream();
            if ("gzip".equalsIgnoreCase(c.getContentEncoding())) in = new GZIPInputStream(in);
            byte[] body = readAll(in);
            c.disconnect();
            return body;
        }
    }
}
