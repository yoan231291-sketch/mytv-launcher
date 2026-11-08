package com.yoan.mytv;

import android.content.Context;
import android.util.Base64;

import java.io.DataInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.math.BigInteger;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.concurrent.LinkedBlockingQueue;

import javax.crypto.Cipher;

/**
 * Teclas para CUALQUIER app (YouTube, Netflix…): MyTV se conecta a la depuración ADB de la propia TV
 * (127.0.0.1:5555) y ejecuta "input keyevent", igual que el control físico.
 * La primera vez la TV pregunta "¿Permitir depuración?": se acepta una sola vez con "Permitir siempre".
 */
final class AdbSelf {
    private static final int CNXN = 0x4e584e43, AUTH = 0x48545541, OPEN = 0x4e45504f, OKAY = 0x59414b4f, CLSE = 0x45534c43, WRTE = 0x45545257;
    private static final byte[] SHA1_PREFIX = { 0x30, 0x21, 0x30, 0x09, 0x06, 0x05, 0x2b, 0x0e, 0x03, 0x02, 0x1a, 0x05, 0x00, 0x04, 0x14 };

    private final Context ctx;
    private final LinkedBlockingQueue<String> queue = new LinkedBlockingQueue<>();
    private volatile String state = "sin conectar";   // sin conectar | esperando permiso | listo
    private int attempt = 0;
    private Socket sock; private DataInputStream in; private OutputStream out;
    private int remoteId; private final Object okLock = new Object(); private boolean okPending;

    AdbSelf(Context c) { ctx = c.getApplicationContext(); new Thread(this::loop, "mytv-adb").start(); }

    boolean ready() { return "listo".equals(state); }
    String state() { return state; }

    /** Encola un comando de shell (p. ej. "input keyevent 22"). Devuelve false si aún no hay conexión. */
    boolean run(String cmd) { queue.offer(cmd); return ready(); }

    private void loop() {
        while (true) {
            try {
                connect();
                while (true) {
                    String c = queue.take();
                    // juntar varias teclas seguidas en una sola línea (más rápido al deslizar)
                    StringBuilder b = new StringBuilder(c);
                    String n; while ((n = queue.poll()) != null) b.append(" ; ").append(n);
                    write((b + "\n").getBytes("UTF-8"));
                }
            } catch (Exception e) {
                android.util.Log.i("MyTV", "adb: " + e);
                state = "sin conectar"; close();
                queue.clear();
                try { Thread.sleep(5000); } catch (InterruptedException ignored) { }
            }
        }
    }

    private void connect() throws Exception {
        KeyPair kp = keys();
        String host = (attempt++ % 2 == 0) ? RemoteServer.ip() : "127.0.0.1";
        sock = new Socket(); sock.connect(new InetSocketAddress(host, 5555), 4000); sock.setTcpNoDelay(true);
        android.util.Log.i("MyTV", "adb: conectando a " + host);
        in = new DataInputStream(sock.getInputStream()); out = sock.getOutputStream();
        send(CNXN, 0x01000000, 4096, "host::\0".getBytes("UTF-8"));
        boolean sentSig = false, sentKey = false;
        sock.setSoTimeout(0);
        while (true) {
            int[] h = new int[6]; byte[] data = read(h);
            if (h[0] == CNXN) break;
            android.util.Log.i("MyTV", "adb: recibido " + Integer.toHexString(h[0]) + " tipo " + h[1]);
            if (h[0] != AUTH || h[1] != 1) continue;
            if (!sentSig) {
                Cipher c = Cipher.getInstance("RSA/ECB/PKCS1Padding"); c.init(Cipher.ENCRYPT_MODE, kp.getPrivate());
                c.update(SHA1_PREFIX); byte[] sig = c.doFinal(data);
                send(AUTH, 2, 0, sig); sentSig = true;
            } else if (!sentKey) {
                state = "esperando permiso";   // la TV muestra el aviso: hay que aceptar una vez
                send(AUTH, 3, 0, (pubKey((RSAPublicKey) kp.getPublic()) + " mytv@tv\0").getBytes("UTF-8")); sentKey = true;
            }
        }
        send(OPEN, 1, 0, "shell:\0".getBytes("UTF-8"));
        int[] h = new int[6]; read(h);
        if (h[0] != OKAY) throw new IOException("shell rechazado");
        remoteId = h[1];
        // lector: confirma lo que manda la TV y libera la siguiente escritura
        new Thread(() -> {
            try {
                while (true) {
                    int[] x = new int[6]; read(x);
                    if (x[0] == WRTE) send(OKAY, 1, remoteId, null);
                    else if (x[0] == OKAY) synchronized (okLock) { okPending = false; okLock.notifyAll(); }
                    else if (x[0] == CLSE) { close(); return; }
                }
            } catch (Exception e) { close(); }
        }, "mytv-adb-read").start();
        state = "listo";
        android.util.Log.i("MyTV", "adb: listo para mandar teclas a cualquier app");
    }

