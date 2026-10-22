package com.yoan.mytv;

import android.accessibilityservice.AccessibilityService;
import android.content.Intent;
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
        if (e.getEventType() != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return;
        CharSequence pkg = e.getPackageName();
        if (pkg == null || !AMAZON_HOME.contentEquals(pkg)) return;
        long now = SystemClock.elapsedRealtime();
        if (now < allowUntil || now - lastLaunch < 800) return;
        lastLaunch = now;
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

    @Override public void onInterrupt() { }
}
