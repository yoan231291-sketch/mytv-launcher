#!/usr/bin/env python3
"""
Revisión barata: baja la miniatura EN VIVO de cada candidata (i.ytimg.com no limita como las páginas de video),
descarta las negras / blancas / con barras negras a los lados, y arma hojas de contacto con id y ciudad para mirarlas a ojo.
Uso: python3 thumbs.py candidates.json salida_dir [ids_a_excluir.json]
"""
import io, json, sys, urllib.request
from concurrent.futures import ThreadPoolExecutor
from pathlib import Path
from PIL import Image, ImageDraw, ImageStat

cand = json.load(open(sys.argv[1])); out = Path(sys.argv[2]); out.mkdir(parents=True, exist_ok=True)
skip = set(json.load(open(sys.argv[3]))) if len(sys.argv) > 3 else set()
jobs = [(c, x["v"], x.get("ch", "")) for c, l in cand.items() for x in l if x["v"] not in skip]


def fetch(j):
    c, v, ch = j
    for u in (f"https://i.ytimg.com/vi/{v}/mqdefault_live.jpg", f"https://i.ytimg.com/vi/{v}/mqdefault.jpg"):
        try:
            im = Image.open(io.BytesIO(urllib.request.urlopen(u, timeout=15).read())).convert("RGB")
            if im.size[0] >= 300: return c, v, ch, im
        except Exception:
            pass
    return None


def ok(im):
    g = im.convert("L"); st = ImageStat.Stat(g); m, sd = st.mean[0], st.stddev[0]
    if m < 10 or sd < 6 or (m > 235 and sd < 20): return False            # negra / blanca / lisa
    w, h = g.size; l = ImageStat.Stat(g.crop((0, 0, w // 12, h))).mean[0]; r = ImageStat.Stat(g.crop((w - w // 12, 0, w, h))).mean[0]
    if l < 6 and r < 6 and m > 25: return False                            # barras negras a los lados
    return True


with ThreadPoolExecutor(8) as ex: res = [r for r in ex.map(fetch, jobs) if r]
res = [r for r in res if ok(r[3])]; res.sort(key=lambda r: (r[0], r[1]))
W, H, COLS, ROWS = 320, 180, 6, 6
meta = []
for n in range(0, len(res), COLS * ROWS):
    sheet = Image.new("RGB", (W * COLS, H * ROWS), "black"); d = ImageDraw.Draw(sheet)
    for i, (c, v, ch, im) in enumerate(res[n:n + COLS * ROWS]):
        x, y = (i % COLS) * W, (i // COLS) * H; sheet.paste(im.resize((W, H)), (x, y))
        d.rectangle([x, y, x + 200, y + 12], fill="black"); d.text((x + 2, y + 1), f"{n + i} {c}", fill="yellow"); meta.append((c, v, ch))
    sheet.save(out / f"t{n // (COLS * ROWS)}.jpg", quality=78)
json.dump(meta, open(out / "meta.json", "w"), ensure_ascii=False)
print(len(jobs), "candidatas →", len(res), "pasan el filtro automático;", (len(res) + 35) // 36, "hojas")
