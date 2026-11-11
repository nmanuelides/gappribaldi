package ar.garibaldi.obd.modules

import ar.garibaldi.obd.log.FrameLog
import ar.garibaldi.obd.protocol.DiagResultado
import ar.garibaldi.obd.protocol.Diagnostico
import ar.garibaldi.obd.protocol.Protocolo
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

enum class Categoria(val etiqueta: String, val seguridad: Boolean = false) {
    MOTOR("Motor"),
    CAJA("Caja automática"),
    FRENOS("Frenos / ABS / ESP", seguridad = true),
    AIRBAG("Airbag (SRS)", seguridad = true),
    ARRANQUE("Llave y arranque"),
    CARROCERIA("Carrocería y luces"),
    TABLERO("Tablero"),
    CONFORT("Confort"),
    OTRO("Otro"),
    DESCONOCIDO("Sin identificar"),
}

/** Un módulo (computadora) del auto que contestó en el CAN de diagnóstico. */
data class Modulo(
    val idPedido: Int,
    val idRespuesta: Int,
    val nombre: String,
    val categoria: Categoria,
    val protocolo: Protocolo? = null,
    val identificacion: Map<String, String> = emptyMap(),
    val nombreConfirmado: Boolean = false,
) {
    val direccion: String get() = "%03X → %03X".format(idPedido, idRespuesta)
    val trenMotriz: Boolean get() = categoria == Categoria.MOTOR || categoria == Categoria.CAJA
}

/**
 * Lo poco que se sabe públicamente de las direcciones del W204. Se usa solo como
 * sugerencia de nombre ("probable"); lo que manda es lo que el auto contesta.
 * Fuentes: pares OBD estándar ISO 15765-4 y el esquema de Daimler
 * pedido 0x602 + 16·n / respuesta 0x480 + 2·n documentado por proyectos abiertos.
 */
object CatalogoModulos {
    private val sugeridos = mapOf(
        0x7E0 to ("Motor (ME 9.7)" to Categoria.MOTOR),
        0x7E1 to ("Caja automática (7G-Tronic)" to Categoria.CAJA),
        0x612 to ("Llave y arranque (EZS)" to Categoria.ARRANQUE),
        0x632 to ("Frenos / ESP" to Categoria.FRENOS),
    )

    /** Opciones para que el usuario le ponga nombre a un módulo sin identificar. */
    val opciones = listOf(
        "Motor (ME 9.7)" to Categoria.MOTOR,
        "Caja automática (7G-Tronic)" to Categoria.CAJA,
        "Frenos / ESP" to Categoria.FRENOS,
        "Airbag (SRS)" to Categoria.AIRBAG,
        "Llave y arranque (EZS)" to Categoria.ARRANQUE,
        "Traba de dirección (ELV)" to Categoria.ARRANQUE,
        "SAM delantero (luces y fusibles)" to Categoria.CARROCERIA,
        "SAM trasero" to Categoria.CARROCERIA,
        "Tablero de instrumentos" to Categoria.TABLERO,
        "Módulo de la columna de dirección" to Categoria.CARROCERIA,
        "Climatizador" to Categoria.CONFORT,
        "Radio / COMAND" to Categoria.CONFORT,
        "Puerta delantera izquierda" to Categoria.CONFORT,
        "Puerta delantera derecha" to Categoria.CONFORT,
        "Asiento con memoria" to Categoria.CONFORT,
        "Sensores de estacionamiento" to Categoria.CONFORT,
        "Presión de neumáticos" to Categoria.FRENOS,
        "Panel de techo" to Categoria.CONFORT,
        "Faros de xenón" to Categoria.CARROCERIA,
        "Otro" to Categoria.OTRO,
    )

    fun nuevo(idPedido: Int, idRespuesta: Int): Modulo {
        val s = sugeridos[idPedido]
        return if (s != null) Modulo(idPedido, idRespuesta, s.first + " (probable)", s.second)
        else Modulo(idPedido, idRespuesta, "Módulo %03X".format(idPedido), Categoria.DESCONOCIDO)
    }

    /** Candidatos del escaneo rápido: pares OBD estándar y el esquema de Daimler. */
    fun candidatosRapidos(): List<Int> = (0x7E0..0x7E7).toList() + (0..0x1F).map { 0x602 + it * 0x10 }

    /**
     * Escaneo completo. Se saltean los IDs funcionales (7DF, 441: le hablan a todos a la vez)
     * y los rangos donde los módulos transmiten sus respuestas, para no pisarlos en el bus.
     */
    fun candidatosCompletos(): List<Int> =
        ((0x400..0x47F) + (0x500..0x7E7) + (0x7F0..0x7FF)).filter { it != 0x7DF && it != 0x441 }
}

