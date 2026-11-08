#!/usr/bin/env python3
"""
Arma docs/cities.js con las ciudades aprobadas.
Entrada: sel.json  { "ciudad": { "A": [ids limpias], "B": [ids con detalles menores] } }  (revisadas a ojo y reproducidas)
Las 16 ciudades que ya estaban se conservan tal cual; las nuevas salen de catalog.py / catalog2.py.
Uso: python3 gen_cities.py sel.json
"""
import json, re, sys
from pathlib import Path
sys.path.insert(0, str(Path(__file__).parent))
from catalog import CATALOG
from catalog2 import CATALOG2

ROOT = Path(__file__).resolve().parent.parent
JS = ROOT / "docs" / "cities.js"
sel = json.load(open(sys.argv[1]))
meta = {r[0]: r for r in CATALOG + CATALOG2}
meta["leavenworth"] = ("leavenworth", "Leavenworth", "Calle principal", "🇺🇸", "America/Los_Angeles", 47.596, -120.661, "leavenworth washington live webcam", "leavenworth")

src = JS.read_text()
head = src[: src.index("window.CITIES = [")]
tail = src[src.index("window.APPS"):]
old = re.findall(r"^  \{ id:'(\w+)'.*$", src[src.index("window.CITIES"): src.index("window.APPS")], re.M)
old_lines = [l for l in src.splitlines() if re.match(r"^  \{ id:'\w+'", l)]
old_ids = [re.match(r"^  \{ id:'(\w+)'", l).group(1) for l in old_lines]

new = []
for cid, d in sel.items():
    if cid in old_ids or cid == "london" and False: continue
    cams = (d.get("A", []) + d.get("B", []))[:4]
    if not cams: continue
    _, name, spot, flag, tz, lat, lon, q, kw = meta[cid]
    kws = [k.strip() for k in kw.split("|")]
    new.append((name, f"  {{ id:'{cid}', name:{json.dumps(name, ensure_ascii=False)}, spot:{json.dumps(spot, ensure_ascii=False)}, flag:'{flag}', tz:'{tz}', lat:{lat}, lon:{lon}, kw:{json.dumps(kws, ensure_ascii=False)}, q:{json.dumps([q], ensure_ascii=False)}, cams:{json.dumps(cams)} }},"))
new.sort(key=lambda t: t[0].lower())
out = head + "window.CITIES = [\n" + "\n".join(old_lines) + "\n" + "\n".join(l for _, l in new) + "\n];\n\n" + tail
JS.write_text(out)
print(len(old_lines), "anteriores +", len(new), "nuevas =", len(old_lines) + len(new))
