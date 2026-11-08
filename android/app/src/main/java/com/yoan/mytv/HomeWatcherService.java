package com.yoan.mytv;

import android.accessibilityservice.AccessibilityService;
import android.content.Intent;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.view.KeyEvent;
import android.view.accessibility.AccessibilityEvent;

/**
 * Fire OS no permite elegir otro launcher. Cuando el inicio de Amazon pasa al frente
 * (botón Home o encendido), este servicio abre MyTV en su lugar.
 */
public class HomeWatcherService extends AccessibilityService {

    private static final String AMAZON_HOME = "com.amazon.tv.launcher";
    private static volatile long allowUntil = 0;
    private long lastLaunch = 0;

    /** Deja pasar el inicio de Amazon un momento (p. ej. al abrir Ajustes desde MyTV). */
    static void allowAmazonHomeFor(long ms) { allowUntil = SystemClock.elapsedRealtime() + ms; }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent e) {
        int t = e.getEventType();
        // Cambio de pantalla, o foco que se mueve dentro del inicio de Amazon (p. ej. Home estando ya ahí)
        if (t != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED && t != AccessibilityEvent.TYPE_VIEW_FOCUSED) return;
        CharSequence pkg = e.getPackageName();
        if (pkg == null || !AMAZON_HOME.contentEquals(pkg)) return;
        long now = SystemClock.elapsedRealtime();
        if (now < allowUntil || now - lastLaunch < 1500) return;
        lastLaunch = now; android.util.Log.i("MyTV", "inicio de Amazon detectado: abriendo MyTV");
        cover.show();
        startActivity(new Intent(this, MainActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_REORDER_TO_FRONT));
    }

    // --- Botón Home del control: toque corto = MyTV al instante; mantener = inicio de Amazon ---
    private long homeDown = 0;

    @Override
    protected boolean onKeyEvent(KeyEvent e) {
        if (e.getKeyCode() != KeyEvent.KEYCODE_HOME) return false;
        if (e.getAction() == KeyEvent.ACTION_DOWN) {
            if (e.getRepeatCount() == 0) homeDown = SystemClock.elapsedRealtime();
            return true;
        }
        if (e.getAction() == KeyEvent.ACTION_UP) {
            long held = SystemClock.elapsedRealtime() - homeDown;
            if (held > 700) {                       // mantener presionado: ir al inicio de Amazon
                allowAmazonHomeFor(4000);
                performGlobalAction(GLOBAL_ACTION_HOME);
            } else {
                lastLaunch = SystemClock.elapsedRealtime();
                startActivity(new Intent(this, MainActivity.class)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_REORDER_TO_FRONT));
            }
            return true;
        }
        return true;
    }

    // Revisión cada 4 s con las estadísticas de uso (permiso concedido por ADB al instalar):
    // si el inicio de Amazon quedó al frente sin generar evento (p. ej. al encender la TV), volvemos a MyTV.
    private final Handler h = new Handler(Looper.getMainLooper());
    String foreground() {
        android.app.usage.UsageStatsManager um = (android.app.usage.UsageStatsManager) getSystemService(USAGE_STATS_SERVICE);
        if (um == null) return null;
        long now = System.currentTimeMillis();
        android.app.usage.UsageEvents ev = um.queryEvents(now - 10 * 60_000L, now);
        android.app.usage.UsageEvents.Event e = new android.app.usage.UsageEvents.Event();
        String last = null;
        while (ev.hasNextEvent()) { ev.getNextEvent(e); if (e.getEventType() == android.app.usage.UsageEvents.Event.MOVE_TO_FOREGROUND) last = e.getPackageName(); }
        return last;
    }
    private final Runnable poll = new Runnable() {
        @Override public void run() {
            try {
                long now = SystemClock.elapsedRealtime();
                if (AMAZON_HOME.equals(foreground()) && now >= allowUntil && now - lastLaunch > 3000) {
                    lastLaunch = now;
                    android.util.Log.i("MyTV", "inicio de Amazon al frente (revisión): abriendo MyTV");
                    cover.show();
                    startActivity(new Intent(HomeWatcherService.this, MainActivity.class)
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_REORDER_TO_FRONT));
                }
            } catch (Exception ignored) { }
            h.postDelayed(this, 4000);
        }
    };

    // Encendido/apagado de pantalla: reconectar el equipo de sonido Bluetooth al prender la TV
    private final android.content.BroadcastReceiver screen = new android.content.BroadcastReceiver() {
        @Override public void onReceive(android.content.Context c, Intent i) {
            if (Intent.ACTION_SCREEN_ON.equals(i.getAction())) Bluetooth.onScreenOn(HomeWatcherService.this);
            else Bluetooth.onScreenOff(HomeWatcherService.this);
        }
    };
    private boolean screenRegistered = false;

    static volatile HomeWatcherService instance;
    final HomeCover cover = new HomeCover(this);            // tapa el inicio de Amazon mientras abre MyTV
    final DrawOverlay drawOverlay = new DrawOverlay(this);   // dibujo del teléfono encima de cualquier app
    private RemoteServer remote;
    AdbSelf adb;
    @Override protected void onServiceConnected() { super.onServiceConnected(); instance = this;
        if (remote == null) { remote = new RemoteServer(this); remote.start(); Bluetooth.init(this); }
        if (!screenRegistered) { android.content.IntentFilter f = new android.content.IntentFilter(Intent.ACTION_SCREEN_ON); f.addAction(Intent.ACTION_SCREEN_OFF); registerReceiver(screen, f); screenRegistered = true; } h.removeCallbacks(poll); h.postDelayed(poll, 4000); }
    @Override public void onDestroy() { if (screenRegistered) { try { unregisterReceiver(screen); } catch (Exception ignored) { } } if (remote != null) remote.stop(); h.removeCallbacks(poll); super.onDestroy(); }

    @Override public void onInterrupt() { }

    /** Para la instalación: "adb shell dumpsys activity service com.yoan.mytv/.HomeWatcherService" muestra los enlaces del control. */
    @Override protected void dump(java.io.FileDescriptor fd, java.io.PrintWriter pw, String[] args) {
        pw.println("MYTV_CONTROL_CASA=" + RemoteServer.url(this));
        pw.println("MYTV_CONTROL_INTERNET=" + RemoteServer.onlineUrl(this));
        pw.println("MYTV_DIBUJO=" + RemoteServer.drawUrl(this));
        pw.println("MYTV_TECLADO=" + (RemoteIme.current != null ? "activo" : "inactivo"));
        pw.println("MYTV_FOTO=" + (ScreenCap.projection != null ? "lista" : "sin permiso"));
    }
}
