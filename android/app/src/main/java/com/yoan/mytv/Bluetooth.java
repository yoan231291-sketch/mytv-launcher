package com.yoan.mytv;

import android.bluetooth.BluetoothA2dp;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothClass;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothProfile;
import android.content.Context;
import android.content.Intent;

import org.json.JSONArray;
import org.json.JSONObject;

/**
 * Bluetooth para el control del teléfono: lista los equipos emparejados con la TV
 * (equipo de sonido, audífonos…) y conecta el que elijas, sin entrar a Ajustes.
 */
final class Bluetooth {
    private static volatile BluetoothA2dp a2dp;

    /** Pide al sistema el perfil de audio (A2DP); llega de forma asíncrona y se guarda. */
    static void init(Context c) {
        BluetoothAdapter ad = BluetoothAdapter.getDefaultAdapter();
        if (ad == null) return;
        ad.getProfileProxy(c.getApplicationContext(), new BluetoothProfile.ServiceListener() {
            @Override public void onServiceConnected(int p, BluetoothProfile proxy) { a2dp = (BluetoothA2dp) proxy; }
            @Override public void onServiceDisconnected(int p) { a2dp = null; }
        }, BluetoothProfile.A2DP);
    }

    static JSONArray list() {
        JSONArray out = new JSONArray();
        BluetoothAdapter ad = BluetoothAdapter.getDefaultAdapter();
        if (ad == null) return out;
        try {
            for (BluetoothDevice d : ad.getBondedDevices()) {
                BluetoothClass bc = d.getBluetoothClass();
                if (bc != null && (bc.getMajorDeviceClass() == BluetoothClass.Device.Major.PERIPHERAL
                        || bc.getMajorDeviceClass() == BluetoothClass.Device.Major.PHONE)) continue;   // sin controles, teclados ni teléfonos
                BluetoothA2dp p = a2dp;
                boolean on = p != null && p.getConnectionState(d) == BluetoothProfile.STATE_CONNECTED;
                out.put(new JSONObject().put("name", d.getName() == null ? d.getAddress() : d.getName())
                        .put("addr", d.getAddress()).put("on", on));
            }
        } catch (Exception ignored) { }
        return out;
    }

    /** Conecta (o desconecta) el audio a un equipo emparejado. */
    static String connect(String addr, boolean off) {
        BluetoothAdapter ad = BluetoothAdapter.getDefaultAdapter();
        if (ad == null) return "Esta TV no tiene Bluetooth";
        if (!ad.isEnabled()) { ad.enable(); return "Encendiendo el Bluetooth… inténtalo otra vez en 5 segundos"; }
        BluetoothA2dp p = a2dp;
        if (p == null) return "El Bluetooth aún no está listo, inténtalo otra vez";
        try {
            BluetoothDevice d = ad.getRemoteDevice(addr);
            if (!off) {   // solo un equipo de audio a la vez: suelta el que esté conectado
                for (BluetoothDevice o : p.getConnectedDevices())
                    if (!o.getAddress().equals(addr)) BluetoothA2dp.class.getMethod("disconnect", BluetoothDevice.class).invoke(p, o);
            }
            Object ok = BluetoothA2dp.class.getMethod(off ? "disconnect" : "connect", BluetoothDevice.class).invoke(p, d);
            return Boolean.TRUE.equals(ok) ? null : "La TV no aceptó la conexión. Revisa que el equipo esté prendido.";
        } catch (Exception e) {
            return "No se pudo conectar desde aquí. Usa “Ajustes de Bluetooth”.";
        }
    }

    // ---- Reconexión automática del equipo de sonido al prender la TV ----
    private static final android.os.Handler H = new android.os.Handler(android.os.Looper.getMainLooper());

    /** Pantalla apagada: recuerda qué equipo de audio estaba conectado. */
    static void onScreenOff(Context c) {
        H.removeCallbacksAndMessages(null);
        BluetoothA2dp p = a2dp;
        if (p == null) return;
        try {
            for (BluetoothDevice d : p.getConnectedDevices())
                c.getSharedPreferences("bt", Context.MODE_PRIVATE).edit().putString("last", d.getAddress()).apply();
        } catch (Exception ignored) { }
    }

    /** Pantalla encendida: si no hay audio Bluetooth conectado, reconecta el último (varios intentos: el equipo puede tardar en despertar). */
    static void onScreenOn(final Context c) {
        H.removeCallbacksAndMessages(null);
        for (long ms : new long[]{ 4000, 12000, 30000, 60000 }) H.postDelayed(() -> reconnect(c), ms);
    }

    private static void reconnect(Context c) {
        String last = c.getSharedPreferences("bt", Context.MODE_PRIVATE).getString("last", null);
        BluetoothA2dp p = a2dp;
        if (last == null) return;
        if (p == null) { init(c); return; }
        try {
            if (!p.getConnectedDevices().isEmpty()) { H.removeCallbacksAndMessages(null); return; }   // ya hay audio conectado
            android.util.Log.i("MyTV", "Bluetooth: reconectando equipo de sonido " + last);
            connect(last, false);
        } catch (Exception ignored) { }
    }

    /** Recuerda el equipo elegido desde el teléfono. */
    static void remember(Context c, String addr) { c.getSharedPreferences("bt", Context.MODE_PRIVATE).edit().putString("last", addr).apply(); }

    /** Abre la pantalla de Bluetooth de Fire TV (Controles y dispositivos Bluetooth). */
    static void openSettings(Context c) {
        HomeWatcherService.allowAmazonHomeFor(8000);
        Intent i = new Intent().setClassName("com.amazon.tv.settings.v2",
                "com.amazon.tv.settings.v2.tv.controllers_bluetooth_devices.ControllersAndBluetoothActivity");
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        try { c.startActivity(i); }
        catch (Exception e) { c.startActivity(new Intent(android.provider.Settings.ACTION_BLUETOOTH_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)); }
    }
}
