#!/usr/bin/env python3
"""
MyTV relay (corre en el Mac). La TV pide /play?ids=a,b,c,d y recibe UN solo video HLS limpio:
sin reproductor de YouTube (sin anuncios, sin ruedita, sin textos), con 1-4 cámaras juntas en cuadrícula.
  GET /play?ids=ID[,ID,ID,ID]   -> arranca (o reutiliza) la sesión y devuelve {"url": "/s/N/index.m3u8"}
  GET /stop                      -> detiene
  GET /health                    -> {"ok":true}
Solo biblioteca estándar + yt-dlp + ffmpeg.
"""
from concurrent.futures import ThreadPoolExecutor
import json, os, re, shutil, signal, socket, subprocess, sys, threading, time, urllib.request
from http.server import ThreadingHTTPServer, SimpleHTTPRequestHandler
from urllib.parse import urlparse, parse_qs

PORT = int(os.environ.get("RELAY_PORT", "8700"))
ROOT = os.path.join(os.path.dirname(os.path.abspath(__file__)), "out")
YTDLP, FFMPEG = shutil.which("yt-dlp") or "/opt/homebrew/bin/yt-dlp", shutil.which("ffmpeg") or "/opt/homebrew/bin/ffmpeg"
os.makedirs(ROOT, exist_ok=True)
lock = threading.Lock()
cur = {"ids": None, "proc": None, "n": 0, "t": 0}


URLS = {}            # (id, altura) -> (url, hora): la URL de YouTube dura horas; así un cambio de cámara no repite yt-dlp
URL_TTL = 3 * 3600


def stream_url(v, h):
    # Video en vivo "crudo" de YouTube (HLS) sin el reproductor: no trae anuncios. El 1er formato es el que casi siempre existe (los otros son respaldo)
    k = (v, h); c = URLS.get(k)
    if c and time.time() - c[1] < URL_TTL:
        return c[0]
    for fmt in (f"bv*[height<={h}][vcodec^=avc1]", f"bv*[height<={h}]", f"best[height<={h}][vcodec^=avc1]", "best"):
        r = subprocess.run([YTDLP, "-g", "-f", fmt, "--no-playlist", f"https://www.youtube.com/watch?v={v}"], capture_output=True, text=True, timeout=60)
        u = r.stdout.strip().splitlines()
        if r.returncode == 0 and u:
            URLS[k] = (u[0], time.time())
            return u[0]
    raise RuntimeError("sin stream para " + v)


def all_cams():
    """Primera cámara de cada ciudad, leída de docs/cities.js (si está junto a este programa)."""
    try:
        t = open(os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "docs", "cities.js"), encoding="utf-8").read()
        return [m for m in re.findall(r'cams:\["([A-Za-z0-9_-]{11})"', t)]
    except Exception:
        return []


def warm_loop():
    """Deja listas las direcciones de todas las cámaras (720p); se repite antes de que caduquen."""
    while True:
        ids = all_cams()
        with ThreadPoolExecutor(3) as ex:
            list(ex.map(lambda v: _try(stream_url, v, 720), ids))
        print(f"precalentadas {len(ids)} cámaras", flush=True)
        time.sleep(URL_TTL - 600)


def _try(f, *a):
    try: return f(*a)
    except Exception: return None


def stop():
    p = cur["proc"]
    if p and p.poll() is None:
        p.terminate()                                   # no espero: la nueva cámara arranca ya
        threading.Thread(target=lambda: (_try(p.wait, 5) or p.poll() is not None or p.kill()), daemon=True).start()
    cur.update(ids=None, proc=None)


def start(ids):
    stop()
    cur["n"] += 1; n = cur["n"]
    d = os.path.join(ROOT, str(n)); shutil.rmtree(d, ignore_errors=True); os.makedirs(d)
    k = len(ids)
    with ThreadPoolExecutor(4) as ex: urls = list(ex.map(lambda v: stream_url(v, 720 if k == 1 else 480), ids))
    inp = []
    for u in urls:
        inp += ["-analyzeduration", "1000000", "-probesize", "1000000", "-reconnect", "1", "-reconnect_streamed", "1", "-reconnect_delay_max", "5", "-i", u]
    hls = ["-f", "hls", "-hls_time", "2", "-hls_list_size", "6", "-hls_flags", "delete_segments+omit_endlist+independent_segments",
           "-hls_segment_filename", os.path.join(d, "s%05d.ts"), os.path.join(d, "index.m3u8")]
    if k == 1:
        cmd = [FFMPEG, "-loglevel", "warning", *inp, "-c:v", "copy", "-an", *hls]
    else:
        cells = ""
        for i in range(k):
            cells += f"[{i}:v]scale=960:540:force_original_aspect_ratio=decrease,pad=960:540:(ow-iw)/2:(oh-ih)/2,setsar=1,fps=24[v{i}];"
        for i in range(k, 4):   # relleno negro si hay menos de 4
            cells += f"color=c=black:s=960x540:r=24[v{i}];"
        fc = cells + "[v0][v1][v2][v3]xstack=inputs=4:layout=0_0|w0_0|0_h0|w0_h0[o]"
        cmd = [FFMPEG, "-loglevel", "warning", *inp, "-filter_complex", fc, "-map", "[o]", "-c:v", "h264_videotoolbox", "-b:v", "5M", "-g", "48", "-an", *hls]
    cur["proc"] = subprocess.Popen(cmd, stderr=open(os.path.join(d, "ffmpeg.log"), "w"))
    cur["ids"] = ids; cur["t"] = time.time()
    return f"/s/{n}/index.m3u8"


