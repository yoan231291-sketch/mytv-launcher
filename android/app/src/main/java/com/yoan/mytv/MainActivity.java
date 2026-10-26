package com.yoan.mytv;

import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.util.Base64;
import android.view.KeyEvent;
import android.view.View;
import android.view.WindowManager;
import android.webkit.JavascriptInterface;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceError;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Launcher a pantalla completa: muestra la web de GitHub Pages y abre las apps reales de la TV. */
public class MainActivity extends Activity {

    private WebView web;
    private final Handler ui = new Handler(Looper.getMainLooper());
    private boolean offline = false;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        // Pantalla siempre encendida mientras MyTV está al frente: sin salvapantallas ni suspensión
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN | WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        WebView.setWebContentsDebuggingEnabled(true);   // permite revisar la web desde la Mac por ADB
        web = new WebView(this);
        web.setBackgroundColor(Color.BLACK);
        setContentView(web);
        hideSystemUi();

        WebSettings s = web.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setMediaPlaybackRequiresUserGesture(false);   // la cámara arranca sola (en silencio)
        s.setLoadWithOverviewMode(true);
        s.setUseWideViewPort(true);
        s.setCacheMode(WebSettings.LOAD_DEFAULT);
        web.setWebChromeClient(new WebChromeClient() {
            @Override public boolean onConsoleMessage(android.webkit.ConsoleMessage m) {
                android.util.Log.i("MyTV-web", m.message() + " @" + m.lineNumber());
                return true;
            }
        });
        web.addJavascriptInterface(new Bridge(), "AndroidTV");
        web.setWebViewClient(new WebViewClient() {
            /** Bloqueador de anuncios: corta las peticiones a servidores de publicidad (solo dentro de MyTV). */
            @Override
            public WebResourceResponse shouldInterceptRequest(WebView v, WebResourceRequest req) {
                return AdBlock.isAd(req.getUrl()) ? AdBlock.EMPTY() : null;
            }

            @Override
            public void onReceivedError(WebView v, WebResourceRequest req, WebResourceError err) {
                if (req.isForMainFrame()) showOffline();
            }
        });
        load();
    }

    private void load() {
        offline = false;
        web.loadUrl(BuildConfig.LAUNCHER_URL + "?v=" + BuildConfig.VERSION_CODE);
    }

    /** Sin internet: carga una pantalla local con las apps y reintenta cada 15 s. */
    private void showOffline() {
        offline = true;
        web.loadUrl("file:///android_asset/offline.html");
        ui.postDelayed(() -> { if (offline) load(); }, 15000);
    }

    private void hideSystemUi() {
        getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_FULLSCREEN
                | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);
    }

    /** Teclas del control que la web no recibe bien: Atrás, Menú, Play/Pausa, Adelantar, Retroceder. */
    @Override
    public boolean dispatchKeyEvent(KeyEvent e) {
        String k = null;
        switch (e.getKeyCode()) {
            case KeyEvent.KEYCODE_BACK: k = "back"; break;
            case KeyEvent.KEYCODE_MENU: k = "menu"; break;
            case KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE:
            case KeyEvent.KEYCODE_MEDIA_PLAY:
            case KeyEvent.KEYCODE_MEDIA_PAUSE: k = "playpause"; break;
            case KeyEvent.KEYCODE_MEDIA_FAST_FORWARD: k = "ff"; break;
            case KeyEvent.KEYCODE_MEDIA_REWIND: k = "rew"; break;
        }
        if (k != null) {
            if (e.getAction() == KeyEvent.ACTION_UP) web.evaluateJavascript("window.tvKey && tvKey('" + k + "')", null);
            return true;   // nunca salimos del launcher con Atrás
        }
        return super.dispatchKeyEvent(e);
    }

    @Override protected void onResume() { super.onResume(); hideSystemUi(); web.onResume(); web.resumeTimers(); }
    // Al abrir otra app, pausamos la cámara para no gastar internet ni CPU
    @Override protected void onStop() { super.onStop(); web.onPause(); web.pauseTimers(); }
    @Override protected void onNewIntent(Intent i) { super.onNewIntent(i); if (offline) load(); }

    // ---------- Puente JavaScript ↔ Android ----------
    class Bridge {
        /** Abre la primera app instalada de una lista "pkg1,pkg2". */
        @JavascriptInterface
        public boolean launch(String pkgs) {
            PackageManager pm = getPackageManager();
            for (String p : pkgs.split(",")) {
                Intent i = pm.getLeanbackLaunchIntentForPackage(p.trim());
                if (i == null) i = pm.getLaunchIntentForPackage(p.trim());
                if (i != null) {
                    HomeWatcherService.allowAmazonHomeFor(5000);
                    i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    startActivity(i);
                    return true;
                }
            }
            return false;
        }

        @JavascriptInterface
        public void openSettings() {
            HomeWatcherService.allowAmazonHomeFor(5000);
            startActivity(new Intent(Settings.ACTION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        }

        /** Abre una pantalla concreta de Ajustes de Fire TV ("paquete/.Clase" o una acción). */
        @JavascriptInterface
        public boolean openSetting(String target) {
            Intent i = new Intent();
            if (target.contains("/")) {
                String[] p = target.split("/", 2);
                i.setClassName(p[0], p[1].startsWith(".") ? p[0] + p[1] : p[1]);
            } else {
                i.setAction(target).setPackage("com.amazon.tv.settings.v2");
            }
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            try { startActivity(i); return true; } catch (Exception ex) { return false; }
        }

        /** Lista de apps instaladas (nombre, paquete, ícono en base64). */
        @JavascriptInterface
        public String getApps() {
            PackageManager pm = getPackageManager();
            JSONArray out = new JSONArray();
            Set<String> seen = new HashSet<>();
            seen.add(getPackageName());
            for (String cat : new String[]{Intent.CATEGORY_LEANBACK_LAUNCHER, Intent.CATEGORY_LAUNCHER}) {
                List<ResolveInfo> list = pm.queryIntentActivities(new Intent(Intent.ACTION_MAIN).addCategory(cat), 0);
                for (ResolveInfo r : list) {
                    String pkg = r.activityInfo.packageName;
                    if (!seen.add(pkg)) continue;
                    try {
                        JSONObject o = new JSONObject();
                        o.put("pkg", pkg);
                        o.put("label", r.loadLabel(pm).toString());
                        Drawable d = r.activityInfo.applicationInfo.loadBanner(pm);
                        if (d == null) d = r.loadIcon(pm);
                        o.put("icon", toDataUrl(d));
                        out.put(o);
                    } catch (Exception ignored) { }
                }
            }
            return out.toString();
        }

        /**
         * Busca en YouTube transmisiones EN VIVO para una ciudad, desde el internet de la casa (YouTube no lo bloquea).
         * Asíncrono: al terminar llama window[callback](tag, [ids]) en la página.
         */
        @JavascriptInterface
        public void findLive(final String query, final String tag, final String callback) {
            new Thread(() -> {
                String json = LiveSearch.search(query).toString();
                ui.post(() -> web.evaluateJavascript("window." + callback + " && " + callback + "(" + JSONObject.quote(tag) + "," + json + ")", null));
            }).start();
        }

        @JavascriptInterface public int version() { return BuildConfig.VERSION_CODE; }
    }

    private static String toDataUrl(Drawable d) {
        if (d == null) return "";
        int w = Math.max(1, Math.min(320, d.getIntrinsicWidth() > 0 ? d.getIntrinsicWidth() : 160));
        int h = Math.max(1, Math.min(180, d.getIntrinsicHeight() > 0 ? d.getIntrinsicHeight() : 160));
        Bitmap bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(bmp);
        d.setBounds(0, 0, w, h);
        d.draw(c);
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        bmp.compress(Bitmap.CompressFormat.PNG, 100, bos);
        return "data:image/png;base64," + Base64.encodeToString(bos.toByteArray(), Base64.NO_WRAP);
    }
}
