package com.yoan.mytv;

import android.accessibilityservice.AccessibilityService;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.view.WindowManager;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/**
 * Dibujo del teléfono ENCIMA de lo que se esté viendo (YouTube, Netflix, MyTV…), sin pausar ni cambiar de app.
 * Usa una ventana del servicio de accesibilidad: transparente, no toma el control ni el foco.
 */
final class DrawOverlay {
    private static final class Stroke { String id; int color; float w; final List<float[]> p = new ArrayList<>(); }

    private final AccessibilityService svc;
    private final Handler ui = new Handler(Looper.getMainLooper());
    private final List<Stroke> strokes = new ArrayList<>();
    private View view; private boolean shown;

    DrawOverlay(AccessibilityService s) { svc = s; }

    /** Llega un mensaje del canal de dibujo (se procesa en la pantalla principal). */
    void onEvent(final String event, final JSONObject p) { ui.post(() -> apply(event, p)); }

    private void apply(String event, JSONObject p) {
        try {
            switch (event) {
                case "start": { Stroke s = new Stroke(); s.id = p.optString("id"); s.color = parse(p.optString("c")); s.w = (float) p.optDouble("w", 7);
                    add(s.p, p.optJSONArray("p")); strokes.add(s); break; }
                case "pts": { Stroke s = find(p.optString("id")); if (s != null) add(s.p, p.optJSONArray("p")); break; }
                case "undo": { Stroke s = find(p.optString("id")); if (s != null) strokes.remove(s); break; }
                case "clear": case "hide": strokes.clear(); break;
                case "sync": {
                    if (!strokes.isEmpty()) return;
                    JSONArray all = p.optJSONArray("strokes"); if (all == null) return;
                    for (int i = 0; i < all.length(); i++) { JSONObject o = all.getJSONObject(i); Stroke s = new Stroke();
                        s.id = o.optString("id"); s.color = parse(o.optString("c")); s.w = (float) o.optDouble("w", 7); add(s.p, o.optJSONArray("p")); strokes.add(s); }
                    break;
                }
                default: return;
            }
        } catch (Exception ignored) { }
        refresh();
    }

    private void refresh() {
        WindowManager wm = (WindowManager) svc.getSystemService(Context.WINDOW_SERVICE);
        if (strokes.isEmpty()) {                      // sin dibujo: se quita la ventana (cero carga sobre el video)
            if (shown) { try { wm.removeView(view); } catch (Exception ignored) { } shown = false; }
            return;
        }
        if (view == null) view = new Canvas2();
        if (!shown) {
            WindowManager.LayoutParams lp = new WindowManager.LayoutParams(
                    WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.MATCH_PARENT,
                    WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
                            | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                    PixelFormat.TRANSLUCENT);
            try { wm.addView(view, lp); shown = true; } catch (Exception e) { android.util.Log.i("MyTV", "dibujo: " + e); return; }
        }
        view.invalidate();
    }

    private Stroke find(String id) { for (Stroke s : strokes) if (s.id.equals(id)) return s; return null; }

    private static void add(List<float[]> to, JSONArray pts) {
        if (pts == null) return;
        for (int i = 0; i < pts.length(); i++) { JSONArray xy = pts.optJSONArray(i); if (xy != null) to.add(new float[]{ (float) xy.optDouble(0), (float) xy.optDouble(1) }); }
    }

    private static int parse(String c) { try { return Color.parseColor(c); } catch (Exception e) { return Color.YELLOW; } }

    /** La hoja transparente donde se pintan los trazos (coordenadas 0..1 → pantalla). */
    private final class Canvas2 extends View {
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        Canvas2() {
            super(svc);
            paint.setStyle(Paint.Style.STROKE); paint.setStrokeCap(Paint.Cap.ROUND); paint.setStrokeJoin(Paint.Join.ROUND);
            setLayerType(LAYER_TYPE_SOFTWARE, null);   // la sombra de los trazos necesita dibujo por software
        }
        @Override protected void onDraw(Canvas c) {
            float W = getWidth(), H = getHeight(), sc = W / 1280f;
            paint.setShadowLayer(6 * sc, 0, 0, 0x73000000);
            for (Stroke s : strokes) {
                if (s.p.isEmpty()) continue;
                paint.setColor(s.color); paint.setStrokeWidth(s.w * sc);
                Path path = new Path(); float[] a = s.p.get(0); path.moveTo(a[0] * W, a[1] * H);
                if (s.p.size() == 1) path.lineTo(a[0] * W + 0.5f, a[1] * H);
                for (int i = 1; i < s.p.size(); i++) { float[] b = s.p.get(i); path.lineTo(b[0] * W, b[1] * H); }
                c.drawPath(path, paint);
            }
        }
    }
}
