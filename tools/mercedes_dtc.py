"""
Genera app/src/main/assets/dtc/mercedes.json.gz: descripciones de los códigos propios de cada módulo Mercedes.

Fuente: listados "Full list of fault codes and events" exportados del DAS (Diagnosis Assistance System de
Daimler) y publicados en http://benzbits.com/dtc/ por la comunidad. Son de autos de la misma época que el W204
(W203, CLK W209, SL R230) con los mismos módulos o sus antecesores: sirven de guía, no son la lista oficial
del W204. La app lo aclara al mostrarlos.

Cada código se guarda con la clave que lee la app: los dos bytes crudos en hex ("9001", "5001").
  - Códigos de 4 caracteres hex ("9001", "912B", "D001", "0604") ya son esos dos bytes.
  - Códigos estilo SAE ("B1003", "C1001") se convierten a sus dos bytes (B1003 = 9003, C1001 = 5001).
  - En la lista del motor ME9.7 el número de 4 cifras es interno del DAS; se usa el código SAE entre paréntesis.

Uso:  python tools/mercedes_dtc.py [carpeta-de-trabajo]   (necesita `pip install pypdf`)
"""
import gzip
import json
import os
import re
import sys
import time
import urllib.parse
import urllib.request

BASE = "http://benzbits.com/dtc/"
SALIDA = os.path.join(os.path.dirname(__file__), "..", "app", "src", "main", "assets", "dtc", "mercedes.json.gz")

# familia -> (archivos, nombre para mostrar, de qué auto sale la lista, ¿la clave sale del código SAE entre paréntesis?)
FAMILIAS = {
    "motor": (["ME9.7.pdf"], "Motor ME 9.7 (M272/M273)", "CLK W209", True),
    "caja": (["Transmission7229.pdf"], "Caja 7G-Tronic (722.9)", "CLK W209", False),
    "esp": (["ESPCodes (C).pdf"], "ESP / ABS / BAS", "C-Class W203", False),
    "airbag": (["SRScodesW203.pdf"], "Airbag (SRS)", "C-Class W203", False),
    "ezs": (["EZScodes (B).pdf"], "Llave y arranque (EZS)", "C-Class W203", False),
    "sam_del": (["FrontSAMcodes (B).pdf"], "SAM delantero", "C-Class W203", False),
    "sam_tras": (["RearSAMcodes (B).pdf"], "SAM trasero", "C-Class W203", False),
    "tablero": (["ICMcodes.pdf"], "Tablero de instrumentos", "C-Class W203", False),
    "clima": (["ACcodes (B).pdf"], "Climatizador", "C-Class W203", False),
    "columna": (["SCMcodes.pdf"], "Módulo de la columna de dirección", "C-Class W203", False),
    "techo": (["UCP DTCs.pdf", "OCP DTCs.pdf"], "Panel de techo / panel superior", "W203 / R230", False),
    "comand": (["COMANDcodes (D).pdf", "SOUNDcodes (D).pdf", "CDCcodes (D).pdf", "NAVcodes (D).pdf",
                "SATcodes (D).pdf", "MHIcodes (D).pdf", "VCScodes (D).pdf"], "Radio / COMAND y audio", "C-Class W203", False),
    "teleaid": (["TeleAidCodes (D).pdf"], "Tele Aid", "C-Class W203", False),
    "distronic": (["DTC_Distronic.pdf"], "Distronic", "C-Class W203 / CLK", False),
}

# Códigos del W204 que no están en las listas del W203 y que varios foros y talleres describen igual.
FOROS_W204 = {
    "esp": {
        "5944": "Internal fault in control unit N30/4 (ESP control unit, W204). Reported by W204 owners and ESP repair shops.",
        "5945": "Internal fault in control unit N30/4 (ESP control unit, W204). Reported by W204 owners and ESP repair shops.",
    },
}

LETRAS = "PCBU"
CABECERAS = re.compile(
    r"^(Diagnosis Assistance System|\d{2} \w+ \d{4} \d{2}:\d{2}|Ns:|Page \d|VIN |series/model|designation|"
    r"Order number|Full list of fault codes|\d{3}\.\d{3}\s*$|Model\s*$)"
)
ENTRADA = re.compile(
    r"^\s*(Event\s+)?([PBCU][0-9A-F]{4}|[0-9A-F]{4})(?:\s*-\s*([PBCU][0-9A-F]{4}|[0-9A-F]{4}))?\s{2,}(.*)$"
)
SAE_PAREN = re.compile(r"\(\s*([PBCU][0-9A-F]{4})\s*\)")


