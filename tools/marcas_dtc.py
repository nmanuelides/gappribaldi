"""
Genera app/src/main/assets/dtc/marcas.json.gz: descripciones de los códigos propios de cada marca
(los rangos que la norma SAE J2012 deja a cada fabricante: P1xxx, P30-P33, B1-B2, C1-C2, U1-U2).

Fuentes (dos bases abiertas, licencia MIT, que juntan lo que publican fabricantes, talleres y foros):
  - kierandrewett/obd  (carpeta dtc_codes/, una lista por marca juntada de dot.report)
      https://github.com/kierandrewett/obd
  - Wal33D/dtc-database (data/source-data/<marca>_codes.txt, formato "CODIGO - Descripción")
      https://github.com/Wal33D/dtc-database

Cada marca de la app usa primero sus propias listas y, si el código no está, las de sus marcas hermanas
(mismo grupo, que comparten computadoras y tablas de códigos: Mini <- BMW, VW <- Audi, Toyota <- Lexus,
Ford <- Lincoln/Mercury, Chevrolet <- GM, Honda <- Acura). La app aclara cuando el texto sale de una hermana.

Uso:  git clone --depth 1 https://github.com/kierandrewett/obd
      git clone --depth 1 https://github.com/Wal33D/dtc-database
      python tools/marcas_dtc.py <carpeta-obd> <carpeta-dtc-database>
"""
import gzip
import json
import os
import re
import sys

SALIDA = os.path.join(os.path.dirname(__file__), "..", "app", "src", "main", "assets", "dtc", "marcas.json.gz")

# marca de la app -> [(nombre de la marca en la fuente, archivos de kierandrewett, archivos de Wal33D)], la propia primero
MARCAS = {
    "Ford": [("Ford", ["ford"], ["ford"]), ("Lincoln", ["lincoln"], ["lincoln"]), ("Mercury", ["mercury"], ["mercury"])],
    "Volkswagen": [("Volkswagen", ["volkswagen"], ["volkswagen"]), ("Audi", ["audi"], ["audi"])],
    "Toyota": [("Toyota", ["toyota"], ["toyota"]), ("Lexus", ["lexus"], ["lexus"])],
    "BMW": [("BMW", ["bmw"], ["bmw"]), ("Mini", ["mini"], [])],
    "Mini": [("Mini", ["mini"], []), ("BMW", ["bmw"], ["bmw"])],
    "Chevrolet": [
        ("Chevrolet", ["chevrolet"], ["chevy"]),
        ("GM", [], ["gm", "gmc", "buick", "pontiac", "cadillac"]),
        ("Opel", ["opel"], []),
        ("Saturn", ["saturn"], ["saturn"]),
        ("Oldsmobile", ["oldsmobile"], ["oldsmobile"]),
    ],
    "Honda": [("Honda", ["honda"], ["honda"]), ("Acura", ["acura"], ["acura"])],
    "Mercedes-Benz": [("Mercedes-Benz", ["mercedes-benz"], ["mercedes"])],
}


def es_propio(codigo: str) -> bool:
    """¿Rango que define cada fabricante? (lo contrario de esRangoGenerico en Dtc.kt)"""
    if not re.fullmatch(r"[PBCU][0-3][0-9A-F]{3}", codigo):
        return False
    d = codigo[1]
    if codigo[0] == "P":
        return d == "1" or (d == "3" and codigo[2] in "0123")
    if codigo[0] in "BC":
        return d in "12"
    return d in "12"  # U


def limpiar(texto: str) -> str:
    t = texto.replace("\xa0", " ")
    t = re.sub(r"\s+", " ", t).strip(" -;:")
    # Listas de Ford: "93: Control Module Software #2: No Operation Z: ABS ... (Sistema)". El texto es de un
    # tipo de falla puntual; se deja el nombre del código y el sistema, que valen para todos los tipos de falla.
    m = re.match(r"^[0-9A-F]{2}:\s*(.+)$", t)
    if m:
        resto = m.group(1)
        sistema = re.search(r"\(((?:[^()]|\([^()]*\))+)\)\s*$", resto)
        nombre = re.split(r"\s[A-Z]{1,2}:\s", resto)[0].rsplit(": ", 1)[0].strip()
        t = nombre + (f" ({sistema.group(1)})" if sistema else "")
    # Listas de VW: terminan con el código en hex y el número de 5 cifras que usan VAG-COM / VCDS
    t = re.sub(r"\s[0-9A-F]{4}\s(\d{5})(?=$|\s/)", r" [VAG \1]", t)
    # Textos de otra marca pegados ("/GM: ...")
    t = re.sub(r"\s*/\s*(GM|Ford|Chrysler|Nissan|Honda|Toyota|Mazda)\s*:.*$", "", t)
    # Palabras pegadas en la fuente ("BrakeSystem", "SteeringWheel")
    t = re.sub(r"([a-z])(System|Wheel|Control)\b", r"\1 \2", t)
    return t.strip(" -;:/")


def normal(texto: str) -> str:
    """Para comparar textos sin fijarse en mayúsculas, puntos ni espacios."""
    return re.sub(r"[^a-z0-9]", "", texto.lower())


def kieran(carpeta: str, archivo: str) -> dict:
    ruta = os.path.join(carpeta, "dtc_codes", archivo + ".json")
    if not os.path.exists(ruta):
        return {}
    datos = json.load(open(ruta, encoding="utf-8"))
    return {c.upper(): limpiar(d) for c, d in datos.items() if isinstance(d, str)}


def wal(carpeta: str, archivo: str) -> dict:
    ruta = os.path.join(carpeta, "data", "source-data", archivo + "_codes.txt")
    if not os.path.exists(ruta):
        return {}
    salida = {}
    for linea in open(ruta, encoding="utf-8", errors="replace"):
        m = re.match(r"\s*([PBCU][0-9A-F]{4})\s*[-–:]\s*(.+)", linea.strip(), re.I)
        if m:
            salida.setdefault(m.group(1).upper(), limpiar(m.group(2)))
    return salida


def main():
    obd, wal_db = sys.argv[1], sys.argv[2]
    salida = {"fuentes": ["https://github.com/kierandrewett/obd", "https://github.com/Wal33D/dtc-database"], "marcas": {}}
    total = 0
    for marca, fuentes in MARCAS.items():
        codigos = {}
        for nombre, ks, ws in fuentes:
            propia = nombre == fuentes[0][0]
            for lista in [kieran(obd, k) for k in ks] + [wal(wal_db, w) for w in ws]:
                for c, d in lista.items():
                    if not es_propio(c) or not d or len(d) < 4:
                        continue
                    previo = codigos.get(c)
                    if previo is None:
                        codigos[c] = {"d": d} if propia else {"d": d, "o": nombre}
                    elif propia and "o" not in previo and normal(d) not in normal(previo["d"]):
                        # Dos fuentes de la misma marca con textos distintos: se muestran los dos
                        if len(previo["d"]) + len(d) < 260:
                            previo["d"] += " / " + d
        salida["marcas"][marca] = codigos
        propios = sum(1 for v in codigos.values() if "o" not in v)
        total += len(codigos)
        print(f"{marca:14s} {len(codigos):5d}  ({propios} propios, {len(codigos) - propios} de marcas hermanas)")
    datos = json.dumps(salida, ensure_ascii=False, separators=(",", ":")).encode("utf-8")
    with gzip.open(os.path.normpath(SALIDA), "wb", compresslevel=9) as f:
        f.write(datos)
    print(f"total {total}, {len(datos) // 1024} KB sin comprimir -> {os.path.normpath(SALIDA)}")


if __name__ == "__main__":
    main()
