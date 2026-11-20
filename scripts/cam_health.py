#!/usr/bin/env python3
"""
Revisión diaria de cámaras (corre en el Mac con launchd). Usa yt-dlp (el mismo camino que el relé), así que "viva" aquí = "se ve en la TV".
  1. Prueba cada cámara de docs/cities.js. Quita las caídas.
  2. Si una ciudad se queda con menos de 2, busca cámaras nuevas en YouTube (el título/canal debe nombrar la ciudad), prueba que estén vivas y no negras.
  3. Si una ciudad se queda sin ninguna, la quita (queda anotado en el registro).
  4. Guarda cities.js, hace commit y push (GitHub Pages lo publica; la TV lo lee sola).
Protecciones: si YouTube responde 429 o salen más de 30 caídas de golpe (probable falla de red) no cambia nada.
Uso: python3 cam_health.py [--dry]
"""
import re, subprocess, sys, time, unicodedata
from pathlib import Path
ROOT = Path(__file__).resolve().parent.parent
CITIES = ROOT / "docs" / "cities.js"
YTDLP, FFMPEG = "/opt/homebrew/bin/yt-dlp", "/opt/homebrew/bin/ffmpeg"
sys.path.insert(0, str(Path(__file__).parent))
from find_cams import search, BAD
EXTRA_BAD = re.compile(r"\btalk|adoraci|ko[sś]ci[oó]ł|church|\bmass\b|msza|worship|sermon|podcast|first person|journey|compilation|best of|walking|tour\b", re.I)
DRY = "--dry" in sys.argv
MIN_CAMS, MAX_CAMS, MAX_DEAD = 2, 4, 30


def log(*a): print(time.strftime("%H:%M:%S"), *a, flush=True)
def norm(s): return unicodedata.normalize("NFD", s).encode("ascii", "ignore").decode().lower()


class Blocked(Exception): pass


def alive(v):
    r = subprocess.run([YTDLP, "--no-update", "-g", "-f", "bv*[height<=720]", "--no-playlist", "https://www.youtube.com/watch?v=" + v], capture_output=True, text=True, timeout=90)
    time.sleep(3)
    if "429" in r.stderr or "not a bot" in r.stderr: raise Blocked(v)
    u = r.stdout.strip().splitlines()
    return u[0] if r.returncode == 0 and u else None


def not_black(url):
    try:
        fr = subprocess.run([FFMPEG, "-v", "error", "-y", "-t", "15", "-i", url, "-vf", "scale=64:36,format=gray", "-frames:v", "1", "-f", "rawvideo", "-"], capture_output=True, timeout=45).stdout
        return bool(fr) and sum(fr) / len(fr) >= 12
    except Exception:
        return False


def parse():
    rows = []
    for i, l in enumerate(CITIES.read_text(encoding="utf-8").split("\n")):
        m, c = re.search(r"id:'([a-z0-9_]+)'", l), re.search(r"cams:\[([^\]]*)\]", l)
        if not (m and c): continue
        kw = re.search(r"kw:\[([^\]]*)\]", l); q = re.search(r"q:\[([^\]]*)\]", l)
        rows.append(dict(i=i, id=m.group(1), cams=re.findall(r'"([\w-]{11})"', c.group(1)),
                         kw=re.findall(r'"([^"]+)"', kw.group(1)) if kw else [], q=re.findall(r'"([^"]+)"', q.group(1)) if q else []))
    return rows


def find_new(row, have):
    kws = [norm(k) for k in row["kw"]] or [row["id"]]
    queries = row["q"] + [f"{kws[0]} live cam 24/7", f"{kws[0]} webcam live stream"]
    out, seen = [], set(have)
    for q in queries:
        for v, t, ch in search(q):
            if v in seen or BAD.search(t) or EXTRA_BAD.search(t): continue
            if not any(k in norm(t + " " + ch) for k in kws): continue
            seen.add(v); out.append(v)
        if len(out) >= 8: break
    return out[:8]


def main():
    rows = parse(); log("ciudades:", len(rows))
    dead, status = [], {}
    try:
        for r in rows:
            for v in r["cams"]:
                if v not in status: status[v] = alive(v)
                if not status[v]: dead.append(v)
    except Blocked as e:
        log("YouTube limitó las consultas (429): no cambio nada"); return 1
    log("cámaras caídas:", len(dead))
    if len(dead) > MAX_DEAD: log("demasiadas caídas de golpe (¿falla de red?): no cambio nada"); return 1
    changes, removed = [], []
    lines = CITIES.read_text(encoding="utf-8").split("\n")
    try:
        for r in rows:
            keep = [v for v in r["cams"] if status[v]]
            if len(keep) < MIN_CAMS:
                for v in find_new(r, r["cams"]):
                    if len(keep) >= MAX_CAMS or (len(keep) >= MIN_CAMS): break
                    u = alive(v)
                    if u and not_black(u): keep.append(v); log(r["id"], "cámara nueva", v)
            if keep == r["cams"]: continue
            if not keep: removed.append(r["id"]); lines[r["i"]] = None; continue
            l = lines[r["i"]]; c = re.search(r"cams:\[([^\]]*)\]", l)
            lines[r["i"]] = l[:c.start()] + "cams:[" + ", ".join(f'"{x}"' for x in keep) + "]" + l[c.end():]
            changes.append(r["id"])
    except Blocked:
        log("YouTube limitó las consultas (429) buscando reemplazos: no cambio nada"); return 1
    log("ciudades ajustadas:", changes, "| quitadas (sin cámaras):", removed)
    if not changes and not removed: log("todo en orden"); return 0
    if DRY: log("(prueba: no guardo)"); return 0
    CITIES.write_text("\n".join(l for l in lines if l is not None), encoding="utf-8")
    sh = lambda *a: subprocess.run(a, cwd=ROOT, capture_output=True, text=True)
    sh("git", "pull", "--rebase", "-q")
    sh("git", "add", "docs/cities.js")
    sh("git", "commit", "-qm", f"Revisión diaria de cámaras: ajustadas {len(changes)}, quitadas {len(removed)}")
    p = sh("git", "push", "-q"); log("push:", "ok" if p.returncode == 0 else p.stderr.strip()[:200])
    return 0


if __name__ == "__main__":
    sys.exit(main())
