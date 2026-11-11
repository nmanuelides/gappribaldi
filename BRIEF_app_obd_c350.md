# Brief: App Android nativa de diagnóstico para Mercedes-Benz C350 W204 (2007)

## Contexto
- Auto: Mercedes-Benz C350, chasis W204, año 2007, motor V6 M272, caja automática 7G-Tronic (722.9).
- Usuario en Argentina. **Toda la interfaz y los mensajes en español rioplatense, claros, para alguien que no es mecánico.**
- Adaptador: **OBDLink MX+** (chip STN, Bluetooth Classic SPP; soporta comandos ELM327 y extendidos ST).
- Plataforma: **Android nativo**, Kotlin + Jetpack Compose. minSdk 26.
- El usuario va a compilar con Android Studio en su PC e instalar el APK en su celular. Va a haber varias iteraciones probando en el auto: priorizar logs claros y fáciles de compartir.

## Objetivo
1. Conectarse al OBDLink MX+ por Bluetooth.
2. Leer códigos de error de **todos los módulos que respondan**, no solo el motor.
3. Traducir cada código a un mensaje claro: qué significa, causa probable, gravedad (¿puedo seguir manejando?) y qué revisar.
4. Borrar códigos (por módulo o todos), con confirmación.

## Fuera de alcance (NO implementar)
- **Codificación / modificación de configuraciones.** Nada de escribir en módulos.
- No usar servicios de escritura ni de seguridad: KWP/UDS `0x27` (SecurityAccess), `0x2E`/`0x3B` (escritura de datos), `0x31` (RoutineControl), `0x2F`/`0x30` (control de E/S), `0x34`–`0x37` (descarga/flasheo), `0x10` a sesiones de programación.
- Solo están permitidos: lectura de identificación, lectura de DTC, borrado de DTC (`0x14`), TesterPresent y sesión de diagnóstico por defecto/extendida si un módulo lo exige para leer.
- Si en algún momento se agrega codificación, debe ser un proyecto aparte con backups y validación; no en esta app.

## Arquitectura sugerida
- `transport/` — conexión Bluetooth SPP (UUID 00001101-0000-1000-8000-00805F9B34FB), lectura/escritura con timeouts, manejo de reconexión.
- `adapter/` — capa ELM327/STN: inicialización (`ATZ`, `ATE0`, `ATL0`, `ATS0`, `ATH1`, `ATSP6`), cambio de headers (`ATSH`, `ATCRA` / `STFAC`/`STFAP`), detección de `NO DATA`, `CAN ERROR`, `?`.
- `protocol/` — ISO-TP (multi-frame, flow control), KWP2000-sobre-CAN y UDS. Abstracción `DiagnosticSession` por módulo.
- `obd/` — OBD2 genérico: modo 03 (confirmados), 07 (pendientes), 0A (permanentes), 04 (borrar), 01 PID 01 (MIL y cantidad de DTC).
- `modules/` — escaneo de módulos Mercedes.
- `dtc/` — base de datos de códigos y motor de traducción.
- `ui/` — Compose: Conectar → Escanear → Lista de módulos con errores → Detalle del error → Borrar.
- `log/` — log de tramas crudas (TX/RX con timestamp) exportable a archivo y compartible (Intent de compartir).

## Detalles del auto a tener en cuenta
- La W204 temprana expone el **CAN de diagnóstico en los pines 6/14 del OBD2 a 500 kbps**, con IDs de 11 bits.
- Los módulos de esa época usan mayormente **KWP2000 sobre CAN** (lectura de DTC `0x18`, borrado `0x14`, identificación `0x1A`); algunos pueden usar **UDS** (`0x19`, `0x14`, `0x22`). La app debe probar ambos por módulo y recordar cuál funcionó.
- **No hardcodear un mapa de direcciones dado por supuesto.** Implementar un **escaneo de descubrimiento**: recorrer rangos de IDs de request, enviar una petición de identificación inofensiva (`0x1A 0x86`/`0x1A 0x87` en KWP, `0x22 F1 xx` en UDS) y registrar qué ID de respuesta contesta. Guardar el mapa descubierto en el dispositivo.
- Rangos a recorrer con prudencia (timeouts cortos, sin saturar el bus), por ejemplo `0x7E0–0x7EF` (OBD estándar) y luego `0x400–0x7FF` de a poco. Mostrar progreso.
- Antes de escanear: pedir motor apagado y contacto en posición 2 (o motor en marcha), y avisar que la batería tiene que estar bien.

## Traducción de códigos
- **Códigos genéricos (P0xxx, P2xxx, U0xxx, etc.):** base de datos local en español con: título, explicación simple, causas probables ordenadas, gravedad (Verde: puede esperar / Amarillo: revisar pronto / Rojo: no manejar o riesgo de daño) y qué revisar.
- **Notas específicas del M272/C350**: cuando aplique, agregar contexto conocido (p. ej. P0016–P0019 y relación con ejes de balance/engranaje del árbol de levas en motores M272 de esa época; códigos de mezcla pobre P0171/P0174 y fugas de vacío / múltiple de admisión). Marcar estas notas como "frecuente en este motor", no como diagnóstico seguro.
- **Códigos propios de Mercedes** (formato de fabricante, p. ej. por módulo): mostrar el código crudo, el módulo y, si no hay descripción local, ofrecer explicación con IA (ver abajo). Nunca inventar una descripción como si fuera oficial.
- Mostrar también el estado del DTC (actual/almacenado/pendiente) cuando el módulo lo informe.

### Explicación con IA (opcional, fase 2)
- Botón "Explicame este error" que envía código + módulo + auto a la API de Claude y muestra la respuesta en español.
- La API key la ingresa el usuario y se guarda cifrada (EncryptedSharedPreferences). Funciona sin esto si no hay key.
- La respuesta debe aclarar cuándo es una estimación.

## Borrado de códigos
- Borrar por módulo y "borrar todo".
- Confirmación clara antes de borrar, recordando que **borrar no arregla la causa** y que el código puede volver.
- Advertencia reforzada para módulos de **airbag (SRS)**, **ABS/ESP** y **frenos**: recomendar revisar antes de borrar.
- Después de borrar, volver a leer y mostrar qué quedó.

## Fases de desarrollo
1. **Conexión + log crudo + OBD2 genérico** (modo 03/07/04) con traducción de P-codes. Probable que funcione al primer intento: sirve para validar Bluetooth y adaptador.
2. **Descubrimiento de módulos Mercedes** y lectura de DTC por módulo (KWP/UDS). Acá van a hacer falta iteraciones con los logs que mande el usuario.
3. **Borrado por módulo** con las advertencias.
4. Pulido de UI, historial de escaneos guardado localmente, explicación con IA.

## Requisitos de calidad
- Nunca bloquear el hilo principal; corrutinas para I/O.
- Timeouts y reintentos acotados; si un módulo no responde, seguir con el siguiente.
- Pantalla de "Diagnóstico de la conexión" que muestre versión del adaptador (`ATI`, `STI`), voltaje (`ATRV`) y estado.
- Tests unitarios para el parser ISO-TP, el decodificador de DTC y la base de traducciones.
- README con pasos para compilar en Android Studio e instalar el APK en el celular (incluyendo activar la depuración USB).
