package com.yoan.mytv;

import android.accessibilityservice.AccessibilityService;
import android.content.Intent;
import android.os.SystemClock;
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

    @Override public void onInterrupt() { }
}
