package ar.garibaldi.obd.i18n

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * Idioma de la interfaz. La app está escrita en español; en inglés, cada texto que se muestra pasa
 * por [L], que lo busca en [DICCIONARIO]. Las entradas con `{}` son plantillas: lo que va en el hueco
 * se traduce a su vez (sirve para "Batería: {}", "{} (probable)", etc.).
 *
 * [ingles] es estado de Compose: cambiarlo redibuja todas las pantallas al instante.
 */
object Idioma {
    var ingles by mutableStateOf(false)

    private val cache = HashMap<String, String>()

    private class Plantilla(val regex: Regex, val partes: List<String>, val peso: Int)

    private val exactas: Map<String, String>
    private val plantillas: List<Plantilla>

    init {
        val ex = HashMap<String, String>()
        val pl = ArrayList<Plantilla>()
        for ((es, en) in DICCIONARIO) {
            if ("{}" in es) {
                val trozos = es.split("{}")
                val patron = trozos.joinToString("(.+?)") { Regex.escape(it) }
                pl += Plantilla(Regex("^$patron$", RegexOption.DOT_MATCHES_ALL), en.split("{}"), trozos.sumOf { it.length })
            } else {
                ex[es] = en
            }
        }
        exactas = ex
        // Las plantillas más específicas (más texto fijo) se prueban primero
        plantillas = pl.sortedByDescending { it.peso }
    }

    /** Traduce un texto de la interfaz al idioma actual. En español lo devuelve tal cual. */
    fun L(texto: String): String {
        if (!ingles || texto.isBlank()) return texto
        synchronized(cache) { cache[texto]?.let { return it } }
        val r = traducir(texto)
        synchronized(cache) { cache[texto] = r }
        return r
    }

    private fun traducir(texto: String): String {
        if ('\n' in texto) return texto.split('\n').joinToString("\n") { traducir(it) }
        val sinIzq = texto.trimStart()
        if (sinIzq.length != texto.length) return texto.substring(0, texto.length - sinIzq.length) + traducir(sinIzq)
        val sinDer = texto.trimEnd()
        if (sinDer.length != texto.length) return traducir(sinDer) + texto.substring(sinDer.length)
        if (texto.isEmpty()) return texto
        exactas[texto]?.let { return it }
        for (p in plantillas) {
            val m = p.regex.matchEntire(texto) ?: continue
            val sb = StringBuilder(p.partes[0])
            for (i in 1 until p.partes.size) {
                sb.append(traducir(m.groupValues.getOrElse(i) { "" })).append(p.partes[i])
            }
            return sb.toString()
        }
        if (" · " in texto) return texto.split(" · ").joinToString(" · ") { traducir(it) }
        val dosPuntos = texto.indexOf(": ")
        if (dosPuntos > 0) {
            return traducir(texto.substring(0, dosPuntos)) + ": " + traducir(texto.substring(dosPuntos + 2))
        }
        return texto
    }

    /** Elige entre dos textos según el idioma (para lo que no conviene pasar por el diccionario). */
    fun t(es: String, en: String): String = if (ingles) en else es
}

/** Atajo para traducir textos fuera de la interfaz (informe compartido, etc.). */
fun L(texto: String): String = Idioma.L(texto)

