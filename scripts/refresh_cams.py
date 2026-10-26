#!/usr/bin/env python3
"""
Refresca las cámaras en vivo de docs/cities.js.

Para cada ciudad:
  1. Revisa las cámaras actuales y se queda con las que siguen EN VIVO y se pueden incrustar.
  2. Busca en YouTube (filtro "En vivo") cámaras nuevas con las búsquedas de cam_queries.json.
  3. Escribe hasta MAX_CAMS cámaras (primero las que ya funcionaban, para que no cambien sin razón).

Si una ciudad se queda sin ninguna cámara válida, conserva la lista anterior (nunca la deja vacía).
Solo usa la biblioteca estándar de Python. Lo corre GitHub Actions todos los días.
"""
import json, re, sys, time, urllib.parse, urllib.request
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
CITIES_JS = ROOT / "docs" / "cities.js"
QUERIES = ROOT / "scripts" / "cam_queries.json"
MAX_CAMS = 4
UA = {"User-Agent": "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124 Safari/537.36",
      "Accept-Language": "en-US,en;q=0.9", "Cookie": "CONSENT=YES+1; SOCS=CAI"}
# Títulos que no son cámaras fijas de la ciudad (recopilaciones, noticias, música, recorridos grabados…)
BAD = re.compile(r"pre-?recorded|top live|webcam tour|cams? around the world|\b\d{3,4} (top )?live|protest|news|lofi|music|relaxing|radio|replay|highlights", re.I)


def get(url, timeout=20):
    req = urllib.request.Request(url, headers=UA)
    with urllib.request.urlopen(req, timeout=timeout) as r:
        return r.status, r.read().decode("utf-8", "ignore")


def embeddable(vid):
    """oEmbed devuelve 401/404 si el video no se puede incrustar o no existe."""
    try:
        s, _ = get("https://www.youtube.com/oembed?format=json&url=" + urllib.parse.quote(f"https://www.youtube.com/watch?v={vid}"), 15)
        return s == 200
    except Exception:
        return False


def live_now(vid):
    """True si la página del video dice que está transmitiendo en vivo ahora y es reproducible."""
    try:
        _, html = get(f"https://www.youtube.com/watch?v={vid}")
    except Exception:
        return False
    return '"isLiveNow":true' in html and '"playableInEmbed":true' in html and '"status":"OK"' in html


def valid(vid):
    return embeddable(vid) and live_now(vid)


def search_live(query):
    """IDs de videos EN VIVO para una búsqueda (filtro sp=EgJAAQ== de YouTube)."""
    url = "https://www.youtube.com/results?search_query=" + urllib.parse.quote(query) + "&sp=EgJAAQ%253D%253D"
    try:
        _, html = get(url)
    except Exception as e:
        print(f"    búsqueda falló: {e}")
        return []
    out = []
    for vid, title in re.findall(r'"videoRenderer":\{"videoId":"([\w-]{11})".{0,1500}?"title":\{"runs":\[\{"text":"(.*?)"', html):
        if vid not in [v for v, _ in out] and not BAD.search(title):
            out.append((vid, title))
    return out


def main():
    src = CITIES_JS.read_text(encoding="utf-8")
    queries = json.loads(QUERIES.read_text(encoding="utf-8"))
    changed = 0
    for cid, q in queries.items():
        m = re.search(r"(\{ id:'%s'.*?cams:\[)([^\]]*)(\])" % re.escape(cid), src)
        if not m:
            print(f"! {cid}: no está en cities.js"); continue
        old = re.findall(r"'([\w-]{11})'", m.group(2))
        print(f"== {cid}")
        keep = [v for v in old if valid(v)]
        print(f"   siguen en vivo: {keep or '-'}  (de {old})")
        seen = set(keep) | set(old)
        for query in q["search"]:
            if len(keep) >= MAX_CAMS: break
            for vid, title in search_live(query):
                if len(keep) >= MAX_CAMS: break
                if vid in seen: continue
                seen.add(vid)
                if valid(vid):
                    keep.append(vid); print(f"   + {vid}  {title[:70]}")
                time.sleep(0.3)
        if not keep:
            print("   sin cámaras válidas: conservo la lista anterior"); continue
        if keep != old:
            new = ",".join(f"'{v}'" for v in keep)
            src = src[:m.start(2)] + new + src[m.end(2):]
            changed += 1
    if changed:
        CITIES_JS.write_text(src, encoding="utf-8")
    print(f"\nCiudades actualizadas: {changed}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
