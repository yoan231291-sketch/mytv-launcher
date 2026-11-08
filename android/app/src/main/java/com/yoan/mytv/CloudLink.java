package com.yoan.mytv;

import android.util.Log;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.DataInputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

import javax.net.ssl.SSLSocketFactory;

/**
 * Control desde cualquier lugar: la TV se queda conectada a un canal en vivo de internet
 * (Supabase Realtime, solo canal, sin base de datos). El teléfono manda órdenes al canal y la TV responde.
 * Corre dentro del servicio de accesibilidad, así funciona aunque haya otra app al frente.
 */
final class CloudLink {
    static final String HOST = "eythwqbeatakgsvjhrbl.supabase.co";
    static final String KEY = "sb_publishable_CPOL_oOUFhvdJ1sSDqK_Kg_46iP00Cj";

    interface Api { JSONObject handle(String path, Map<String, String> q) throws Exception; }
    interface Draw { void onEvent(String event, JSONObject payload); }

    private final String topic, drawTopic;
    private final Api api;
    private final Draw draw;
    private volatile boolean running = true;
    private Socket sock; private OutputStream out;
    private int ref = 1;
    private final SecureRandom rnd = new SecureRandom();

    CloudLink(String channelKey, Api api, Draw draw) {
        this.topic = "realtime:mytv-remote-" + channelKey;
        this.drawTopic = "realtime:mytv-draw-" + channelKey;
        this.api = api; this.draw = draw;
        new Thread(this::loop, "mytv-cloud").start();
    }

    void stop() { running = false; try { if (sock != null) sock.close(); } catch (Exception ignored) { } }

    private void loop() {
        long wait = 2000;
        while (running) {
            try {
                connect();
                wait = 2000;
                read();   // vuelve cuando se corta la conexión
            } catch (Exception e) { Log.i("MyTV", "nube: " + e); }
            try { if (sock != null) sock.close(); } catch (Exception ignored) { }
            if (!running) return;
            try { Thread.sleep(wait); } catch (InterruptedException ignored) { }
            wait = Math.min(wait * 2, 60_000);   // reintenta: 2 s, 4 s… hasta 1 min
        }
    }

    private void connect() throws Exception {
        sock = SSLSocketFactory.getDefault().createSocket(HOST, 443);
        sock.setSoTimeout(70_000);
        out = sock.getOutputStream();
        byte[] k = new byte[16]; rnd.nextBytes(k);
        String req = "GET /realtime/v1/websocket?apikey=" + KEY + "&vsn=1.0.0 HTTP/1.1\r\nHost: " + HOST +
                "\r\nUpgrade: websocket\r\nConnection: Upgrade\r\nSec-WebSocket-Version: 13\r\nSec-WebSocket-Key: " +
                android.util.Base64.encodeToString(k, android.util.Base64.NO_WRAP) + "\r\n\r\n";
        out.write(req.getBytes(StandardCharsets.UTF_8)); out.flush();
        DataInputStream in = new DataInputStream(sock.getInputStream());
        StringBuilder head = new StringBuilder();
        while (!head.toString().endsWith("\r\n\r\n")) head.append((char) in.readUnsignedByte());
        if (!head.toString().startsWith("HTTP/1.1 101")) throw new IOException("sin websocket: " + head.toString().split("\r\n")[0]);
        // entrar al canal (la TV aparece como "tv" en presencia)
        JSONObject cfg = new JSONObject().put("config", new JSONObject()
                .put("broadcast", new JSONObject().put("self", false))
                .put("presence", new JSONObject().put("key", "tv")));
        for (String t : new String[]{ topic, drawTopic }) {
            sendMsg(t, "phx_join", cfg);
            sendMsg(t, "presence", new JSONObject().put("type", "presence").put("event", "track").put("payload", new JSONObject().put("at", System.currentTimeMillis())));
        }
        // canal de dibujo: aviso que la TV llegó, para que el teléfono mande el dibujo que ya tenga
        sendMsg(drawTopic, "broadcast", new JSONObject().put("type", "broadcast").put("event", "hello").put("payload", new JSONObject()));
        // latido cada 25 s para que no se corte
        final Socket mine = sock;
        new Thread(() -> {
            while (running && mine == sock && !mine.isClosed()) {
                try { Thread.sleep(25_000); sendMsg("phoenix", "heartbeat", new JSONObject()); } catch (Exception e) { return; }
            }
        }, "mytv-cloud-hb").start();
        Log.i("MyTV", "nube: conectada");
    }