/** Guarda en el teléfono el mapa de módulos descubierto, para no volver a buscar cada vez. */
class AlmacenModulos(carpeta: File) {
    private val archivo = File(carpeta, "modulos.json")

    fun cargar(): List<Modulo> = try {
        val arr = JSONArray(archivo.readText())
        (0 until arr.length()).map { i ->
            val o = arr.getJSONObject(i)
            val ident = LinkedHashMap<String, String>()
            o.optJSONObject("ident")?.let { j -> j.keys().forEach { k -> ident[k] = j.getString(k) } }
            Modulo(
                idPedido = o.getInt("req"),
                idRespuesta = o.getInt("resp"),
                nombre = o.getString("nombre"),
                categoria = runCatching { Categoria.valueOf(o.getString("cat")) }.getOrDefault(Categoria.DESCONOCIDO),
                protocolo = o.optString("proto").takeIf { it.isNotEmpty() }?.let { runCatching { Protocolo.valueOf(it) }.getOrNull() },
                identificacion = ident,
                nombreConfirmado = o.optBoolean("confirmado"),
            )
        }
    } catch (_: Exception) {
        emptyList()
    }

    fun guardar(modulos: List<Modulo>) {
        val arr = JSONArray()
        for (m in modulos) {
            arr.put(
                JSONObject()
                    .put("req", m.idPedido).put("resp", m.idRespuesta)
                    .put("nombre", m.nombre).put("cat", m.categoria.name)
                    .put("proto", m.protocolo?.name ?: "")
                    .put("ident", JSONObject(m.identificacion))
                    .put("confirmado", m.nombreConfirmado)
            )
        }
        archivo.parentFile?.mkdirs()
        archivo.writeText(arr.toString(1))
    }
}

/**
 * Descubrimiento de módulos: a cada ID candidato se le manda un pedido inofensivo
 * (TesterPresent y, si no contesta, lectura de identificación) escuchando cualquier
 * ID de respuesta. Cualquier contestación válida, positiva o negativa, delata un módulo.
 */
class Descubridor(private val diag: Diagnostico, private val log: FrameLog) {

    suspend fun buscar(
        completo: Boolean,
        conocidos: List<Modulo>,
        progreso: (hecho: Int, total: Int, encontrados: Int) -> Unit,
    ): List<Modulo> {
        val candidatos = if (completo) {
            (CatalogoModulos.candidatosRapidos() + CatalogoModulos.candidatosCompletos()).distinct()
        } else {
            (conocidos.map { it.idPedido } + CatalogoModulos.candidatosRapidos()).distinct()
        }
        log.nota("Descubrimiento ${if (completo) "completo" else "rápido"}: ${candidatos.size} direcciones")
        val encontrados = ArrayList<Modulo>()
        val idsDeRespuesta = HashSet<Int>()
        candidatos.forEachIndexed { i, id ->
            currentCoroutineContext().ensureActive()
            progreso(i, candidatos.size, encontrados.size)
            if (id in idsDeRespuesta) return@forEachIndexed // ahí transmite un módulo: no le escribimos encima
            val espera = if (completo && id !in CatalogoModulos.candidatosRapidos()) 80 else 150
            val idResp = sondear(id, espera) ?: return@forEachIndexed
            idsDeRespuesta += idResp
            log.nota("Módulo encontrado: %03X responde en %03X".format(id, idResp))
            val previo = conocidos.firstOrNull { it.idPedido == id && it.idRespuesta == idResp }
            val base = previo ?: CatalogoModulos.nuevo(id, idResp)
            val ident = diag.identificar(id, idResp)
            encontrados += base.copy(
                protocolo = ident.protocolo ?: base.protocolo,
                identificacion = ident.datos.ifEmpty { base.identificacion },
            )
        }
        progreso(candidatos.size, candidatos.size, encontrados.size)
        return encontrados
    }

    private suspend fun sondear(id: Int, esperaMs: Int): Int? {
        for (pedido in listOf(byteArrayOf(0x3E, 0x00), byteArrayOf(0x1A, 0x87.toByte()))) {
            when (val r = diag.fisico(id, null, pedido, esperaMs)) {
                is DiagResultado.Positiva -> return r.idRespuesta
                is DiagResultado.Negativa -> return r.idRespuesta
                else -> Unit
            }
        }
        return null
    }
}
