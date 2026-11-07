package com.yoan.mytv;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.media.AudioManager;
import android.os.SystemClock;
import android.view.KeyEvent;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.URLDecoder;
import java.security.SecureRandom;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * Control remoto por Wi-Fi: el teléfono abre http://IP-DE-LA-TV:8686/?t=CLAVE y manda órdenes.
 * Corre dentro del servicio de accesibilidad (siempre activo), así funciona aunque haya otra app al frente.
 * La CLAVE se crea una sola vez en la TV; sin ella no se acepta ninguna orden.
 */
final class RemoteServer {
    static final int PORT = 8686;
    private final HomeWatcherService svc;
    private ServerSocket server;
    private volatile boolean running;
    private final String token;

    RemoteServer(HomeWatcherService svc) {
        this.svc = svc;
        SharedPreferences sp = svc.getSharedPreferences("remote", Context.MODE_PRIVATE);
        String t = sp.getString("token", null);
        if (t == null) {
            String abc = "abcdefghjkmnpqrstuvwxyz23456789"; SecureRandom r = new SecureRandom(); StringBuilder b = new StringBuilder();
            for (int i = 0; i < 8; i++) b.append(abc.charAt(r.nextInt(abc.length())));
            t = b.toString(); sp.edit().putString("token", t).apply();
        }
        token = t;
    }

    static String url(Context c) {
        String t = c.getSharedPreferences("remote", Context.MODE_PRIVATE).getString("token", "");
        return "http://" + ip() + ":" + PORT + "/?t=" + t;
    }

    static String ip() {
        try {
            for (NetworkInterface ni : Collections.list(NetworkInterface.getNetworkInterfaces())) {
                if (!ni.isUp() || ni.isLoopback()) continue;
                for (InetAddress a : Collections.list(ni.getInetAddresses()))
                    if (a instanceof Inet4Address && !a.isLoopbackAddress()) return a.getHostAddress();
            }
        } catch (Exception ignored) { }
        return "0.0.0.0";
    }

    void start() {
        if (running) return;
        running = true;
        new Thread(() -> {
            try {
                server = new ServerSocket(PORT);
                while (running) {
                    final Socket s = server.accept();
                    new Thread(() -> handle(s)).start();
                }
            } catch (Exception e) { android.util.Log.i("MyTV", "control remoto: " + e); }
        }, "mytv-remote").start();
    }

    void stop() { running = false; try { if (server != null) server.close(); } catch (Exception ignored) { } }

    private void handle(Socket s) {
        try (Socket sock = s) {
            sock.setSoTimeout(8000);
            BufferedReader in = new BufferedReader(new InputStreamReader(sock.getInputStream(), "UTF-8"));
            String line = in.readLine();
            if (line == null) return;
            String[] parts = line.split(" ");
            String full = parts.length > 1 ? parts[1] : "/";
            while ((line = in.readLine()) != null && !line.isEmpty()) { /* cabeceras: no se usan */ }
            String path = full.contains("?") ? full.substring(0, full.indexOf('?')) : full;
            Map<String, String> q = query(full.contains("?") ? full.substring(full.indexOf('?') + 1) : "");
            OutputStream out = sock.getOutputStream();

            if (path.equals("/") || path.equals("/index.html")) { send(out, 200, "text/html; charset=utf-8", asset("remote.html")); return; }
            if (path.equals("/manifest.json")) { send(out, 200, "application/manifest+json", asset("remote-manifest.json")); return; }
            if (!token.equals(q.get("t"))) { send(out, 403, "application/json", "{\"ok\":false,\"msg\":\"Clave incorrecta\"}".getBytes("UTF-8")); return; }

            JSONObject res = new JSONObject();
            if (path.equals("/api/apps")) res.put("apps", new JSONArray(Apps.list(svc)));
            else if (path.equals("/api/bt")) res.put("devices", Bluetooth.list());
            else if (path.equals("/api/cmd")) res = command(q.get("k"), q.get("v"));
            else { send(out, 404, "text/plain", "no".getBytes()); return; }
            send(out, 200, "application/json; charset=utf-8", res.toString().getBytes("UTF-8"));
        } catch (Exception ignored) { }
    }

