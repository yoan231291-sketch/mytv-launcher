package com.yoan.mytv;

import android.inputmethodservice.InputMethodService;
import android.os.SystemClock;
import android.view.KeyEvent;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputConnection;

/**
 * "Teclado" de MyTV: no muestra nada en pantalla, pero deja que el control del teléfono
 * mande teclas (flechas, OK, atrás) y texto a la app que esté abierta (YouTube, Netflix…).
 * Fire OS no permite que una app inyecte teclas en otra; un teclado sí puede.
 */
public class RemoteIme extends InputMethodService {
    static volatile RemoteIme current;
    private volatile boolean hasEditor;

    @Override public void onCreate() { super.onCreate(); current = this; }
    @Override public void onDestroy() { if (current == this) current = null; super.onDestroy(); }

    @Override public void onStartInput(EditorInfo info, boolean restarting) {
        super.onStartInput(info, restarting);
        hasEditor = info != null && info.inputType != EditorInfo.TYPE_NULL;
    }

    // Sin teclado en pantalla (las apps de TV como YouTube traen el suyo)
    @Override public boolean onEvaluateInputViewShown() { return false; }
    @Override public boolean onEvaluateFullscreenMode() { return false; }

    /** Manda una tecla a la app que tiene el foco. */
    static boolean key(int code) {
        RemoteIme me = current; if (me == null) return false;
        InputConnection ic = me.getCurrentInputConnection(); if (ic == null) return false;
        long t = SystemClock.uptimeMillis();
        boolean a = ic.sendKeyEvent(new KeyEvent(t, t, KeyEvent.ACTION_DOWN, code, 0, 0, -1, 0, KeyEvent.FLAG_SOFT_KEYBOARD | KeyEvent.FLAG_KEEP_TOUCH_MODE));
        boolean b = ic.sendKeyEvent(new KeyEvent(t, SystemClock.uptimeMillis(), KeyEvent.ACTION_UP, code, 0, 0, -1, 0, KeyEvent.FLAG_SOFT_KEYBOARD | KeyEvent.FLAG_KEEP_TOUCH_MODE));
        return a && b;
    }

    static boolean hasEditor() { RemoteIme me = current; return me != null && me.hasEditor && me.getCurrentInputConnection() != null; }

    /** Escribe texto: en un campo de texto real lo pega; si no, lo teclea letra por letra. */
    static boolean text(String s) {
        RemoteIme me = current; if (me == null) return false;
        InputConnection ic = me.getCurrentInputConnection(); if (ic == null) return false;
        if (me.hasEditor) return ic.commitText(s, 1);
        android.view.KeyCharacterMap km = android.view.KeyCharacterMap.load(android.view.KeyCharacterMap.VIRTUAL_KEYBOARD);
        KeyEvent[] evs = km.getEvents(s.toCharArray()); if (evs == null) return false;
        for (KeyEvent e : evs) ic.sendKeyEvent(e);
        return true;
    }
}
