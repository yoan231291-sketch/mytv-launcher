package com.yoan.mytv;

import android.accessibilityservice.AccessibilityService;
import android.content.Intent;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.view.accessibility.AccessibilityNodeInfo;
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

    // Revisión cada 4 s: si el inicio de Amazon quedó al frente (p. ej. al encender la TV) y no hubo evento, volvemos a MyTV
    private final Handler h = new Handler(Looper.getMainLooper());
    private final Runnable poll = new Runnable() {
        @Override public void run() {
            try {
                AccessibilityNodeInfo root = getRootInActiveWindow();
                if (root != null) {
                    CharSequence pkg = root.getPackageName();
                    root.recycle();
                    long now = SystemClock.elapsedRealtime();
                    if (pkg != null && AMAZON_HOME.contentEquals(pkg) && now >= allowUntil && now - lastLaunch > 3000) {
                        lastLaunch = now;
                        android.util.Log.i("MyTV", "inicio de Amazon al frente (revisión): abriendo MyTV");
                        startActivity(new Intent(HomeWatcherService.this, MainActivity.class)
                                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_REORDER_TO_FRONT));
                    }
                }
            } catch (Exception ignored) { }
            h.postDelayed(this, 4000);
        }
    };

    @Override protected void onServiceConnected() { super.onServiceConnected(); h.removeCallbacks(poll); h.postDelayed(poll, 4000); }
    @Override public void onDestroy() { h.removeCallbacks(poll); super.onDestroy(); }

    @Override public void onInterrupt() { }
}
