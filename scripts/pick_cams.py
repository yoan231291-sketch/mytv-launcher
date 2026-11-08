#!/usr/bin/env python3
"""
Paso 3: con verified.json (resultado de verify_cams.js) elige las cámaras aprobadas por ciudad
(0 anuncios, arranca 3/3, estable) y hace hojas de contacto con la captura de cada una para revisarlas a ojo
(marcas de agua, textos encima, pantalla negra).
Uso: python3 pick_cams.py verified.json shots/ salida_dir/   → sheets/sheet_N.jpg  y  picked.json
"""
import json, sys
from pathlib import Path
from PIL import Image, ImageDraw

ver = json.load(open(sys.argv[1])); shots = Path(sys.argv[2]); out = Path(sys.argv[3]); out.mkdir(parents=True, exist_ok=True)
picked = {}
for v, x in ver.items():
    if x["stable"] and x["ads"] == 0 and x["starts"] == 3 and (shots / f"{v}.jpg").exists():
        picked.setdefault(x["city"], []).append(v)
json.dump(picked, open(out / "picked.json", "w"), indent=1)
items = [(c, v) for c, vs in sorted(picked.items()) for v in vs[:3]]
W, H, COLS, ROWS = 480, 270, 4, 4
for n in range(0, len(items), COLS * ROWS):
    sheet = Image.new("RGB", (W * COLS, H * ROWS), "black"); d = ImageDraw.Draw(sheet)
    for i, (c, v) in enumerate(items[n:n + COLS * ROWS]):
        im = Image.open(shots / f"{v}.jpg").resize((W, H)); x, y = (i % COLS) * W, (i // COLS) * H
        sheet.paste(im, (x, y)); d.rectangle([x, y, x + 250, y + 16], fill="black"); d.text((x + 3, y + 2), f"{n + i}: {c} {v}", fill="yellow")
    sheet.save(out / f"sheet_{n // (COLS * ROWS)}.jpg", quality=80)
print(len(picked), "ciudades,", len(items), "cámaras →", out)
