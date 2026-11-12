"""
Ayuda para revisar un tablero contra su imagen de referencia (ver docs/revision-tableros.md).
Trabaja en las mismas coordenadas que el código: la referencia escalada a 2000 px de ancho.

  python tools/revision_tablero.py recorte <ref.jpg> x0 y0 x1 y1 <salida.png> [ancho_salida]
  python tools/revision_tablero.py perfil  <ref.jpg> cx cy angulo r0 r1 [paso]
  python tools/revision_tablero.py linea   <ref.jpg> x0 y0 x1 y1 [puntos]

`perfil` lista el color a lo largo de un radio (ángulo como en el lienzo: 0 = derecha, 90 = abajo);
`linea` lo hace a lo largo de un segmento cualquiera.
"""
import math
import sys

from PIL import Image


def abrir(ruta):
    img = Image.open(ruta).convert("RGB")
    return img, img.size[0] / 2000.0


def recorte(ruta, x0, y0, x1, y1, salida, ancho=900):
    img, k = abrir(ruta)
    r = img.crop((int(x0 * k), int(y0 * k), int(x1 * k), int(y1 * k)))
    alto = int(ancho * r.size[1] / r.size[0])
    r.resize((int(ancho), alto)).save(salida)
    print(f"{salida}: {ancho}x{alto} (1 px = {(x1 - x0) / ancho:.2f} unidades)")


def color(img, k, x, y):
    return "%02x%02x%02x" % img.getpixel((min(int(x * k), img.size[0] - 1), min(int(y * k), img.size[1] - 1)))


def perfil(ruta, cx, cy, ang, r0, r1, paso=4):
    img, k = abrir(ruta)
    a = math.radians(ang)
    print(" ".join(f"{r}:{color(img, k, cx + r * math.cos(a), cy + r * math.sin(a))}" for r in range(int(r0), int(r1), int(paso))))


def linea(ruta, x0, y0, x1, y1, puntos=30):
    img, k = abrir(ruta)
    n = int(puntos)
    print(" ".join(f"{x0 + (x1 - x0) * i / n:.0f},{y0 + (y1 - y0) * i / n:.0f}:{color(img, k, x0 + (x1 - x0) * i / n, y0 + (y1 - y0) * i / n)}" for i in range(n + 1)))


if __name__ == "__main__":
    cmd, ruta, *resto = sys.argv[1:]
    if cmd == "recorte":
        x0, y0, x1, y1 = map(float, resto[:4])
        recorte(ruta, x0, y0, x1, y1, resto[4], float(resto[5]) if len(resto) > 5 else 900)
    elif cmd == "perfil":
        perfil(ruta, *map(float, resto))
    elif cmd == "linea":
        linea(ruta, *map(float, resto))