    private void read() throws Exception {
        DataInputStream in = new DataInputStream(sock.getInputStream());
        StringBuilder msg = new StringBuilder();
        while (running) {
            int b0 = in.readUnsignedByte(), b1 = in.readUnsignedByte();
            int op = b0 & 0x0f; boolean fin = (b0 & 0x80) != 0;
            long len = b1 & 0x7f;
            if (len == 126) len = in.readUnsignedShort(); else if (len == 127) len = in.readLong();
            byte[] data = new byte[(int) len]; in.readFully(data);
            if (op == 8) return;                                      // cierre
            if (op == 9) { frame(10, data); continue; }               // ping → pong
            if (op == 1 || op == 0) {
                msg.append(new String(data, StandardCharsets.UTF_8));
                if (fin) { handle(msg.toString()); msg.setLength(0); }
            }
        }
    }

    private void handle(String raw) {
        try {
            JSONObject m = new JSONObject(raw);
            if (!"broadcast".equals(m.optString("event"))) return;
            JSONObject b = m.getJSONObject("payload");
            if (drawTopic.equals(m.optString("topic"))) { if (draw != null) draw.onEvent(b.optString("event"), b.optJSONObject("payload")); return; }
            if (!"req".equals(b.optString("event"))) return;
            final JSONObject p = b.getJSONObject("payload");
            new Thread(() -> answer(p)).start();
        } catch (Exception e) { Log.i("MyTV", "nube: mensaje " + e); }
    }

    /** Ejecuta la orden con la misma lógica del control de la casa y responde al teléfono. */
    private void answer(JSONObject p) {
        String id = p.optString("id");
        try {
            Map<String, String> q = new HashMap<>();
            JSONObject args = p.optJSONObject("q");
            if (args != null) for (Iterator<String> it = args.keys(); it.hasNext(); ) { String k = it.next(); q.put(k, args.optString(k)); }
            String path = p.optString("path");
            JSONObject body = api.handle(path, q);
            JSONArray icons = null;
            if (path.equals("/api/apps") && body.has("apps")) {           // los íconos van aparte (los mensajes deben ser pequeños)
                JSONArray apps = body.getJSONArray("apps"); icons = new JSONArray();
                for (int i = 0; i < apps.length(); i++) {
                    JSONObject a = apps.getJSONObject(i);
                    icons.put(new JSONObject().put("pkg", a.optString("pkg")).put("icon", a.optString("icon")));
                    a.remove("icon");
                }
            }
            broadcast("res", new JSONObject().put("id", id).put("body", body));
            if (icons != null) for (int i = 0; i < icons.length(); i++) broadcast("icon", icons.getJSONObject(i));
        } catch (Exception e) {
            try { broadcast("res", new JSONObject().put("id", id).put("body", new JSONObject().put("ok", false).put("msg", "Error en la TV"))); } catch (Exception ignored) { }
        }
    }

    private void broadcast(String event, JSONObject payload) throws Exception {
        sendMsg(topic, "broadcast", new JSONObject().put("type", "broadcast").put("event", event).put("payload", payload));
    }

    private synchronized void sendMsg(String t, String event, JSONObject payload) throws Exception {
        JSONObject m = new JSONObject().put("topic", t).put("event", event).put("payload", payload).put("ref", String.valueOf(ref++));
        frame(1, m.toString().getBytes(StandardCharsets.UTF_8));
    }

    /** Envía un frame WebSocket (enmascarado, como exige el protocolo para clientes). */
    private synchronized void frame(int op, byte[] data) throws IOException {
        int n = data.length;
        java.io.ByteArrayOutputStream f = new java.io.ByteArrayOutputStream(n + 14);
        f.write(0x80 | op);
        if (n < 126) f.write(0x80 | n);
        else if (n < 65536) { f.write(0x80 | 126); f.write(n >> 8); f.write(n & 0xff); }
        else { f.write(0x80 | 127); for (int i = 7; i >= 0; i--) f.write((int) (((long) n >> (8 * i)) & 0xff)); }
        byte[] mask = new byte[4]; rnd.nextBytes(mask); f.write(mask, 0, 4);
        for (int i = 0; i < n; i++) f.write(data[i] ^ mask[i & 3]);
        out.write(f.toByteArray()); out.flush();
    }
}