    private void write(byte[] b) throws Exception {
        synchronized (okLock) {
            long until = System.currentTimeMillis() + 5000;
            while (okPending) { long w = until - System.currentTimeMillis(); if (w <= 0) throw new IOException("sin respuesta"); okLock.wait(w); }
            okPending = true;
        }
        send(WRTE, 1, remoteId, b);
    }

    private synchronized void send(int cmd, int a0, int a1, byte[] data) throws IOException {
        int len = data == null ? 0 : data.length, sum = 0;
        if (data != null) for (byte x : data) sum += x & 0xff;
        ByteBuffer bb = ByteBuffer.allocate(24 + len).order(ByteOrder.LITTLE_ENDIAN);
        bb.putInt(cmd).putInt(a0).putInt(a1).putInt(len).putInt(sum).putInt(cmd ^ 0xffffffff);
        if (data != null) bb.put(data);
        out.write(bb.array()); out.flush();
    }

    private byte[] read(int[] h) throws IOException {
        byte[] head = new byte[24]; in.readFully(head);
        ByteBuffer bb = ByteBuffer.wrap(head).order(ByteOrder.LITTLE_ENDIAN);
        for (int i = 0; i < 6; i++) h[i] = bb.getInt();
        byte[] d = new byte[h[3]]; in.readFully(d); return d;
    }

    private void close() {
        state = "sin conectar";
        try { if (sock != null) sock.close(); } catch (Exception ignored) { }
        synchronized (okLock) { okPending = false; okLock.notifyAll(); }
    }

    // ---- Clave RSA propia de MyTV (se crea una vez y se guarda en la app) ----
    private KeyPair keys() throws Exception {
        File pri = new File(ctx.getFilesDir(), "adbkey"), pub = new File(ctx.getFilesDir(), "adbkey.pub");
        KeyFactory kf = KeyFactory.getInstance("RSA");
        if (pri.exists() && pub.exists()) {
            return new KeyPair(kf.generatePublic(new X509EncodedKeySpec(readAll(pub))), kf.generatePrivate(new PKCS8EncodedKeySpec(readAll(pri))));
        }
        KeyPairGenerator g = KeyPairGenerator.getInstance("RSA"); g.initialize(2048);
        KeyPair kp = g.generateKeyPair();
        try (FileOutputStream f = new FileOutputStream(pri)) { f.write(kp.getPrivate().getEncoded()); }
        try (FileOutputStream f = new FileOutputStream(pub)) { f.write(kp.getPublic().getEncoded()); }
        return kp;
    }

    private static byte[] readAll(File f) throws IOException {
        byte[] b = new byte[(int) f.length()];
        try (DataInputStream d = new DataInputStream(new FileInputStream(f))) { d.readFully(b); }
        return b;
    }

    /** Clave pública en el formato que espera adbd (estructura RSAPublicKey de Android, en base64). */
    private static String pubKey(RSAPublicKey k) {
        final int words = 64;
        BigInteger r32 = BigInteger.ZERO.setBit(32), n = k.getModulus();
        BigInteger rr = BigInteger.ZERO.setBit(words * 32).modPow(BigInteger.valueOf(2), n);
        BigInteger n0inv = n.remainder(r32).modInverse(r32);
        ByteBuffer bb = ByteBuffer.allocate(4 + 4 + words * 4 + words * 4 + 4).order(ByteOrder.LITTLE_ENDIAN);
        bb.putInt(words); bb.putInt(n0inv.negate().intValue());
        BigInteger t = n; for (int i = 0; i < words; i++) { BigInteger[] qr = t.divideAndRemainder(r32); t = qr[0]; bb.putInt(qr[1].intValue()); }
        t = rr; for (int i = 0; i < words; i++) { BigInteger[] qr = t.divideAndRemainder(r32); t = qr[0]; bb.putInt(qr[1].intValue()); }
        bb.putInt(k.getPublicExponent().intValue());
        return Base64.encodeToString(bb.array(), Base64.NO_WRAP);
    }
}
