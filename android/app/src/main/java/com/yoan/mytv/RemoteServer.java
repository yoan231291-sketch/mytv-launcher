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
        return "http://" + ip() + ":" + PORT + "/k/" + t + "/";
    }

    /** Clave del dibujo desde fuera de casa (canal en vivo de internet). Se crea una vez. */
    static String drawKey(Context c) {
        SharedPreferences sp = c.getSharedPreferences("remote", Context.MODE_PRIVATE);
        String k = sp.getString("draw", null);
        if (k == null) {
            String abc = "abcdefghjkmnpqrstuvwxyz23456789"; SecureRandom r = new SecureRandom(); StringBuilder b = new StringBuilder();
            for (int i = 0; i < 16; i++) b.append(abc.charAt(r.nextInt(abc.length())));
            k = b.toString(); sp.edit().putString("draw", k).apply();
        }
        return k;
    }
    static String onlineUrl(Context c) { return "https://yoan231291-sketch.github.io/mytv-launcher/remote.html?k=" + drawKey(c); }
    static String drawUrl(Context c) { return "https://yoan231291-sketch.github.io/mytv-launcher/draw.html#k=" + drawKey(c); }

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

    private CloudLink cloud;

    void start() {
        if (running) return;
        running = true;
        cloud = new CloudLink(drawKey(svc), this::api, svc.drawOverlay::onEvent);   // control desde cualquier lugar (internet)
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

    void stop() { running = false; if (cloud != null) cloud.stop(); try { if (server != null) server.close(); } catch (Exception ignored) { } }

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

            // La clave va DENTRO de la dirección (/k/CLAVE/): así el ícono de la pantalla de inicio del iPhone
            // la conserva y el mismo enlace se puede compartir con la familia.
            if (path.startsWith("/k/")) {
                String rest = path.substring(3); int sl = rest.indexOf('/');
                String pk = sl < 0 ? rest : rest.substring(0, sl), sub = sl < 0 ? "" : rest.substring(sl);
                if (sl < 0) { redirect(out, "/k/" + pk + "/"); return; }
                if (!token.equals(pk)) { send(out, 403, "text/html; charset=utf-8", asset("remote.html")); return; }
                if (sub.equals("/") || sub.equals("/index.html")) { send(out, 200, "text/html; charset=utf-8", asset("remote.html")); return; }
                if (sub.equals("/manifest.json")) { send(out, 200, "application/manifest+json", asset("remote-manifest.json")); return; }
                if (!sub.startsWith("/api/")) { send(out, 404, "text/plain", "no".getBytes()); return; }
                path = sub; q.put("t", pk);
            }
            if (path.equals("/") || path.equals("/index.html")) {
                if (token.equals(q.get("t"))) { redirect(out, "/k/" + token + "/"); return; }   // enlaces viejos con ?t=
                send(out, 200, "text/html; charset=utf-8", asset("remote.html")); return;
            }
            if (path.equals("/manifest.json")) { send(out, 200, "application/manifest+json", asset("remote-manifest.json")); return; }
            if (!token.equals(q.get("t"))) { send(out, 403, "application/json", "{\"ok\":false,\"msg\":\"Clave incorrecta\"}".getBytes("UTF-8")); return; }

            JSONObject res = api(path, q);
            if (res == null) { send(out, 404, "text/plain", "no".getBytes()); return; }
            send(out, 200, "application/json; charset=utf-8", res.toString().getBytes("UTF-8"));
        } catch (Exception ignored) { }
    }

    /** Respuestas del control (iguales en la casa por Wi-Fi y desde internet). null = no existe. */
    JSONObject api(String path, Map<String, String> q) throws Exception {
        JSONObject res = new JSONObject();
        if (path.equals("/api/apps")) res.put("apps", new JSONArray(Apps.list(svc)));
        else if (path.equals("/api/bt")) res.put("devices", Bluetooth.list());
        else if (path.equals("/api/state")) res.put("on", screenOn()).put("keys", RemoteIme.current != null ? "listo" : "sin teclado")
                .put("draw", drawUrl(svc)).put("online", onlineUrl(svc)).put("lan", url(svc));
        else if (path.equals("/api/cmd") && "voice".equals(q.get("k"))) res = voice(q.get("v"), q.get("c"));
        else if (path.equals("/api/cmd")) res = command(q.get("k"), q.get("v"));
        else if (path.equals("/api/now")) res = NowPlaying.info(svc, screenOn());
        else if (path.equals("/api/shot")) {           // foto de la pantalla (solo si está prendida)
            if (!screenOn()) res.put("off", true);
            else if (ScreenCap.projection == null) { ScreenCap.ask(svc); res.put("wait", true); }
            else { String img = ScreenCap.shot(); if (img != null) res.put("img", img); else res.put("wait", true); }
        }
        else return null;
        return res;
    }

    /** Orden de voz (botón Alexa del teléfono): se interpreta y se ejecuta aquí, con lo que ya tiene la TV. */
    private JSONObject voice(String text, String cities) throws Exception {
        JSONObject r = new JSONObject().put("ok", true);
        if (text == null || text.trim().isEmpty()) return r.put("ok", false).put("msg", "No escuché nada");
        Voice.Plan p = Voice.plan(text, Apps.labels(svc), cities);
        if (p.said == null) return r.put("ok", false).put("msg", "No entendí: “" + text + "”. Prueba: “pon Netflix”, “busca recetas en YouTube”, “sube el volumen”.");
        for (String[] c : p.cmds) {
            String k = c[0], v = c[1];
            if (k.equals("power_on")) { if (!screenOn()) command("power", null); }
            else if (k.equals("power_off")) { if (screenOn()) command("power", null); }
            else if (k.equals("text_yt")) { youtubeSearch(v); }
            else command(k, v.isEmpty() ? null : v);
            if (p.cmds.length > 1) Thread.sleep(120);
        }
        return r.put("msg", p.said);
    }

    private void youtubeSearch(String t) {
        if (!screenOn()) command0Power();
        Intent yi = new Intent(Intent.ACTION_VIEW, android.net.Uri.parse("youtube://search?query=" + android.net.Uri.encode(t))).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        String pkg = Apps.installed(svc, "com.amazon.firetv.youtube,com.google.android.youtube.tv");
        if (pkg != null) yi.setPackage(pkg);
        try { svc.startActivity(yi); } catch (Exception ignored) { }
    }
    private void command0Power() { try { command("power", null); Thread.sleep(800); } catch (Exception ignored) { } }

    /** Ejecuta una orden del teléfono. */
    private JSONObject command(String k, String v) throws Exception {
        JSONObject r = new JSONObject().put("ok", true);
        if (k == null) return r.put("ok", false);
        MainActivity a = MainActivity.current;
        boolean front = a != null && a.isFront();
        AudioManager am = (AudioManager) svc.getSystemService(Context.AUDIO_SERVICE);
        switch (k) {
            case "up": case "down": case "left": case "right": case "ok": {
                int code = k.equals("up") ? KeyEvent.KEYCODE_DPAD_UP : k.equals("down") ? KeyEvent.KEYCODE_DPAD_DOWN
                        : k.equals("left") ? KeyEvent.KEYCODE_DPAD_LEFT : k.equals("right") ? KeyEvent.KEYCODE_DPAD_RIGHT : KeyEvent.KEYCODE_DPAD_CENTER;
                if (front) a.remoteKey(code == KeyEvent.KEYCODE_DPAD_CENTER ? KeyEvent.KEYCODE_ENTER : code);   // MyTV: directo, sin demora
                else if (!key(code)) r.put("ok", false).put("msg", adbMsg());
                break;
            }
            case "back":
                if (front) a.remoteJs("tvKey('back')");
                else if (!key(KeyEvent.KEYCODE_BACK)) svc.performGlobalAction(android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_BACK);
                break;
            case "home": openMyTV(); break;
            case "playpause": case "ff": case "rew": {
                int code = k.equals("playpause") ? KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE : k.equals("ff") ? KeyEvent.KEYCODE_MEDIA_FAST_FORWARD : KeyEvent.KEYCODE_MEDIA_REWIND;
                if (front) a.remoteJs("tvKey('" + k + "')");
                else if (!key(code)) media(am, code);
                break;
            }
            case "text": {   // escribir en buscadores (YouTube, Netflix…): solo letras sin acentos, números y signos simples
                String t = v == null ? "" : java.text.Normalizer.normalize(v, java.text.Normalizer.Form.NFD).replaceAll("[^A-Za-z0-9 .,@_\\-]", "");
                if (t.isEmpty()) { r.put("ok", false).put("msg", "Nada que escribir"); break; }
                String fg = null; try { fg = svc.foreground(); } catch (Exception ignored) { }
                boolean yt = front || (fg != null && fg.contains("youtube"));
                if (RemoteIme.hasEditor()) { RemoteIme.text(t); }                 // hay un campo de texto abierto: se escribe ahí
                else if (yt) {                                                       // YouTube (o MyTV): abre la búsqueda con el texto ya puesto
                    Intent yi = new Intent(Intent.ACTION_VIEW, android.net.Uri.parse("youtube://search?query=" + android.net.Uri.encode(t)))
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    String pkg = Apps.installed(svc, "com.amazon.firetv.youtube,com.google.android.youtube.tv");
                    if (pkg != null) yi.setPackage(pkg);
                    try { svc.startActivity(yi); r.put("msg", "Buscando en YouTube"); } catch (Exception e) { r.put("ok", false).put("msg", "No se pudo abrir YouTube"); }
                }
                else if (!RemoteIme.text(t)) r.put("ok", false).put("msg", "Primero abre el buscador de la app en la TV");
                break;
            }
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
            case "mosaic": {   // 4 ciudades a la vez (v = "1" prender, "0" apagar, vacío = alternar)
                openMyTV();
                String js = "window.toggleMosaic && toggleMosaic(" + ("1".equals(v) ? "true" : "0".equals(v) ? "false" : "") + ")";
                if (a != null) a.remoteJs(js); else MainActivity.pendingJs = js;
                break;
            }
            case "cam": openMyTV(); if (a != null) a.remoteJs("tvKey('ff')"); break;
            case "apps": openMyTV(); if (a != null) a.remoteJs("tvKey('menu')"); break;
            case "bt": case "btoff": {
                String err = v == null ? "Falta el equipo" : Bluetooth.connect(v, k.equals("btoff"));
                if (err == null && k.equals("bt")) Bluetooth.remember(svc, v);
                if (err != null) r.put("ok", false).put("msg", err);
                break;
            }
            case "power":
                if (!screenOn()) {      // prender: despierta la pantalla y abre MyTV
                    android.os.PowerManager pm = (android.os.PowerManager) svc.getSystemService(Context.POWER_SERVICE);
                    @SuppressWarnings("deprecation") android.os.PowerManager.WakeLock wl = pm.newWakeLock(
                            android.os.PowerManager.FULL_WAKE_LOCK | android.os.PowerManager.ACQUIRE_CAUSES_WAKEUP, "mytv:remote-power");
                    wl.acquire(5000);
                    openMyTV();
                    r.put("on", true);
                } else {                // apagar: igual que el botón de encendido del control
                    boolean ok = svc.performGlobalAction(8 /* GLOBAL_ACTION_LOCK_SCREEN (Android 9+) */);
                    r.put("on", !ok);
                    if (!ok) r.put("ok", false).put("msg", "No se pudo apagar desde aquí");
                }
                break;
            case "alexa":   // igual que el botón de Alexa del control: Alexa escucha (por el micrófono de la TV)
                try { svc.startActivity(new Intent(Intent.ACTION_ASSIST).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)); }
                catch (Exception e) { r.put("ok", false).put("msg", "Alexa no respondió"); }
                break;
            case "btsettings": Bluetooth.openSettings(svc); break;
            default: r.put("ok", false);
        }
        return r;
    }

    /** Tecla a cualquier app por ADB local. */
    private boolean key(int code) { return RemoteIme.key(code) || (svc.adb != null && svc.adb.ready() && svc.adb.run("input keyevent " + code)); }
    private String adbMsg() { return "Esa tecla no llegó a la app de la TV. Toca Inicio y vuelve a abrir la app."; }

    private boolean screenOn() {
        android.os.PowerManager pm = (android.os.PowerManager) svc.getSystemService(Context.POWER_SERVICE);
        return pm == null || pm.isInteractive();
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

    private static void redirect(OutputStream out, String to) throws Exception {
        out.write(("HTTP/1.1 302 Found\r\nLocation: " + to + "\r\nContent-Length: 0\r\nConnection: close\r\n\r\n").getBytes("UTF-8")); out.flush();
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
