# Gappribaldi

App Android nativa (Kotlin + Jetpack Compose) para leer, entender y borrar los códigos de falla de un
**Mercedes-Benz C350 W204 (2007, motor M272, caja 7G-Tronic)** con un adaptador **OBDLink MX+**.
Toda la interfaz está en español rioplatense y pensada para alguien que no es mecánico.

**Solo lee y borra códigos.** No codifica ni escribe en los módulos (ver [Seguridad](#seguridad)).

## Qué hace

- **Conexión** por Bluetooth clásico (SPP) al OBDLink MX+, con pantalla de diagnóstico (versión del adaptador, tensión de batería, estado del bus).
- **Escaneo completo**: OBD-II estándar (códigos confirmados, pendientes y permanentes) y todos los módulos Mercedes que respondan, por KWP2000-sobre-CAN o UDS. Prueba ambos protocolos por módulo y recuerda cuál funcionó.
- **Descubrimiento de módulos**: no hay un mapa de direcciones dado por supuesto. La app pregunta dirección por dirección con pedidos inofensivos y guarda en el teléfono quién contestó. Búsqueda rápida (unos segundos) y completa (3 a 5 minutos).
- **Traducción de cada código**: qué significa, causas probables ordenadas, gravedad (verde / amarillo / rojo), qué revisar y notas "frecuente en este auto".
- **Borrado** por módulo o total, con confirmación, advertencia reforzada para airbag y frenos, y relectura automática.
- **Datos en vivo** del motor (ajustes de nafta, temperatura, caudal de aire, sondas…), **monitores de emisiones** y **foto del momento de la falla** (freeze frame).
- **Historial** de escaneos, con marca de "NUEVO" en los códigos que no estaban la vez anterior, e **informe para compartir** con el mecánico.
- **Registro crudo** de todo lo enviado y recibido (TX/RX con hora), compartible con un toque.
- **Modo demo**: un auto simulado para probar toda la app sin estar en el garaje.
- **"Explicame este error"** (opcional): consulta a Claude con tu propia API key.
- **Marcas**: Mercedes-Benz, Ford, Volkswagen, Toyota, BMW, Mini y Chevrolet, con sus colores y logo. El C350 es el auto
  de referencia: tiene mapa de módulos, notas de motor, códigos propios de Mercedes y foto. Con las otras marcas se lee
  lo que es común a todos los autos (OBD-II: motor y emisiones) y la búsqueda completa de módulos; sus códigos propios
  se muestran sin descripción. Sin foto de la marca, la portada muestra solo el modelo en letras gigantes.

## De dónde salen las descripciones

| Fuente | Qué aporta |
|---|---|
| Fichas curadas (`tools/curados.py`) | 395 códigos en español, escritos para este auto, con notas del M272 y la 722.9 |
| [OBDex](https://github.com/foerbsnavi/OBDex) (CC0, dominio público) | Los 9.533 códigos genéricos SAE J2012 con causas, síntomas y banderas (en inglés) |
| Listas del DAS de Mercedes ([benzbits.com/dtc](http://benzbits.com/dtc/), `tools/mercedes_dtc.py`) | 1.494 códigos propios de 14 módulos (motor ME 9.7, caja 722.9, ESP, airbag, EZS, SAM, tablero, clima…), de autos de la misma época que el W204 (W203, CLK) |
| Bases abiertas [kierandrewett/obd](https://github.com/kierandrewett/obd) y [Wal33D/dtc-database](https://github.com/Wal33D/dtc-database) (MIT, `tools/marcas_dtc.py`) | ~6.700 códigos propios de Ford, VW, Toyota, BMW/Mini, Chevrolet y Honda (P1xxx, B1/B2, C1/C2, U1/U2), con las listas de marcas hermanas como respaldo |
| ML Kit de Google, en el teléfono | Traduce al español los textos de OBDex (una descarga de ~30 MB, opcional) |
| Claude (opcional) | Explicación orientativa de un código puntual, incluidos los propios de Mercedes |

**Códigos propios de Mercedes** (los que no pertenecen a los rangos que define la norma): la app los busca en las
listas del DAS del módulo correspondiente y aclara de qué auto sale cada texto. Si el módulo no está identificado,
muestra los significados posibles de cada lista. Si el código no figura, muestra el código crudo y **no inventa una
descripción**; para eso están los botones "Buscar en la web" y "Explicame este error".

## Actualizaciones y novedades

Mismo sistema que MTG Reforged:

- **Autoactualización**: cada push a `main` hace que GitHub Actions compile el APK firmado y publique un release
  `build-<versionCode>`. La app se fija al abrirse; si hay una versión más nueva muestra "Versión nueva disponible",
  la baja con progreso y abre el instalador de Android. En Ajustes → Actualizaciones se puede buscar a mano
  (también avisa si hay una versión compilándose).
- **Novedades**: `news.json` en la raíz del repo. La app lo lee al abrirse y muestra cada mensaje una vez,
  en español o inglés según el idioma elegido. Los changelogs (`id` = `v0.02.0`) aparecen recién cuando esa versión
  está instalada; si se saltearon varias, se juntan en un solo mensaje.
- **Versión**: `apk=` en `version.properties` (1 → v0.01.0). Pasos para publicar en [CLAUDE.md](CLAUDE.md).

## Compilar e instalar

Necesitás [Android Studio](https://developer.android.com/studio) (trae todo lo necesario).

1. Abrí Android Studio → **Open** → elegí esta carpeta. Esperá a que termine de sincronizar (la primera vez baja dependencias).
2. En el celular, activá la depuración USB:
   - **Ajustes → Acerca del teléfono** → tocá 7 veces **Número de compilación** hasta que diga "Ya sos desarrollador".
   - **Ajustes → Sistema → Opciones para desarrolladores** → activá **Depuración por USB**.
3. Conectá el celular por USB y aceptá el cartel "¿Permitir depuración USB?".
4. En Android Studio elegí tu celular arriba y apretá **Run ▶**.

Por línea de comandos:

```bash
./gradlew assembleDebug
```

El APK queda en `app/build/outputs/apk/debug/app-debug.apk`; se puede copiar al teléfono e instalar a mano.

```bash
./gradlew testDebugUnitTest
```

## Primer uso en el auto

1. Emparejá el OBDLink MX+ desde los ajustes de Bluetooth del teléfono (apretá el botón del adaptador para que acepte).
2. Enchufalo, poné el contacto en posición 2 con el motor apagado y abrí la app → **Conectar al adaptador**.
3. **Escanear el auto**. La primera vez hace una búsqueda rápida de módulos.
4. Con la batería bien cargada, hacé una vez la **Búsqueda completa** (Módulos → Búsqueda completa) para encontrar módulos que no estén en las direcciones habituales.
5. Tocá cada módulo "probable" o sin identificar para ponerle el nombre correcto.

Si algo no anda: **Registro → Compartir registro**. Ese archivo tiene todo lo necesario para ajustar la app.

## Qué se sabe y qué no del W204

- El CAN de diagnóstico está en los pines 6/14 a 500 kbps con IDs de 11 bits.
- Motor y caja responden en los pares OBD estándar (`7E0→7E8`, `7E1→7E9`).
- El resto usa el esquema de Daimler: pedido `0x602 + 16·n`, respuesta `0x480 + 2·n` (por ejemplo `612→482`). Este dato viene de proyectos abiertos y **no está confirmado en este auto**: por eso la app no depende de él y escucha cualquier ID de respuesta.
- Qué módulo vive en cada dirección no es público. Los nombres "probable" son una suposición hasta que los confirmes.
- La interpretación de los estados de falla KWP sigue la norma ISO 14230; Mercedes puede apartarse en algún módulo.

Todo esto se valida con los registros de las primeras pruebas en el auto.

## Seguridad

Todo pedido de diagnóstico pasa por `SafetyGuard` antes de salir del teléfono. Solo se permiten:

- OBD estándar de lectura (modos 01, 02, 03, 07, 09, 0A)
- Identificación (`1A`, `22 F1xx`), lectura de códigos (`18`, `19`), TesterPresent (`3E`)
- Sesión por defecto o extendida (`10 01/03/81/92`), solo si un módulo la exige para leer
- Borrado de códigos (`04`, `14`), únicamente tras la confirmación del usuario

Quedan bloqueados SecurityAccess (`27`), escritura (`2E`, `3B`, `3D`), rutinas (`31`), control de E/S (`2F`, `30`),
descarga/flasheo (`34`–`37`), reinicio de módulos (`11`) y sesiones de programación. Hay un test que lo verifica y
otro que comprueba que nada de lo enviado en un escaneo completo cae fuera de la lista.

## Estructura

```
app/src/main/java/ar/garibaldi/obd/
  transport/   Bluetooth SPP y auto simulado
  adapter/     ELM327/STN: inicialización, cabeceras, filtros, control de flujo, errores
  protocol/    ISO-TP, KWP2000/UDS, filtro de seguridad
  obd/         OBD-II genérico: códigos, datos en vivo, monitores, freeze frame
  modules/     descubrimiento y mapa de módulos
  dtc/         decodificador, base de códigos, traducción
  scan/        escaneo completo, historial, informe
  ai/          explicación con Claude (Messages API) y API key cifrada
  log/         registro TX/RX
  ui/          Compose: tema por marca y pantallas
tools/
  build_dtc_db.py   regenera los assets de OBDex
  marcas_dtc.py     regenera marcas.json.gz (códigos propios de cada marca)
  curados.py        fichas curadas en español
```

## Foto del auto en la portada

La app trae una silueta genérica. Para que quede como el diseño de referencia, en **Ajustes → Elegir foto del auto**
cargá un PNG de tu auto con fondo transparente: el nombre del modelo queda por detrás.
