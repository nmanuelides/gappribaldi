#!/usr/bin/env python3
"""Genera los assets de códigos genéricos a partir de OBDex (CC0).

Uso:
    git clone --depth 1 https://github.com/foerbsnavi/OBDex.git
    python tools/build_dtc_db.py <ruta a OBDex> app/src/main/assets/dtc

Salida: un archivo gzip por familia (primeros 3 caracteres del código, p. ej.
P01.json.gz) con un objeto JSON {codigo: entrada}. Campos de cada entrada:
  t  título (inglés)            d  descripción (inglés)
  c  causas [[probabilidad, texto]]  (probabilidad: h/m/l)
  s  síntomas                   r  códigos relacionados
  f  banderas: 1=MIL, 2=emisiones, 4=puede entrar en modo emergencia
  x  dificultad de reparación (easy/medium/hard)
"""
import glob, gzip, json, os, sys, collections
import yaml

try:
    Loader = yaml.CSafeLoader
except AttributeError:
    Loader = yaml.SafeLoader

src, out = sys.argv[1], sys.argv[2]
os.makedirs(out, exist_ok=True)
buckets = collections.defaultdict(dict)
n = 0
for path in sorted(glob.glob(os.path.join(src, "data", "generic", "*.yaml"))):
    with open(path, encoding="utf-8") as fh:
        for e in yaml.load(fh, Loader=Loader):
            code = e["code"].upper()
            flags = e.get("flags") or {}
            f = (1 if flags.get("mil") else 0) | (2 if flags.get("emissions_relevant") else 0) \
                | (4 if flags.get("limp_mode_possible") else 0)
            entry = {"t": e["title"]["en"]}
            d = (e.get("description") or {}).get("en")
            if d: entry["d"] = d
            c = [[(x.get("likelihood") or "m")[0], x["label"]["en"]] for x in e.get("common_causes") or []]
            if c: entry["c"] = c
            s = [x["en"] for x in e.get("symptoms") or []]
            if s: entry["s"] = s
            if e.get("related_codes"): entry["r"] = e["related_codes"]
            if f: entry["f"] = f
            x = (e.get("repair") or {}).get("difficulty")
            if x: entry["x"] = x
            buckets[code[:3]][code] = entry
            n += 1
total = 0
for key, data in buckets.items():
    raw = json.dumps(data, ensure_ascii=False, separators=(",", ":")).encode("utf-8")
    with open(os.path.join(out, key + ".json.gz"), "wb") as fh:
        with gzip.GzipFile(fileobj=fh, mode="wb", mtime=0) as gz:
            gz.write(raw)
    total += os.path.getsize(os.path.join(out, key + ".json.gz"))
print(f"{n} códigos en {len(buckets)} archivos, {total/1e6:.1f} MB")
