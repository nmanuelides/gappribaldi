package ar.garibaldi.obd.scan

import ar.garibaldi.obd.auto.Auto
import ar.garibaldi.obd.dtc.Dtc
import ar.garibaldi.obd.dtc.DtcRepositorio
import ar.garibaldi.obd.dtc.EstadoDtc
import ar.garibaldi.obd.dtc.FuenteInfo
import ar.garibaldi.obd.dtc.OrigenDtc
import ar.garibaldi.obd.modules.Categoria
import ar.garibaldi.obd.modules.Modulo
import ar.garibaldi.obd.obd.Obd2
import ar.garibaldi.obd.protocol.Diagnostico
import ar.garibaldi.obd.protocol.Protocolo
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import ar.garibaldi.obd.i18n.L
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Resultado de leer un módulo. [clave] identifica al módulo entre escaneos ("obd-7E8", "mod-612"). */
class ResultadoModulo(
    val clave: String,
    val nombre: String,
    val categoria: Categoria,
    val detalle: String,
    val dtcs: List<Dtc>,
    val error: String?,
) {
    val esObd: Boolean get() = clave.startsWith("obd-")
    val idPedido: Int? get() = if (esObd) null else clave.removePrefix("mod-").toIntOrNull(16)
}

class ResultadoEscaneo(
    val fecha: Long,
    val vin: String?,
    val voltaje: String?,
    val testigoMotor: Boolean?,
    val monitoresPendientes: List<String>,
    val modulos: List<ResultadoModulo>,
) {
    val totalCodigos: Int get() = modulos.sumOf { it.dtcs.size }

    fun clavesDeCodigos(): Set<String> = modulos.flatMap { m -> m.dtcs.map { m.clave + "|" + it.codigo } }.toSet()
}

/** Lee todo: OBD estándar y cada módulo conocido del auto. Si uno no responde, sigue con el siguiente. */
class Escaner(private val diag: Diagnostico, private val obd: Obd2) {

    suspend fun escanear(
        modulos: List<Modulo>,
        voltaje: String?,
        progreso: (hecho: Int, total: Int, texto: String) -> Unit,
        protocoloDetectado: (Modulo, Protocolo) -> Unit,
    ): ResultadoEscaneo {
        val total = modulos.size + 1
        progreso(0, total, "Leyendo datos de emisiones (OBD estándar)…")
        val vin = obd.vin()
        val estados = obd.estado()
        val resultados = ArrayList<ResultadoModulo>()
        val porEcu = obd.leerDtc()
        val ecus = (estados.map { it.idEcu } + porEcu.keys).distinct().sorted()
        for (id in ecus) {
            val (nombre, cat) = when (id) {
                0x7E8 -> "Motor — OBD estándar" to Categoria.MOTOR
                0x7E9 -> "Caja — OBD estándar" to Categoria.CAJA
                else -> "Módulo de emisiones %03X".format(id) to Categoria.OTRO
            }
            resultados += ResultadoModulo(
                "obd-%03X".format(id), nombre, cat,
                "Códigos de emisiones que exige la norma OBD-II", porEcu[id].orEmpty(), null,
            )
        }
        modulos.forEachIndexed { i, m ->
            currentCoroutineContext().ensureActive()
            progreso(i + 1, total, "Leyendo ${m.nombre}…")
            val lectura = diag.leerDtc(m.idPedido, m.idRespuesta, m.protocolo, m.trenMotriz)
            if (lectura.protocolo != null && lectura.protocolo != m.protocolo) protocoloDetectado(m, lectura.protocolo)
            resultados += ResultadoModulo(
                "mod-%03X".format(m.idPedido), m.nombre, m.categoria,
                "Dirección ${m.direccion} · ${(lectura.protocolo ?: m.protocolo)?.name ?: "protocolo sin detectar"}",
                lectura.dtcs, lectura.error,
            )
        }
        progreso(total, total, "Listo")
        return ResultadoEscaneo(
            fecha = System.currentTimeMillis(),
            vin = vin,
            voltaje = voltaje,
            testigoMotor = estados.takeIf { it.isNotEmpty() }?.any { it.testigoEncendido },
            monitoresPendientes = estados.flatMap { e -> e.monitores.filter { !it.listo }.map { it.nombre } }.distinct(),
            modulos = resultados,
        )
    }
}

/** Historial de escaneos guardado en el teléfono (un JSON por escaneo). */
class Historial(private val carpeta: File) {

    fun guardar(r: ResultadoEscaneo) {
        carpeta.mkdirs()
        File(carpeta, "${r.fecha}.json").writeText(aJson(r).toString())
        carpeta.listFiles()?.sortedByDescending { it.name }?.drop(60)?.forEach { it.delete() }
    }

    fun listar(): List<ResultadoEscaneo> =
        carpeta.listFiles { f -> f.name.endsWith(".json") }.orEmpty()
            .sortedByDescending { it.name }
            .mapNotNull { runCatching { deJson(JSONObject(it.readText())) }.getOrNull() }

    fun borrar(fecha: Long) {
        File(carpeta, "$fecha.json").delete()
    }