private val DICCIONARIO: List<Pair<String, String>> = listOf(
    // ── General / barra superior ──
    "Resultado" to "Results",
    "Detalle" to "Details",
    "Módulos" to "Modules",
    "Datos en vivo" to "Live data",
    "Emisiones" to "Emissions",
    "Conexión" to "Connection",
    "Registro" to "Log",
    "Historial" to "History",
    "Ajustes" to "Settings",
    "Aviso" to "Notice",
    "Entendido" to "Got it",
    "Cancelar" to "Cancel",
    "Cerrar" to "Close",
    "Volver" to "Back",

    // ── Mensajes del ViewModel ──
    "No se pudo usar esa imagen: {}" to "Couldn't use that image: {}",
    "Conectando con {}…" to "Connecting to {}…",
    "Configurando el adaptador…" to "Setting up the adapter…",
    "No se pudo conectar" to "Couldn't connect",
    "Primero conectate al adaptador." to "Connect to the adapter first.",
    "Se perdió la conexión" to "Connection lost",
    "Se perdió la conexión con el adaptador. Volvé a conectar." to "The connection to the adapter was lost. Please reconnect.",
    "Algo salió mal: {}. Mirá el registro para más detalle." to "Something went wrong: {}. Check the log for details.",
    "Búsqueda completa de módulos" to "Full module search",
    "Buscando módulos" to "Searching for modules",
    "Dirección {} de {}" to "Address {} of {}",
    "{} módulos encontrados" to "{} modules found",
    "Se encontraron {} módulos." to "Found {} modules.",
    "Escaneando el auto" to "Scanning the car",
    "Borrando todos los códigos" to "Clearing all codes",
    "Borrando códigos" to "Clearing codes",
    "Borrando códigos de emisiones (OBD)…" to "Clearing emissions codes (OBD)…",
    "OBD estándar: el auto no confirmó el borrado" to "Standard OBD: the car didn't confirm the clear",
    "Borrando {}…" to "Clearing {}…",
    "Borrado hecho. Abajo está lo que quedó después de volver a leer." to "Codes cleared. Below is what remained after reading again.",
    "No se pudo borrar todo:" to "Couldn't clear everything:",
    "Leyendo monitores de emisiones" to "Reading emissions monitors",
    "Se cortó la lectura en vivo: {}" to "Live reading stopped: {}",
    "No se pudo bajar el paquete de traducción. Revisá la conexión a internet." to "Couldn't download the translation pack. Check your internet connection.",
    "API key guardada (cifrada en este teléfono)." to "API key saved (encrypted on this phone).",
    "API key eliminada." to "API key deleted.",
    "Para usar la explicación con IA cargá tu API key en Ajustes." to "To use the AI explanation, add your API key in Settings.",
    "No se pudo consultar a la IA: {}" to "Couldn't reach the AI: {}",
    "La API key no es válida o no tiene permiso. Revisala en Ajustes." to "The API key is invalid or lacks permission. Check it in Settings.",
    "Demasiadas consultas seguidas o sin crédito. Esperá un momento y probá de nuevo." to "Too many requests or no credit left. Wait a moment and try again.",
    "El servicio de IA está con problemas ({}). Probá más tarde." to "The AI service is having problems ({}). Try again later.",
    "La IA rechazó la consulta ({}): {}" to "The AI rejected the request ({}): {}",
    "La IA rechazó la consulta ({})." to "The AI rejected the request ({}).",
    "La IA no pudo responder esta consulta." to "The AI couldn't answer this request.",
    "La IA devolvió una respuesta vacía. Probá de nuevo." to "The AI returned an empty answer. Try again.",
    "No hay conexión a internet para consultar a la IA." to "No internet connection to reach the AI.",

    // ── Inicio ──
    "Foto del auto" to "Car photo",
    "Sin el permiso de \"Dispositivos cercanos\" no se puede usar el Bluetooth. Podés darlo desde los ajustes del teléfono." to
        "Without the \"Nearby devices\" permission Bluetooth can't be used. You can grant it in the phone's settings.",
    "Modo demostración" to "Demo mode",
    "Conectado a {}" to "Connected to {}",
    "Batería: {}" to "Battery: {}",
    "Escanear el auto  →" to "Scan the car  →",
    "No se pudo conectar" to "Couldn't connect",
    "Revisá que el OBDLink esté enchufado, con el contacto puesto, y que ninguna otra app lo esté usando." to
        "Check that the OBDLink is plugged in, the ignition is on, and no other app is using it.",
    "Enchufá el OBDLink MX+ al auto, poné el contacto y conectate para leer los códigos de falla." to
        "Plug the OBDLink MX+ into the car, turn the ignition on and connect to read the fault codes.",
    "Conectar al adaptador  →" to "Connect to adapter  →",
    "Probar sin el auto (modo demo)" to "Try without the car (demo mode)",
    "Último escaneo" to "Last scan",
    "{} códigos en {} módulos" to "{} codes in {} modules",
    "Antes de escanear" to "Before scanning",
    "Escanear" to "Scan",
    "• Motor apagado y contacto en posición 2 (o motor en marcha)." to "• Engine off and ignition in position 2 (or engine running).",
    "• La batería tiene que estar bien: con poca carga aparecen fallas falsas." to "• The battery must be in good shape: a weak battery causes false faults.",
    "• No apagues el contacto ni desenchufes el adaptador hasta que termine." to "• Don't turn the ignition off or unplug the adapter until it finishes.",
    "Es la primera vez: primero se buscan los módulos del auto (tarda unos segundos)." to "First time: the car's modules are searched first (takes a few seconds).",
    "⚠ La batería marca {}: está baja. Conviene cargarla o poner el motor en marcha." to "⚠ The battery reads {}: it's low. Charge it or start the engine.",
    "Este teléfono no tiene Bluetooth." to "This phone has no Bluetooth.",
    "El Bluetooth está apagado. Prendelo y volvé a entrar." to "Bluetooth is off. Turn it on and come back.",
    "No hay dispositivos emparejados." to "No paired devices.",
    "Elegí el adaptador:" to "Choose the adapter:",
    "Adaptador OBD" to "OBD adapter",
    "Ajustes de Bluetooth" to "Bluetooth settings",
    "Sin nombre" to "No name",
    "último usado" to "last used",
    "¿No aparece el OBDLink MX+? Apretá el botón del adaptador (la luz azul parpadea rápido) y emparejalo desde los ajustes de Bluetooth del teléfono." to
        "OBDLink MX+ not listed? Press the adapter's button (the blue light blinks fast) and pair it from the phone's Bluetooth settings.",
    "Desconectar" to "Disconnect",

    // ── Resultado ──
    "Todavía no hay ningún escaneo." to "No scans yet.",
    "código de falla" to "fault code",
    "códigos de falla" to "fault codes",
    "del historial" to "from history",
    "Lo más grave: {}" to "Most serious: {}",
    "No se encontraron fallas guardadas." to "No stored faults found.",
    "Testigo de motor: encendido" to "Check engine light: on",
    "Testigo de motor: apagado" to "Check engine light: off",
    "Testigo de motor: ENCENDIDO" to "Check engine light: ON",
    "Testigo de motor encendido" to "Check engine light on",
    "Testigo de motor apagado" to "Check engine light off",
    "Chasis: {}" to "VIN: {}",
    "Descripciones en inglés" to "Descriptions in English",
    "Algunos códigos solo tienen descripción en inglés. Se pueden traducir en el teléfono bajando, una sola vez, un paquete de unos 30 MB." to
        "Some codes only have a description in English. They can be translated on the phone by downloading a ~30 MB pack once.",
    "Ahora no" to "Not now",
    "Bajar traducción" to "Download translation",
    "Bajando el paquete de traducción…" to "Downloading the translation pack…",
    "Monitores de emisiones sin completar" to "Incomplete emissions monitors",
    "Es normal después de borrar códigos o desconectar la batería: se completan solos andando unos días." to
        "That's normal after clearing codes or disconnecting the battery: they complete on their own after a few days of driving.",
    "Compartir informe" to "Share report",
    "Diagnóstico del auto" to "Car diagnosis",
    "Borrar todo" to "Clear all",
    "Volver a escanear" to "Scan again",
    "¿Borrar todos los códigos?" to "Clear all codes?",
    "¿Borrar los códigos de {}?" to "Clear the codes of {}?",
    "Borrar no arregla la causa: si la falla sigue, el código va a volver." to "Clearing doesn't fix the cause: if the fault persists, the code will come back.",
    "Antes de borrar conviene compartir o anotar el informe, porque los códigos guardados son la pista para el mecánico." to
        "Before clearing, share or note down the report: stored codes are the clue for the mechanic.",
    "Al borrar los códigos de emisiones también se reinician los monitores del motor." to "Clearing emissions codes also resets the engine's monitors.",
    "Sí, borrar" to "Yes, clear",
    "Atención: seguridad" to "Warning: safety",
    "Hay códigos en módulos de airbag, frenos/ABS/ESP o en módulos sin identificar. Una falla ahí puede significar que el sistema no te va a proteger. Hacelo revisar antes de borrar." to
        "There are codes in airbag, brakes/ABS/ESP or unidentified modules. A fault there may mean the system won't protect you. Have it checked before clearing.",
    "SIN LEER" to "NOT READ",
    "SIN FALLAS" to "NO FAULTS",
    "NUEVO" to "NEW",
    "Código propio de {} · tocá para ver opciones" to "{}-specific code · tap for options",
    "Borrar códigos de este módulo" to "Clear this module's codes",
    "No se pudo leer: {}" to "Couldn't read: {}",
    "Sin códigos" to "No codes",
    "■ {}" to "■ {}",

    // ── Detalle ──
    "Ese código ya no está en el resultado." to "That code is no longer in the results.",
    "ESTIMADA" to "ESTIMATED",
    "La gravedad se calculó con reglas generales; tomala como orientación." to "Severity was estimated with general rules; take it as a guide.",
    "Sin descripción local" to "No local description",
    "Este código es propio de {} para este módulo y su significado exacto solo figura en la documentación del fabricante. No lo vamos a adivinar. Podés buscarlo en la web o pedirle una explicación orientativa a la IA." to
        "This is a {}-specific code for this module and its exact meaning is only in the manufacturer's documentation. We won't guess. You can search the web or ask the AI for an approximate explanation.",
    "Dato crudo: {}" to "Raw data: {}",
    "en formato SAE: {}" to "in SAE format: {}",
    "Qué significa" to "What it means",
    "FRECUENTE EN ESTE AUTO" to "COMMON ON THIS CAR",
    "Causas probables" to "Likely causes",
    "Probabilidad alta" to "High likelihood",
    "Probabilidad media" to "Medium likelihood",
    "Probabilidad baja" to "Low likelihood",
    "Qué podés notar" to "What you may notice",
    "Qué revisar" to "What to check",
    "Traducción automática de la base abierta OBDex: puede tener errores. Título original: {}" to
        "Machine translation: it may contain errors. Original title: {}",
    "Descripción en inglés de la base abierta OBDex." to "Description from the open OBDex database.",
    "Ojo: es la descripción genérica de la norma para {}. Este módulo podría usarlo con un matiz distinto." to
        "Note: this is the standard generic description for {}. This module might use it with a slightly different meaning.",
    "¿Querés saber más?" to "Want to know more?",
    "EXPLICACIÓN DE LA IA · ORIENTATIVA" to "AI EXPLANATION · APPROXIMATE",
    "Generada por Claude. Puede equivocarse: confirmá con un mecánico antes de gastar plata." to
        "Generated by Claude. It can be wrong: confirm with a mechanic before spending money.",
    "Consultando a la IA…" to "Asking the AI…",
    "Buscar en la web" to "Search the web",
    "Explicame este error" to "Explain this fault",
    "La explicación con IA es opcional: necesita una API key de Anthropic, que se carga en Ajustes." to
        "The AI explanation is optional: it needs an Anthropic API key, which you add in Settings.",
    "Códigos relacionados: {}" to "Related codes: {}",

    // ── Módulos ──
    "Cada módulo es una computadora del auto. La app los encuentra preguntando dirección por dirección y guarda el mapa en el teléfono. Los nombres marcados como \"probable\" son una suposición: tocá un módulo para ponerle el nombre correcto." to
        "Each module is one of the car's computers. The app finds them by querying address by address and saves the map on the phone. Names marked \"probable\" are a guess: tap a module to give it the right name.",
    "Búsqueda rápida" to "Quick search",
    "Búsqueda completa" to "Full search",
    "Conectate al adaptador para buscar módulos." to "Connect to the adapter to search for modules.",
    "Todavía no hay módulos guardados." to "No saved modules yet.",
    "Dirección {}" to "Address {}",
    "Olvidar el mapa de módulos" to "Forget the module map",
    "Recorre unas 900 direcciones del bus de diagnóstico con pedidos inofensivos de lectura. Tarda entre 3 y 5 minutos. Dejá el contacto puesto y no uses otras funciones del auto mientras tanto." to
        "Goes through about 900 diagnostic bus addresses with harmless read requests. Takes 3 to 5 minutes. Keep the ignition on and don't use other car functions meanwhile.",
    "Conviene hacerla una vez, con la batería bien cargada." to "Best done once, with a well-charged battery.",
    "Empezar" to "Start",
    "¿Qué módulo es?" to "Which module is it?",
    "Dirección {}. Elegí de la lista o escribí un nombre." to "Address {}. Pick from the list or type a name.",
    "Guardar nombre" to "Save name",
    "Nombre propio" to "Custom name",
    "Otro" to "Other",

    // ── Datos en vivo / emisiones ──
    "Conectate al adaptador para ver datos en vivo." to "Connect to the adapter to see live data.",
    "Preguntándole al motor qué datos ofrece…" to "Asking the engine which data it offers…",
    "Valores que informa la computadora del motor, en tiempo real. Son solo de lectura." to "Values reported by the engine computer in real time. Read-only.",
    "Conectate al adaptador para leer los monitores." to "Connect to the adapter to read the monitors.",
    "Leyendo…" to "Reading…",
    "El auto no respondió al pedido de estado de emisiones." to "The car didn't answer the emissions status request.",
    "Motor" to "Engine",
    "Módulo de emisiones {}" to "Emissions module {}",
    "Módulo {}" to "Module {}",
    "{} códigos confirmados" to "{} confirmed codes",
    "listo" to "ready",
    "sin completar" to "incomplete",
    "Los monitores son autochequeos que el motor hace mientras andás. Después de borrar códigos o desconectar la batería quedan \"sin completar\" y se van completando solos con el uso normal." to
        "Monitors are self-tests the engine runs while you drive. After clearing codes or disconnecting the battery they show \"incomplete\" and complete on their own with normal use.",
    "Foto del momento de la falla" to "Snapshot at the time of the fault",
    "Así estaba el motor cuando se guardó el código {}:" to "This is how the engine was when code {} was stored:",

    // ── Conexión ──
    "Chip (ATI): {}" to "Chip (ATI): {}",
    "no es un adaptador STN" to "not an STN adapter",
    "Firmware (STI): {}" to "Firmware (STI): {}",
    "Modelo (STDI): {}" to "Model (STDI): {}",
    "sin dato" to "no data",
    "Tensión (ATRV): {}" to "Voltage (ATRV): {}",
    "Comunicación con el auto" to "Communication with the car",
    "Protocolo: CAN 11 bits, 500 kbps (ISO 15765-4), pines 6 y 14 del conector." to "Protocol: CAN 11-bit, 500 kbps (ISO 15765-4), connector pins 6 and 14.",
    "Seguridad" to "Safety",
    "La app solo manda pedidos de lectura (identificación, códigos, datos OBD) y el borrado de códigos con confirmación. Cualquier otro pedido (codificación, escritura, pruebas de actuadores, acceso de seguridad, flasheo) queda bloqueado antes de salir del teléfono." to
        "The app only sends read requests (identification, codes, OBD data) and code clearing after confirmation. Any other request (coding, writing, actuator tests, security access, flashing) is blocked before it leaves the phone.",
    "Error de conexión" to "Connection error",
    "Sin conexión." to "Not connected.",
    "Ver registro de comunicación" to "View communication log",
    "Todo lo que la app le manda al adaptador (TX) y lo que recibe (RX). Si algo falla, compartí este archivo." to
        "Everything the app sends to the adapter (TX) and receives (RX). If something fails, share this file.",
    "Compartir registro" to "Share log",

    // ── Historial ──
    "Todavía no hay escaneos guardados. Cada escaneo del auto queda acá." to "No saved scans yet. Every car scan is kept here.",
    "{} códigos" to "{} codes",
    "{} módulos" to "{} modules",
    "Borrar" to "Delete",

    // ── Ajustes ──
    "Apariencia" to "Appearance",
    "Idioma" to "Language",
    "Elegí la marca de tu auto: cambia los colores, el logo y el modelo de la portada." to "Choose your car's brand: it changes the colors, the logo and the model on the home screen.",
    "Con esta marca se leen los códigos de motor y emisiones (OBD-II, iguales en todos los autos). Los demás módulos se pueden buscar en Módulos → Búsqueda completa, pero sus códigos propios no tienen descripción." to
        "With this brand the app reads engine and emissions codes (OBD-II, the same on every car). Other modules can be found in Modules → Full search, but their brand-specific codes have no description.",
    "Dirección asistida" to "Power steering",
    "Radio / multimedia" to "Radio / media",
    "Texto del modelo en la portada" to "Model text on the home screen",
    "Elegir foto del auto" to "Choose car photo",
    "Quitar foto" to "Remove photo",
    "Para que quede como en el diseño, usá un PNG del auto con fondo transparente: el nombre del modelo queda por detrás." to
        "For the intended look, use a PNG of the car with a transparent background: the model name sits behind it.",
    "Traducción de códigos" to "Code translation",
    "La app trae fichas en español para los códigos más comunes de este auto y una base abierta de 9.533 códigos genéricos en inglés. Esos textos se pueden traducir en el teléfono (paquete de ~30 MB, una sola descarga)." to
        "The app includes Spanish write-ups for this car's most common codes and an open database of 9,533 generic codes in English. Texts in the other language can be translated on the phone (~30 MB pack, one download).",
    "Desactivar traducción" to "Turn off translation",
    "Activar traducción" to "Turn on translation",
    "Explicación con IA (opcional)" to "AI explanation (optional)",
    "El botón \"Explicame este error\" le consulta a Claude ({}). Necesita una API key de Anthropic (console.anthropic.com); cada consulta tiene un costo que se cobra en tu cuenta. La key se guarda cifrada en este teléfono y solo se manda a Anthropic. Sin key, el resto de la app funciona igual." to
        "The \"Explain this fault\" button asks Claude ({}). It needs an Anthropic API key (console.anthropic.com); each request has a cost charged to your account. The key is stored encrypted on this phone and only sent to Anthropic. Without a key, the rest of the app works the same.",
    "Estado: hay una API key guardada." to "Status: an API key is saved.",
    "Estado: sin API key." to "Status: no API key.",
    "API key (sk-ant-…)" to "API key (sk-ant-…)",
    "Guardar key" to "Save key",
    "Borrar key" to "Delete key",
    "Acerca de" to "About",
    "Gappribaldi {}. Hecha para un Mercedes-Benz C350 W204 (M272, 7G-Tronic) con OBDLink MX+." to
        "Gappribaldi {}. Made for a Mercedes-Benz C350 W204 (M272, 7G-Tronic) with an OBDLink MX+.",
    "Solo lee y borra códigos: no codifica ni escribe en los módulos." to "It only reads and clears codes: it never codes or writes to the modules.",
    "Códigos genéricos: base OBDex (github.com/foerbsnavi/OBDex), dominio público CC0. Tipografías Anton y Jost (licencia OFL). Las notas \"frecuente en este auto\" son orientativas y no reemplazan el diagnóstico de un mecánico." to
        "Generic codes: OBDex database (github.com/foerbsnavi/OBDex), public domain CC0. Anton and Jost typefaces (OFL license). \"Common on this car\" notes are a guide and don't replace a mechanic's diagnosis.",

    // ── Escaneo / informe ──
    "Leyendo datos de emisiones (OBD estándar)…" to "Reading emissions data (standard OBD)…",
    "Motor — OBD estándar" to "Engine — standard OBD",
    "Caja — OBD estándar" to "Transmission — standard OBD",
    "Códigos de emisiones que exige la norma OBD-II" to "Emissions codes required by the OBD-II standard",
    "Leyendo {}…" to "Reading {}…",
    "Listo" to "Done",
    "protocolo sin detectar" to "protocol not detected",
    "Diagnóstico {} — {}" to "{} diagnosis — {}",
    "Chasis (VIN): {}" to "VIN: {}",
    "Códigos encontrados: {}" to "Codes found: {}",
    "código propio de {}, sin descripción local" to "{}-specific code, no local description",
    "Estado: {}" to "Status: {}",
    "Gravedad estimada: {}" to "Estimated severity: {}",
    "Gravedad: {}" to "Severity: {}",
    "Monitores de emisiones sin completar: {}" to "Incomplete emissions monitors: {}",
    "Generado con Gappribaldi. Es una ayuda orientativa, no reemplaza el diagnóstico de un mecánico." to
        "Generated with Gappribaldi. It's a guide and doesn't replace a mechanic's diagnosis.",

    // ── Estado de un código ──
    "Presente ahora" to "Present now",
    "Guardado (no presente ahora)" to "Stored (not present now)",
    "guardado" to "stored",
    "Pendiente (todavía no confirmado)" to "Pending (not yet confirmed)",
    "enciende testigo" to "turns on warning light",
    "Sin estado informado" to "No status reported",

    // ── Gravedad ──
    "Puede esperar" to "Can wait",
    "Podés seguir usando el auto. Hacelo ver cuando tengas oportunidad." to "You can keep using the car. Have it checked when you get the chance.",
    "Revisar pronto" to "Check soon",
    "Podés manejar con cuidado, pero hacelo revisar en los próximos días." to "You can drive carefully, but have it checked in the next few days.",
    "No conviene manejar" to "Better not to drive",
    "Riesgo de daño o de seguridad. Evitá usar el auto hasta revisarlo." to "Risk of damage or safety issue. Avoid using the car until it's checked.",
    "Código propio de {}" to "{}-specific code",
    "Código propio de {} · {} significados posibles" to "{}-specific code · {} possible meanings",
    "SIGNIFICADOS POSIBLES SEGÚN MERCEDES" to "POSSIBLE MEANINGS ACCORDING TO MERCEDES",
    "TEXTO DE MERCEDES" to "MERCEDES TEXT",
    "¿Qué auto tenés?" to "What car do you have?",
    "Elegí la marca: la app se adapta a tus colores y a lo que puede leer de ese auto." to
        "Choose the brand: the app adapts its colors and what it can read from that car.",
    "Marcar {} como favorita" to "Mark {} as favorite",
    "La app va a abrir directo con esta marca. Se cambia desde Ajustes." to "The app will open straight with this brand. You can change it in Settings.",
    "Si no marcás una favorita, la app te pregunta cada vez que se abre." to "If you don't mark a favorite, the app asks every time it opens.",
    "Continuar con {}  →" to "Continue with {}  →",
    "Favorita" to "Favorite",
    "Favorita: la app abre directo con esta marca." to "Favorite: the app opens straight with this brand.",
    "Sin favorita: la app pregunta la marca cada vez que se abre." to "No favorite: the app asks for the brand every time it opens.",
    "Cambiar" to "Change",
    "No sabemos qué módulo es, así que el mismo número puede significar cosas distintas. Ponele nombre al módulo (en Módulos) para quedarte con el que corresponde." to
        "We don't know which module this is, so the same number can mean different things. Name the module (in Modules) to keep only the right one.",
    "Es un evento: un registro informativo, no una falla." to "It's an event: an informational record, not a fault.",
    "Fuente: foros y talleres especializados en el W204. Código {}." to "Source: W204 forums and specialist shops. Code {}.",
    "Fuente: lista de diagnóstico de Mercedes (DAS) de un {}, de la misma época que el W204. En tu auto el texto puede variar. Código {}." to
        "Source: Mercedes diagnostic list (DAS) from a {}, the same era as the W204. The text may differ on your car. Code {}.",
    "Traducción automática del original en inglés: puede tener errores." to "Machine translation of the English original: it may have errors.",
    "Motor ME 9.7 (M272/M273)" to "ME 9.7 engine (M272/M273)",
    "Caja 7G-Tronic (722.9)" to "7G-Tronic gearbox (722.9)",
    "SAM delantero" to "Front SAM",
    "SAM trasero" to "Rear SAM",
    "Módulo de la columna de dirección" to "Steering column module",
    "Panel de techo / panel superior" to "Overhead / upper control panel",
    "Radio / COMAND y audio" to "Radio / COMAND and audio",

    // ── Categorías y nombres de módulos ──
    "Caja automática" to "Automatic transmission",
    "Frenos / ABS / ESP" to "Brakes / ABS / ESP",
    "Airbag (SRS)" to "Airbag (SRS)",
    "Llave y arranque" to "Key and ignition",
    "Carrocería y luces" to "Body and lights",
    "Tablero" to "Instrument cluster",
    "Confort" to "Comfort",
    "Sin identificar" to "Unidentified",
    "Motor (ME 9.7)" to "Engine (ME 9.7)",
    "Caja automática (7G-Tronic)" to "Automatic transmission (7G-Tronic)",
    "Frenos / ESP" to "Brakes / ESP",
    "Llave y arranque (EZS)" to "Key and ignition (EZS)",
    "Traba de dirección (ELV)" to "Steering lock (ELV)",
    "SAM delantero (luces y fusibles)" to "Front SAM (lights and fuses)",
    "Tablero de instrumentos" to "Instrument cluster",
    "Climatizador" to "Climate control",
    "Radio / COMAND" to "Radio / COMAND",
    "Puerta delantera izquierda" to "Front left door",
    "Puerta delantera derecha" to "Front right door",
    "Asiento con memoria" to "Memory seat",
    "Sensores de estacionamiento" to "Parking sensors",
    "Presión de neumáticos" to "Tire pressure",
    "Panel de techo" to "Overhead control panel",
    "Faros de xenón" to "Xenon headlights",
    "{} (probable)" to "{} (probable)",

    // ── Datos en vivo (PIDs) ──
    "Vueltas del motor" to "Engine speed",
    "Temperatura del refrigerante" to "Coolant temperature",
    "En caliente, normal entre 85 y 105 °C." to "When warm, normal is 85 to 105 °C.",
    "Carga del motor" to "Engine load",
    "Ajuste corto de nafta, banco 1" to "Short-term fuel trim, bank 1",
    "Ajuste largo de nafta, banco 1" to "Long-term fuel trim, bank 1",
    "Ajuste corto de nafta, banco 2" to "Short-term fuel trim, bank 2",
    "Ajuste largo de nafta, banco 2" to "Long-term fuel trim, bank 2",
    "Cuánto corrige la computadora la nafta. Normal: entre -10 % y +10 %. Muy positivo = mezcla pobre (entra aire de más o falta nafta); muy negativo = mezcla rica." to
        "How much the computer corrects fuel. Normal: between -10 % and +10 %. Very positive = lean mixture (extra air or too little fuel); very negative = rich mixture.",
    "Caudal de aire (MAF)" to "Air flow (MAF)",
    "En ralentí caliente un V6 3.5 ronda 4 a 7 g/s." to "At warm idle a 3.5 V6 reads about 4 to 7 g/s.",
    "Presión en el múltiple" to "Manifold pressure",
    "Temperatura del aire de admisión" to "Intake air temperature",
    "Mariposa" to "Throttle",
    "Avance de encendido" to "Ignition timing advance",
    "Velocidad" to "Vehicle speed",
    "Tensión en la computadora" to "Control module voltage",
    "Con motor en marcha, normal entre 13,5 y 14,7 V." to "With the engine running, normal is 13.5 to 14.7 V.",
    "Sonda lambda banco 1, sensor 1" to "O2 sensor bank 1, sensor 1",
    "Sonda lambda banco 1, sensor 2" to "O2 sensor bank 1, sensor 2",
    "Sonda lambda banco 2, sensor 1" to "O2 sensor bank 2, sensor 1",
    "Sonda lambda banco 2, sensor 2" to "O2 sensor bank 2, sensor 2",
    "Nivel de nafta" to "Fuel level",
    "Presión atmosférica" to "Barometric pressure",
    "Tiempo desde el arranque" to "Time since start",
    "Distancia con el testigo encendido" to "Distance with warning light on",
    "Distancia desde el último borrado" to "Distance since codes cleared",

    // ── Monitores ──
    "Fallas de encendido" to "Misfire",
    "Sistema de combustible" to "Fuel system",
    "Componentes generales" to "Comprehensive components",
    "Catalizador" to "Catalyst",
    "Catalizador calefaccionado" to "Heated catalyst",
    "Sistema evaporativo (vapores de nafta)" to "Evaporative system (fuel vapor)",
    "Aire secundario" to "Secondary air",
    "Refrigerante del A/C" to "A/C refrigerant",
    "Sondas lambda" to "O2 sensors",
    "Calefacción de sondas lambda" to "O2 sensor heaters",

    // ── Respuestas negativas de los módulos ──
    "rechazo general" to "general reject",
    "servicio no soportado" to "service not supported",
    "subfunción no soportada" to "sub-function not supported",
    "largo de mensaje incorrecto" to "incorrect message length",
    "módulo ocupado, reintentar" to "module busy, retry",
    "condiciones no correctas (p. ej. motor en marcha o contacto mal puesto)" to "conditions not correct (e.g. engine running or wrong ignition position)",
    "pedido fuera de rango" to "request out of range",
    "requiere acceso de seguridad (no lo hacemos)" to "requires security access (we don't do that)",
    "el módulo sigue procesando" to "module still processing",
    "no disponible en la sesión actual" to "not available in the current session",
    "motivo desconocido" to "unknown reason",
    "{} (0x{})" to "{} (0x{})",
    "El módulo no respondió" to "The module didn't respond",
    "El módulo rechazó la lectura: {}" to "The module rejected the read: {}",
    "Error del adaptador: {}" to "Adapter error: {}",
    "El módulo no aceptó el borrado: {}" to "The module didn't accept the clear: {}",
    "El módulo no respondió al borrado" to "The module didn't respond to the clear",
    "Pedido {} bloqueado: esta app solo lee y borra códigos." to "Request {} blocked: this app only reads and clears codes.",

    // ── Adaptador / Bluetooth ──
    "Se cortó la conexión con el adaptador ({})" to "The connection to the adapter dropped ({})",
    "Se cortó la conexión con el adaptador" to "The connection to the adapter dropped",
    "El adaptador no respondió a '{}'" to "The adapter didn't respond to '{}'",
    "El auto responde ({})" to "The car responds ({})",
    "Sin comunicación con el auto: {}. ¿Está el contacto puesto?" to "No communication with the car: {}. Is the ignition on?",
    "El auto no respondió. Revisá que el contacto esté en posición 2." to "The car didn't respond. Check that the ignition is in position 2.",
    "No se pudo conectar por Bluetooth con {}: {}" to "Couldn't connect over Bluetooth to {}: {}",
    "Sin conexión Bluetooth" to "No Bluetooth connection",

    // ── Actualizaciones y novedades ──
    "Versión nueva disponible" to "New version available",
    "Actualizar" to "Update",
    "Bajando la actualización…" to "Downloading the update…",
    "No se pudo actualizar: {}" to "Couldn't update: {}",
    "Para actualizar, permití que Gappribaldi instale apps y volvé a tocar Actualizar." to
        "To update, allow Gappribaldi to install apps and tap Update again.",
    "NOVEDADES" to "NEWS",
    "Abrir link" to "Open link",
    "Actualizaciones" to "Updates",
    "Versión instalada: {}" to "Installed version: {}",
    "La app se fija sola si hay una versión nueva cada vez que la abrís." to "The app checks for a new version on its own every time you open it.",
    "Buscando actualizaciones…" to "Checking for updates…",
    "Tenés la última versión." to "You have the latest version.",
    "Hay una versión nueva compilándose" to "A new version is being built",
    "Hay una versión nueva compilándose ({})" to "A new version is being built ({})",
    "Se avisa sola en la pantalla de inicio cuando esté lista." to "It will show up on the home screen when it's ready.",
    "Buscar actualizaciones" to "Check for updates",

    // ── Traducción de fichas ──
    "Algunas notas están en español" to "Some notes are in Spanish",
    "Algunas notas solo están en español. Se pueden traducir en el teléfono bajando, una sola vez, un paquete de unos 30 MB." to
        "Some notes are only in Spanish. They can be translated on the phone by downloading a ~30 MB pack once.",
)
