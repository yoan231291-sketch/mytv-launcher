package com.yoan.mytv;

import android.accessibilityservice.AccessibilityService;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.os.Handler;
import android.os.Looper;
import android.view.KeyEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.ImageView;

/**
 * Tapa la pantalla de inicio de Amazon: Fire OS obliga a mostrarla al pulsar Inicio y hace esperar ~6 s
 * a cualquier otra app antes de abrirse. Durante esa espera ponemos encima la última imagen de MyTV
 * (y tragamos las teclas para que nada se abra por detrás). Se quita sola cuando MyTV ya está al frente.
 */
final class HomeCover {
    static volatile Bitmap lastMyTv;          // foto reciente de MyTV (se toma mientras MyTV está al frente)
    private final AccessibilityService svc;
    private final Handler ui = new Handler(Looper.getMainLooper());
    private View view; private boolean shown;
    private final Runnable timeout = this::hide;

    HomeCover(AccessibilityService s) { svc = s; }

    void show() {
        ui.post(() -> {
            if (shown) return;
            FrameLayout root = new FrameLayout(svc) {
                @Override public boolean dispatchKeyEvent(KeyEvent e) { return true; }   // ninguna tecla llega al inicio de Amazon
            };
            root.setBackgroundColor(Color.BLACK);
            Bitmap b = lastMyTv;
            if (b != null) { ImageView iv = new ImageView(svc); iv.setScaleType(ImageView.ScaleType.CENTER_CROP); iv.setImageBitmap(b); root.addView(iv); }
            root.setFocusable(true); root.setFocusableInTouchMode(true);
            WindowManager.LayoutParams lp = new WindowManager.LayoutParams(
                    WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.MATCH_PARENT,
                    WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                    PixelFormat.OPAQUE);
            try { ((WindowManager) svc.getSystemService(Context.WINDOW_SERVICE)).addView(root, lp); view = root; shown = true; root.requestFocus(); }
            catch (Exception e) { android.util.Log.i("MyTV", "tapa: " + e); }
            ui.removeCallbacks(timeout); ui.postDelayed(timeout, 10_000);   // por seguridad, nunca más de 10 s
        });
    }

    /** MyTV ya está al frente: quitar la tapa (con un instante para que MyTV termine de dibujarse). */
    void hide() {
        ui.postDelayed(() -> {
            if (!shown) return;
            try { ((WindowManager) svc.getSystemService(Context.WINDOW_SERVICE)).removeView(view); } catch (Exception ignored) { }
            shown = false; view = null; ui.removeCallbacks(timeout);
        }, 300);
    }
}
