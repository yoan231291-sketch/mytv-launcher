package com.yoan.mytv;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.PixelFormat;
import android.hardware.display.DisplayManager;
import android.hardware.display.VirtualDisplay;
import android.media.Image;
import android.media.ImageReader;
import android.media.projection.MediaProjection;
import android.media.projection.MediaProjectionManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.util.Base64;

import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * Foto pequeña de lo que muestra la TV, para el control del teléfono ("¿la dejé prendida? ¿qué se ve?").
 * Usa el permiso de captura de pantalla de Android (se acepta una vez con "No volver a mostrar").
 * Solo captura cuando el teléfono lo pide; no graba nada ni guarda nada.
 * Netflix, Prime y otras apps protegidas salen en negro: eso lo bloquea la app, no se puede evitar.
 */
final class ScreenCap {
    static volatile MediaProjection projection;
    private static final HandlerThread thread = new HandlerThread("mytv-shot");
    static { thread.start(); }

    /** Pide el permiso (abre una pantalla transparente un instante). */
    static void ask(Context c) {
        c.startActivity(new Intent(c, Ask.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_NO_ANIMATION));
    }

    /** JPEG en base64 (data URL) de 480x270, o null si aún no hay permiso o falló. */
    static synchronized String shot() {
        MediaProjection mp = projection; if (mp == null) return null;
        final int w = 480, h = 270;
        ImageReader ir = ImageReader.newInstance(w, h, PixelFormat.RGBA_8888, 2);
        final Image[] got = new Image[1]; final CountDownLatch done = new CountDownLatch(1);
        Handler hd = new Handler(thread.getLooper());
        ir.setOnImageAvailableListener(r -> { if (got[0] == null) { got[0] = r.acquireLatestImage(); if (got[0] != null) done.countDown(); } }, hd);
        VirtualDisplay vd = null;
        try {
            vd = mp.createVirtualDisplay("mytv-shot", w, h, 160, DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR, ir.getSurface(), null, hd);
            if (!done.await(2500, TimeUnit.MILLISECONDS)) return null;
            Image img = got[0];
            Image.Plane pl = img.getPlanes()[0]; ByteBuffer buf = pl.getBuffer();
            int rowPad = pl.getRowStride() - pl.getPixelStride() * w;
            Bitmap bmp = Bitmap.createBitmap(w + rowPad / pl.getPixelStride(), h, Bitmap.Config.ARGB_8888);
            bmp.copyPixelsFromBuffer(buf); img.close();
            Bitmap crop = Bitmap.createBitmap(bmp, 0, 0, w, h);
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            crop.compress(Bitmap.CompressFormat.JPEG, 62, out);
            return "data:image/jpeg;base64," + Base64.encodeToString(out.toByteArray(), Base64.NO_WRAP);
        } catch (Exception e) {
            android.util.Log.i("MyTV", "foto: " + e);
            if (e instanceof SecurityException) projection = null;
            return null;
        } finally {
            if (vd != null) vd.release();
            ir.close();
        }
    }

    /** Pantalla invisible que pide el permiso de captura y se cierra sola. */
    public static class Ask extends Activity {
        @Override protected void onCreate(Bundle b) {
            super.onCreate(b);
            MediaProjectionManager m = (MediaProjectionManager) getSystemService(MEDIA_PROJECTION_SERVICE);
            startActivityForResult(m.createScreenCaptureIntent(), 1);
        }
        @Override protected void onActivityResult(int req, int res, Intent data) {
            if (res == RESULT_OK && data != null) {
                MediaProjectionManager m = (MediaProjectionManager) getSystemService(MEDIA_PROJECTION_SERVICE);
                projection = m.getMediaProjection(res, data);
                android.util.Log.i("MyTV", "foto: permiso listo");
            }
            finish(); overridePendingTransition(0, 0);
        }
    }
}