    companion object {
        fun aJson(r: ResultadoEscaneo): JSONObject = JSONObject()
            .put("fecha", r.fecha).put("vin", r.vin ?: "").put("voltaje", r.voltaje ?: "")
            .put("mil", r.testigoMotor?.toString() ?: "")
            .put("monitores", JSONArray(r.monitoresPendientes))
            .put("modulos", JSONArray(r.modulos.map { m ->
                JSONObject().put("clave", m.clave).put("nombre", m.nombre).put("cat", m.categoria.name)
                    .put("detalle", m.detalle).put("error", m.error ?: "")
                    .put("dtcs", JSONArray(m.dtcs.map { d ->
                        JSONObject().put("c", d.codigo).put("x", d.crudoHex).put("s", d.formatoSae)
                            .put("g", d.claveGenerica ?: "").put("f", d.tipoFalla ?: -1).put("o", d.origen.name)
                            .put("ea", d.estado.actual).put("es", d.estado.almacenado)
                            .put("ep", d.estado.pendiente).put("el", d.estado.luzTestigo)
                            .put("eb", d.estado.byteCrudo ?: -1)
                    }))
            }))

        fun deJson(o: JSONObject): ResultadoEscaneo {
            val mods = o.getJSONArray("modulos")
            return ResultadoEscaneo(
                fecha = o.getLong("fecha"),
                vin = o.optString("vin").ifEmpty { null },
                voltaje = o.optString("voltaje").ifEmpty { null },
                testigoMotor = o.optString("mil").toBooleanStrictOrNull(),
                monitoresPendientes = o.optJSONArray("monitores")?.let { a -> (0 until a.length()).map { a.getString(it) } }.orEmpty(),
                modulos = (0 until mods.length()).map { i ->
                    val m = mods.getJSONObject(i)
                    val ds = m.getJSONArray("dtcs")
                    ResultadoModulo(
                        clave = m.getString("clave"), nombre = m.getString("nombre"),
                        categoria = runCatching { Categoria.valueOf(m.getString("cat")) }.getOrDefault(Categoria.DESCONOCIDO),
                        detalle = m.optString("detalle"),
                        error = m.optString("error").ifEmpty { null },
                        dtcs = (0 until ds.length()).map { j ->
                            val d = ds.getJSONObject(j)
                            Dtc(
                                codigo = d.getString("c"), crudoHex = d.getString("x"), formatoSae = d.getString("s"),
                                claveGenerica = d.optString("g").ifEmpty { null },
                                tipoFalla = d.optInt("f", -1).takeIf { it >= 0 },
                                estado = EstadoDtc(
                                    d.optBoolean("ea"), d.optBoolean("es"), d.optBoolean("ep"), d.optBoolean("el"),
                                    d.optInt("eb", -1).takeIf { it >= 0 },
                                ),
                                origen = runCatching { OrigenDtc.valueOf(d.getString("o")) }.getOrDefault(OrigenDtc.KWP),
                            )
                        },
                    )
                },
            )
        }
    }
}

/** Informe en texto plano para mandarle al mecánico o pegar en un chat. */
object Informe {
    fun texto(r: ResultadoEscaneo, repo: DtcRepositorio, titulos: Map<String, String> = emptyMap()): String {
        val sb = StringBuilder()
        val fecha = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.US).format(Date(r.fecha))
        sb.appendLine("Diagnóstico ${Auto.descripcion} — $fecha")
        r.vin?.let { sb.appendLine("Chasis (VIN): $it") }
        r.voltaje?.let { sb.appendLine("Batería: $it") }
        r.testigoMotor?.let { sb.appendLine("Testigo de motor: ${if (it) "ENCENDIDO" else "apagado"}") }
        sb.appendLine("Códigos encontrados: ${r.totalCodigos}")
        for (m in r.modulos) {
            sb.appendLine()
            sb.appendLine("■ ${m.nombre}")
            sb.appendLine("  ${m.detalle}")
            if (m.error != null) sb.appendLine("  No se pudo leer: ${m.error}")
            if (m.dtcs.isEmpty() && m.error == null) sb.appendLine("  Sin códigos")
            for (d in m.dtcs) {
                val info = repo.buscar(d, m.categoria, m.nombre)
                val titulo = when {
                    info.fuente == FuenteInfo.NINGUNA -> "código propio de ${Auto.marcaCorta}, sin descripción local"
                    else -> titulos[d.codigo] ?: info.titulo
                }
                sb.appendLine("  ${d.codigo} — $titulo")
                sb.appendLine("     Estado: ${d.estado.texto()} · Gravedad${if (info.gravedadEstimada) " estimada" else ""}: ${info.gravedad.titulo}")
            }
        }
        if (r.monitoresPendientes.isNotEmpty()) {
            sb.appendLine()
            sb.appendLine("Monitores de emisiones sin completar: ${r.monitoresPendientes.joinToString()}")
        }
        sb.appendLine()
        sb.appendLine("Generado con Gappribaldi. Es una ayuda orientativa, no reemplaza el diagnóstico de un mecánico.")
        // En inglés se traducen las frases fijas del informe (en español queda igual)
        return L(sb.toString())
    }
}
