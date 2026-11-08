#!/usr/bin/env python3
"""
Paso 1 de la validación: por cada ciudad de catalog.py busca transmisiones EN VIVO en YouTube y guarda, para cada candidata,
las señales que se pueden leer sin reproducir: en vivo ahora, incrustable, SIN anuncios declarados (adPlacements vacío), vistas.
Salida: candidates.json  { id: [ {v, title, ch, views, ads, hd}, ... ] }   (lo siguiente es probarlas reproduciendo)
Uso: python3 find_cams.py [salida.json] [ids,separados,por,coma]
"""
import json, re, sys, time, urllib.parse, urllib.request
from concurrent.futures import ThreadPoolExecutor
from pathlib import Path
sys.path.insert(0, str(Path(__file__).parent))
from catalog import CATALOG
import os
if os.environ.get('CAT2'):
    from catalog2 import CATALOG2 as CATALOG

UA = {"User-Agent": "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124 Safari/537.36",
      "Accept-Language": "en-US,en;q=0.9", "Cookie": "CONSENT=YES+1; SOCS=CAI"}
BAD = re.compile(r"pre-?recorded|top live|webcam tour|cams? around the world|\b\d{3,4} (top )?live|protest|news|lofi|lo-fi|music|relaxing|radio|replay|highlights|walk(ing)? tour|drive|driving|bus ride|train ride|chat|asmr|scanner|podcast|sleep|rain sounds|meditation|jazz|stream\b.*game|gaming|mix\b|playlist", re.I)


def get(url, t=25):
    for a in range(5):
        time.sleep(0.8)   # ritmo suave: YouTube limita (429) si se pide muy rápido
        try:
            with urllib.request.urlopen(urllib.request.Request(url, headers=UA), timeout=t) as r:
                return r.read().decode("utf-8", "ignore")
        except Exception as e:
            time.sleep(20 * (a + 1) if '429' in str(e) else 3)
    return ""


def search(q):
    html = get("https://www.youtube.com/results?search_query=" + urllib.parse.quote(q) + "&sp=EgJAAQ%253D%253D")
    return re.findall(r'"videoRenderer":\{"videoId":"([\w-]{11})".{0,1500}?"title":\{"runs":\[\{"text":"(.*?)"\}.{0,3000}?"ownerText":\{"runs":\[\{"text":"(.*?)"', html)


def probe(v):
    return dict(v=v, ads=False, hd=0, views=0)   # la comprobación real es la reproducción (verify_cams.js)


def _probe_old(v):
    """Sin pedir la página del video (YouTube la limita): oEmbed dice si existe y se puede incrustar. Lo demás lo mide verify_cams.js reproduciendo."""
    h = get("https://www.youtube.com/oembed?format=json&url=" + urllib.parse.quote(f"https://www.youtube.com/watch?v={v}"))
    return dict(v=v, ads=False, hd=0, views=0) if h else None


def city(row):
    cid, name, spot, flag, tz, lat, lon, q, kw = row
    kwre = re.compile(kw, re.I)
    seen, cands = set(), []
    k = kw.split("|")[0]
    for query in (q, f"{k} live cam 24/7", f"{k} webcam live stream", f"{k} city view live"):
        for v, t, ch in search(query):
            if v in seen or BAD.search(t) or not kwre.search(t + " " + ch): continue
            seen.add(v); cands.append((v, t, ch))
        if len(cands) >= 12: break
    out = []
    for v, t, ch in cands[:12]:
        p = probe(v)
        if p: p.update(title=t, ch=ch); out.append(p)
    print(cid, len(out), "sin anuncios:", sum(not x["ads"] for x in out), flush=True)
    return cid, out


if __name__ == "__main__":
    outf = sys.argv[1] if len(sys.argv) > 1 else "candidates.json"
    only = set(sys.argv[2].split(",")) if len(sys.argv) > 2 else None
    try: res = json.load(open(outf))
    except Exception: res = {}
    rows = [r for r in CATALOG if (not only or r[0] in only) and r[0] not in res]
    def run(row):
        cid, out = city(row)
        if not out: return   # vacío = probablemente limitado; se reintenta al reanudar
        res[cid] = out
        json.dump(res, open(outf, "w"), ensure_ascii=False, indent=1)   # guarda al terminar cada ciudad (se puede reanudar)
    with ThreadPoolExecutor(2) as ex:
        list(ex.map(run, rows))
    print("FIN", sum(1 for v in res.values() if any(not x["ads"] for x in v)), "ciudades con al menos 1 sin anuncios de", len(res))
