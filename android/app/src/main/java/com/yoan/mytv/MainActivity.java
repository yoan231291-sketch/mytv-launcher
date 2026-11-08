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
import android.view.MotionEvent;
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
    // Para el control remoto del teléfono
    static volatile MainActivity current;
    static volatile String pendingJs;            // orden que llegó antes de que la página cargara
    private volatile boolean front = false;
    boolean isFront() { return front; }
    void remoteKey(final int code) {
        ui.post(() -> {
            onUserInteraction();
            long t = android.os.SystemClock.uptimeMillis();
            web.dispatchKeyEvent(new KeyEvent(t, t, KeyEvent.ACTION_DOWN, code, 0));
            web.dispatchKeyEvent(new KeyEvent(t, t, KeyEvent.ACTION_UP, code, 0));
        });
    }
    void remoteJs(final String js) { ui.post(() -> { onUserInteraction(); web.evaluateJavascript(js, null); }); }
    private final Handler ui = new Handler(Looper.getMainLooper());
    private boolean offline = false;
    // Vigilante: la página avisa con AndroidTV.alive() mientras la cámara se reproduce.
    // Si pasan 2 minutos sin aviso (página trabada, sin cámara), se recarga sola sin caché.
    private volatile long lastAlive = 0;
    private static final long STALL_MS = 120_000;
    // Reposo: tras 15 min sin tocar el control, MyTV detiene la cámara y deja de forzar la pantalla encendida,
    // así la TV se apaga sola (y el equipo de sonido Bluetooth se reconecta bien al volver a prenderla).
    private long idleMs = 15 * 60_000L;
    private long lastInput = System.currentTimeMillis();
    private boolean dozing = false;
    private final Runnable idleCheck = new Runnable() {
        @Override public void run() {
            if (!dozing && System.currentTimeMillis() - lastInput > idleMs) doze();
            ui.postDelayed(this, 20_000);
        }
    };
    private void doze() {
        dozing = true;
        android.util.Log.i("MyTV", "reposo: sin actividad, la TV puede apagarse");
        getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        web.evaluateJavascript("window.tvBackground && tvBackground(true)", null);
    }
    private void wake() {
        if (!dozing) return;
        dozing = false;
        android.util.Log.i("MyTV", "reposo: de vuelta");
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        lastAlive = System.currentTimeMillis();
        web.evaluateJavascript("window.tvBackground && tvBackground(false)", null);
    }
    // Al apagarse la pantalla (control, temporizador o reposo) soltamos "pantalla encendida" y paramos la cámara:
    // así nada de MyTV impide que la TV entre en apagado profundo (y el Bluetooth se suelta bien).
    private final android.content.BroadcastReceiver screenOff = new android.content.BroadcastReceiver() {
        @Override public void onReceive(android.content.Context c, Intent i) {
            if (Intent.ACTION_SCREEN_ON.equals(i.getAction())) { lastInput = System.currentTimeMillis(); if (front) wake(); }
            else if (!dozing) doze();
        }
    };
    @Override public void onUserInteraction() { super.onUserInteraction(); lastInput = System.currentTimeMillis(); wake(); }
    private void readIdle(Intent i) { if (i != null && i.hasExtra("idle_sec")) idleMs = i.getIntExtra("idle_sec", 900) * 1000L; }

    private final Runnable watchdog = new Runnable() {
        @Override public void run() {
            if (dozing) { lastAlive = System.currentTimeMillis(); ui.postDelayed(this, 30_000); return; }
            web.resumeTimers(); web.onResume();           // por si la página quedó congelada
            if (!offline && System.currentTimeMillis() - lastAlive > STALL_MS) {
                android.util.Log.i("MyTV", "vigilante: la página no responde, recargando");
                lastAlive = System.currentTimeMillis();
                web.getSettings().setCacheMode(WebSettings.LOAD_NO_CACHE);
                load();
                ui.postDelayed(() -> web.getSettings().setCacheMode(WebSettings.LOAD_DEFAULT), 30_000);
            }
            ui.postDelayed(this, 30_000);
        }
    };

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        // Pantalla siempre encendida mientras MyTV está al frente: sin salvapantallas ni suspensión
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN | WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        WebView.setWebContentsDebuggingEnabled(true);   // permite revisar la web desde la Mac por ADB
        current = this;
        readIdle(getIntent());
        web = new WebView(this);
        web.setBackgroundColor(Color.BLACK);
        setContentView(web);
        hideSystemUi();

        // Cookies del reproductor de YouTube (está incrustado = "de terceros"). Android las bloquea por defecto
        // y sin ellas YouTube entrega el anuncio pero no la transmisión en vivo (se queda cargando).
        android.webkit.CookieManager cm = android.webkit.CookieManager.getInstance();
        cm.setAcceptCookie(true);
        cm.setAcceptThirdPartyCookies(web, true);

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
            // Sin bloqueador de anuncios: desde oct-2026 YouTube detiene el reproductor si se bloquean sus anuncios.

            @Override
            public void onPageFinished(WebView v, String url) {
                final String js = pendingJs;
                if (js != null) { pendingJs = null; ui.postDelayed(() -> web.evaluateJavascript(js, null), 3000); }
            }
            @Override
            public void onReceivedError(WebView v, WebResourceRequest req, WebResourceError err) {
                if (req.isForMainFrame()) showOffline();
            }
        });
        load();
        android.content.IntentFilter f = new android.content.IntentFilter(Intent.ACTION_SCREEN_OFF); f.addAction(Intent.ACTION_SCREEN_ON);
        registerReceiver(screenOff, f);
    }

    private void load() {
        offline = false;
        lastAlive = System.currentTimeMillis();
        // "&t=" cambia cada minuto: así la TV siempre carga la última versión de la página (no la del caché)
        web.loadUrl(BuildConfig.LAUNCHER_URL + "?v=" + BuildConfig.VERSION_CODE + "&t=" + (System.currentTimeMillis() / 60000));
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

    @Override protected void onResume() {
        super.onResume(); front = true; hideSystemUi(); web.onResume(); web.resumeTimers();
        lastAlive = System.currentTimeMillis();           // le damos 2 min para arrancar al volver
        android.os.PowerManager pm = (android.os.PowerManager) getSystemService(POWER_SERVICE);
        if (pm == null || pm.isInteractive()) { lastInput = System.currentTimeMillis(); wake(); }   // al prender la TV o volver de otra app cuenta como actividad
        ui.removeCallbacks(watchdog); ui.postDelayed(watchdog, 30_000);
        ui.removeCallbacks(idleCheck); ui.postDelayed(idleCheck, 20_000);
    }
    // Al abrir otra app, pausamos la cámara (y el vigilante) para no gastar internet ni CPU
    // Al abrir otra app pausamos solo esta vista (NO pauseTimers: es global y en Fire TV a veces no se reanuda → pantalla negra)
    // Al abrir otra app: la página DETIENE la cámara y sus vigilantes (si no, sigue cargando videos en segundo plano,
    // le quita el decodificador de video a la otra app y esa app se congela a ratos).
    @Override protected void onPause() { super.onPause(); front = false; }
    @Override protected void onDestroy() { if (current == this) current = null; try { unregisterReceiver(screenOff); } catch (Exception ignored) { } super.onDestroy(); }
    @Override protected void onStop() {
        super.onStop(); ui.removeCallbacks(watchdog); ui.removeCallbacks(idleCheck);
        web.evaluateJavascript("window.tvBackground && tvBackground(true)", null);
        web.onPause();
    }
    @Override protected void onStart() {
        super.onStart(); web.onResume(); web.resumeTimers();
        if (!dozing) web.evaluateJavascript("window.tvBackground && tvBackground(false)", null);
    }
    // Poca memoria: soltamos caché del WebView
    @Override public void onTrimMemory(int level) { super.onTrimMemory(level); if (level >= TRIM_MEMORY_RUNNING_LOW) web.freeMemory(); }
    @Override protected void onNewIntent(Intent i) { super.onNewIntent(i); readIdle(i); if (offline) load(); }

    // ---------- Puente JavaScript ↔ Android ----------
    class Bridge {
        /** Abre la primera app instalada de una lista "pkg1,pkg2". */
        @JavascriptInterface
        public boolean launch(String pkgs) { return Apps.launch(MainActivity.this, pkgs); }

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
        public String getApps() { return Apps.list(MainActivity.this); }

        /** Dirección del control remoto para el teléfono (se muestra en Ajustes como enlace y QR). */
        @JavascriptInterface
        public String remoteUrl() { return RemoteServer.url(MainActivity.this); }

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

        /** La página avisa que la cámara se está reproduciendo (ver vigilante). */
        @JavascriptInterface public void alive() { lastAlive = System.currentTimeMillis(); }

        /**
         * Pasa un "puntero" sobre el video y lo retira, como haría un mouse: así YouTube esconde sus controles
         * (título, pausa, "More videos") igual que en una computadora. La página activa/desactiva pointer-events.
         */
        @JavascriptInterface
        public void hoverNudge() {
            ui.post(() -> {
                final float x = web.getWidth() / 2f, y = web.getHeight() / 2f;
                hover(MotionEvent.ACTION_HOVER_ENTER, x, y);
                ui.postDelayed(() -> hover(MotionEvent.ACTION_HOVER_MOVE, x + 8, y + 6), 100);
                ui.postDelayed(() -> hover(MotionEvent.ACTION_HOVER_MOVE, x + 12, y + 10), 650);   // ya sin pointer-events: sale del video
                ui.postDelayed(() -> hover(MotionEvent.ACTION_HOVER_EXIT, x + 12, y + 10), 750);
            });
        }

        @JavascriptInterface public int version() { return BuildConfig.VERSION_CODE; }

        /** Clave del canal de dibujo (el teléfono dibuja desde cualquier lugar y aparece aquí). */
        @JavascriptInterface public String drawKey() { return RemoteServer.drawKey(MainActivity.this); }

        /** Llegó un dibujo mientras había otra app: si la pantalla está prendida, MyTV pasa al frente. */
        @JavascriptInterface public void bringFront() {
            android.os.PowerManager pm = (android.os.PowerManager) getSystemService(POWER_SERVICE);
            if (pm != null && !pm.isInteractive()) return;
            startActivity(new Intent(MainActivity.this, MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_REORDER_TO_FRONT));
        }
    }

    private void hover(int action, float x, float y) {
        long t = android.os.SystemClock.uptimeMillis();
        MotionEvent e = MotionEvent.obtain(t, t, action, x, y, 0);
        e.setSource(android.view.InputDevice.SOURCE_MOUSE);
        web.dispatchGenericMotionEvent(e);
        e.recycle();
    }

    static String toDataUrl(Drawable d) {
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