def a_hex(codigo: str) -> str:
    """'B1003' -> '9003'; '9001' -> '9001'."""
    if len(codigo) == 4:
        return codigo.upper()
    alto = (LETRAS.index(codigo[0]) << 6) | (int(codigo[1], 16) << 4) | int(codigo[2], 16)
    return "%02X%s" % (alto, codigo[3:].upper())


def limpiar(texto: str) -> str:
    t = re.sub(r"\s+", " ", texto)
    t = re.sub(r"\s+([.,:;)\]])", r"\1", t)
    t = re.sub(r"([(\[])\s+", r"\1", t)
    return t.strip(" ;")


def bajar(carpeta: str, archivo: str) -> str:
    destino = os.path.join(carpeta, archivo)
    if not os.path.exists(destino):
        url = BASE + urllib.parse.quote(archivo)
        pedido = urllib.request.Request(url, headers={"User-Agent": "Mozilla/5.0 (gappribaldi tools)"})
        with urllib.request.urlopen(pedido, timeout=60) as r:
            open(destino, "wb").write(r.read())
        time.sleep(1.5)
    return destino


def texto_pdf(ruta: str) -> str:
    import pypdf  # noqa: PLC0415

    return "\n".join((p.extract_text() or "") for p in pypdf.PdfReader(ruta).pages)


def parsear(texto: str, clave_sae: bool) -> dict:
    codigos = {}
    actual = None

    def cerrar():
        if not actual:
            return
        evento, desde, hasta, desc = actual
        desc = limpiar(desc)
        if clave_sae:
            m = SAE_PAREN.search(desc)
            if not m:
                return  # número interno del DAS sin código SAE: la app no lo puede leer así
            claves = [a_hex(m.group(1))]
            desc = limpiar(SAE_PAREN.sub("", desc))
        else:
            a, b = int(a_hex(desde), 16), int(a_hex(hasta or desde), 16)
            claves = ["%04X" % x for x in range(a, b + 1)] if 0 <= b - a <= 32 else [a_hex(desde)]
        if not desc:
            return
        for c in claves:
            entrada = {"d": desc}
            if evento:
                entrada["e"] = 1
            previo = codigos.get(c)
            if previo is None:
                codigos[c] = entrada
            elif desc not in previo["d"]:
                previo["d"] += " / " + desc

    for linea in texto.splitlines():
        if not linea.strip() or CABECERAS.match(linea.strip()):
            continue
        m = ENTRADA.match(linea)
        if m:
            cerrar()
            actual = [bool(m.group(1)), m.group(2), m.group(3), m.group(4)]
        elif actual:
            actual[3] += " " + linea.strip()
    cerrar()
    return codigos


def main():
    carpeta = sys.argv[1] if len(sys.argv) > 1 else os.path.join(os.path.dirname(__file__), "_mercedes_pdf")
    os.makedirs(carpeta, exist_ok=True)
    salida = {"fuente": BASE, "familias": {}}
    total = 0
    for clave, (archivos, nombre, origen, clave_sae) in FAMILIAS.items():
        codigos = {}
        for archivo in archivos:
            for c, e in parsear(texto_pdf(bajar(carpeta, archivo)), clave_sae).items():
                codigos.setdefault(c, e)
        for c, d in FOROS_W204.get(clave, {}).items():
            codigos.setdefault(c, {"d": d, "w": 1})
        salida["familias"][clave] = {"nombre": nombre, "origen": origen, "codigos": codigos}
        total += len(codigos)
        print(f"{clave:10s} {len(codigos):5d}  {nombre}")
    datos = json.dumps(salida, ensure_ascii=False, separators=(",", ":")).encode("utf-8")
    with gzip.open(os.path.normpath(SALIDA), "wb", compresslevel=9) as f:
        f.write(datos)
    print(f"total {total} códigos, {len(datos) // 1024} KB sin comprimir -> {os.path.normpath(SALIDA)}")


if __name__ == "__main__":
    main()