    /** Ejecuta una orden del teléfono. */
    private JSONObject command(String k, String v) throws Exception {
        JSONObject r = new JSONObject().put("ok", true);
        if (k == null) return r.put("ok", false);
        MainActivity a = MainActivity.current;
        boolean front = a != null && a.isFront();
        AudioManager am = (AudioManager) svc.getSystemService(Context.AUDIO_SERVICE);
        switch (k) {
            case "up": case "down": case "left": case "right": case "ok":
                if (front) {
                    int code = k.equals("up") ? KeyEvent.KEYCODE_DPAD_UP : k.equals("down") ? KeyEvent.KEYCODE_DPAD_DOWN
                            : k.equals("left") ? KeyEvent.KEYCODE_DPAD_LEFT : k.equals("right") ? KeyEvent.KEYCODE_DPAD_RIGHT : KeyEvent.KEYCODE_ENTER;
                    a.remoteKey(code);
                } else r.put("ok", false).put("msg", "Las flechas solo mueven MyTV. En otras apps usa Atrás/Inicio o la app oficial de Fire TV.");
                break;
            case "back":
                if (front) a.remoteJs("tvKey('back')"); else svc.performGlobalAction(android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_BACK);
                break;
            case "home": openMyTV(); break;
            case "playpause": case "ff": case "rew":
                if (front && !k.equals("playpause")) a.remoteJs("tvKey('" + k + "')");
                else if (front) a.remoteJs("tvKey('playpause')");
                else media(am, k.equals("playpause") ? KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE : k.equals("ff") ? KeyEvent.KEYCODE_MEDIA_FAST_FORWARD : KeyEvent.KEYCODE_MEDIA_REWIND);
                break;
            case "volup": am.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_RAISE, AudioManager.FLAG_SHOW_UI); break;
            case "voldown": am.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_LOWER, AudioManager.FLAG_SHOW_UI); break;
            case "mute": am.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_TOGGLE_MUTE, AudioManager.FLAG_SHOW_UI); break;
            case "app":
                if (v == null || !Apps.launch(svc, v)) r.put("ok", false).put("msg", "Esa app no está instalada");
                break;
            case "city":
                openMyTV();
                final String id = v == null ? "" : v.replaceAll("[^a-z0-9_-]", "");
                MainActivity.pendingJs = "window.selectCityById && selectCityById('" + id + "')";
                if (a != null) a.remoteJs(MainActivity.pendingJs);
                break;
            case "cam": openMyTV(); if (a != null) a.remoteJs("tvKey('ff')"); break;
            case "apps": openMyTV(); if (a != null) a.remoteJs("tvKey('menu')"); break;
            case "bt": case "btoff": {
                String err = v == null ? "Falta el equipo" : Bluetooth.connect(v, k.equals("btoff"));
                if (err == null && k.equals("bt")) Bluetooth.remember(svc, v);
                if (err != null) r.put("ok", false).put("msg", err);
                break;
            }
            case "btsettings": Bluetooth.openSettings(svc); break;
            default: r.put("ok", false);
        }
        return r;
    }

    private void openMyTV() {
        svc.startActivity(new Intent(svc, MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_REORDER_TO_FRONT));
    }

    private static void media(AudioManager am, int code) {
        long t = SystemClock.uptimeMillis();
        am.dispatchMediaKeyEvent(new KeyEvent(t, t, KeyEvent.ACTION_DOWN, code, 0));
        am.dispatchMediaKeyEvent(new KeyEvent(t, t, KeyEvent.ACTION_UP, code, 0));
    }

    private byte[] asset(String name) throws Exception {
        try (InputStream in = svc.getAssets().open(name)) {
            ByteArrayOutputStream b = new ByteArrayOutputStream(); byte[] buf = new byte[8192]; int n;
            while ((n = in.read(buf)) > 0) b.write(buf, 0, n);
            return b.toByteArray();
        }
    }

    private static void send(OutputStream out, int code, String type, byte[] body) throws Exception {
        String head = "HTTP/1.1 " + code + (code == 200 ? " OK" : " Error") + "\r\nContent-Type: " + type +
                "\r\nContent-Length: " + body.length + "\r\nCache-Control: no-store\r\nConnection: close\r\n\r\n";
        out.write(head.getBytes("UTF-8")); out.write(body); out.flush();
    }

    private static Map<String, String> query(String qs) {
        Map<String, String> m = new HashMap<>();
        for (String p : qs.split("&")) {
            int i = p.indexOf('='); if (i <= 0) continue;
            try { m.put(p.substring(0, i), URLDecoder.decode(p.substring(i + 1), "UTF-8")); } catch (Exception ignored) { }
        }
        return m;
    }
}