# La TV encuentra al Mac sola: cada 30 s publico mi IP de la red de la casa en un directorio (solo IPs privadas; la base lo exige).
SB = "https://eythwqbeatakgsvjhrbl.supabase.co/rest/v1/mytv_relay?on_conflict=id"
SB_KEY = "sb_publishable_CPOL_oOUFhvdJ1sSDqK_Kg_46iP00Cj"


def lan_ip():
    s = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
    try:
        s.connect(("10.255.255.255", 1)); return s.getsockname()[0]
    finally:
        s.close()


def announce_loop():
    last = None
    while True:
        try:
            url = f"http://{lan_ip()}:{PORT}"
            req = urllib.request.Request(SB, data=json.dumps({"id": "main", "url": url, "updated_at": time.strftime("%Y-%m-%dT%H:%M:%SZ", time.gmtime())}).encode(), method="POST",
                headers={"apikey": SB_KEY, "Authorization": "Bearer " + SB_KEY, "Content-Type": "application/json", "Prefer": "resolution=merge-duplicates,return=minimal"})
            urllib.request.urlopen(req, timeout=10).read()
            if url != last: print("anunciado:", url, flush=True); last = url
        except Exception as e:
            print("no pude anunciar la IP:", e, flush=True)
        time.sleep(30)


class H(SimpleHTTPRequestHandler):
    def __init__(s, *a, **k): super().__init__(*a, directory=ROOT, **k)
    def end_headers(s):
        s.send_header("Access-Control-Allow-Origin", "*"); s.send_header("Cache-Control", "no-store"); super().end_headers()
    def log_message(s, *a): pass
    def j(s, o, c=200):
        b = json.dumps(o).encode(); s.send_response(c); s.send_header("Content-Type", "application/json"); s.send_header("Content-Length", str(len(b))); s.end_headers(); s.wfile.write(b)
    def do_GET(s):
        u = urlparse(s.path)
        if u.path == "/health": return s.j({"ok": True, "ids": cur["ids"]})
        if u.path == "/stop":
            with lock: stop()
            return s.j({"ok": True})
        if u.path == "/play":
            ids = [x for x in parse_qs(u.query).get("ids", [""])[0].split(",") if x][:4]
            if not ids: return s.j({"error": "ids"}, 400)
            try:
                with lock:
                    alive = cur["proc"] and cur["proc"].poll() is None
                    path = f"/s/{cur['n']}/index.m3u8" if (cur["ids"] == ids and alive) else start(ids)
                f = os.path.join(ROOT, path[3:])
                for _ in range(100):      # espera a que exista la lista (máx 25 s)
                    if os.path.exists(f) and "#EXTINF" in open(f).read(): break
                    time.sleep(.25)
                else:
                    return s.j({"error": "no arrancó"}, 504)
                return s.j({"url": path})
            except Exception as e:
                return s.j({"error": str(e)}, 500)
        if u.path.startswith("/s/"):
            s.path = u.path[2:]; return super().do_GET()
        s.send_error(404)


if __name__ == "__main__":
    signal.signal(signal.SIGTERM, lambda *a: (stop(), sys.exit(0)))
    try:
        srv = ThreadingHTTPServer(("0.0.0.0", PORT), H)      # primero el puerto: si está ocupado, salgo y launchd lo reintenta
    except OSError as e:
        print("puerto ocupado:", e, flush=True); os._exit(1)
    print("relay en puerto", PORT, flush=True)
    threading.Thread(target=announce_loop, daemon=True).start()
    threading.Thread(target=warm_loop, daemon=True).start()
    srv.serve_forever()
