package ar.garibaldi.obd.dtc

import ar.garibaldi.obd.modules.Categoria
import ar.garibaldi.obd.i18n.Idioma
import org.json.JSONArray
import org.json.JSONObject
import java.io.InputStream
import java.util.zip.GZIPInputStream

enum class Gravedad(val titulo: String, val consejo: String) {
    VERDE("Puede esperar", "Podés seguir usando el auto. Hacelo ver cuando tengas oportunidad."),
    AMARILLO("Revisar pronto", "Podés manejar con cuidado, pero hacelo revisar en los próximos días."),
    ROJO("No conviene manejar", "Riesgo de daño o de seguridad. Evitá usar el auto hasta revisarlo."),
}

enum class FuenteInfo {
    /** Ficha escrita a mano en español para esta app. */
    CURADA,

    /** Base abierta OBDex (en inglés; se puede traducir en el teléfono). */
    OBDEX,

    /** Código propio de Mercedes sin descripción local: no se inventa nada. */
    NINGUNA,
}

class Causa(val texto: String, val probabilidad: String?)

/** Todo lo que sabemos de un código, listo para mostrar. */
class InfoDtc(
    val titulo: String,
    val explicacion: String?,
    val causas: List<Causa>,
    val sintomas: List<String>,
    val queRevisar: List<String>,
    val gravedad: Gravedad,
    val gravedadEstimada: Boolean,
    val notaMotor: String?,
    val relacionados: List<String>,
    val fuente: FuenteInfo,
    /** Los textos vienen de OBDex (en inglés). */
    val enIngles: Boolean,
    /** Textos que están en un idioma distinto al de la interfaz y conviene traducir en el teléfono. */
    val ajenos: List<String> = emptyList(),
)

/**
 * Base local de códigos:
 *  1. Fichas curadas en español (assets/dtc/curados_es.json), con notas del motor M272.
 *  2. OBDex: los 9.533 códigos genéricos SAE J2012 con causas y síntomas (CC0, en inglés).
 * [abrir] devuelve el archivo pedido dentro de la carpeta dtc/ o null si no existe.
 */
class DtcRepositorio(private val abrir: (String) -> InputStream?) {
    private val curados: JSONObject by lazy {
        try {
            abrir("curados_es.json")?.use { JSONObject(it.readBytes().toString(Charsets.UTF_8)) } ?: JSONObject()
        } catch (_: Exception) {
            JSONObject()
        }
    }
    private val familias = HashMap<String, JSONObject>()

    @Synchronized
    private fun familia(clave: String): JSONObject = familias.getOrPut(clave) {
        try {
            abrir("$clave.json.gz")?.use { JSONObject(GZIPInputStream(it).readBytes().toString(Charsets.UTF_8)) }
                ?: JSONObject()
        } catch (_: Exception) {
            JSONObject()
        }
    }

    fun codigosCurados(): List<String> = curados.keys().asSequence().toList()

    /** Ficha del código. [categoria] ajusta la gravedad estimada cuando no hay ficha curada. */
    fun buscar(dtc: Dtc, categoria: Categoria): InfoDtc {
        val clave = dtc.claveGenerica
        if (clave != null) {
            val generica = familia(clave.substring(0, 3)).optJSONObject(clave)
            curados.optJSONObject(clave)?.let { c ->
                // En inglés, los textos generales salen de OBDex y la ficha curada aporta gravedad, notas y qué revisar
                return if (Idioma.ingles && generica != null) deCuradaEnIngles(c, clave, generica, categoria) else deCurada(c, generica)
            }
            if (generica != null) return deObdex(clave, generica, categoria)
        }
        return InfoDtc(
            titulo = "Código propio de Mercedes",
            explicacion = null,
            causas = emptyList(), sintomas = emptyList(), queRevisar = emptyList(),
            gravedad = if (categoria.seguridad) Gravedad.AMARILLO else Gravedad.VERDE,
            gravedadEstimada = true,
            notaMotor = null, relacionados = emptyList(),
            fuente = FuenteInfo.NINGUNA, enIngles = false,
        )
    }

    private fun deCurada(c: JSONObject, generica: JSONObject?) = InfoDtc(
        titulo = c.getString("t"),
        explicacion = c.optString("e").ifEmpty { null },
        causas = c.optJSONArray("c").textos().map { Causa(it, null) },
        sintomas = c.optJSONArray("s").textos(),
        queRevisar = c.optJSONArray("r").textos(),
        gravedad = when (c.optString("g")) {
            "rojo" -> Gravedad.ROJO
            "verde" -> Gravedad.VERDE
            else -> Gravedad.AMARILLO
        },
        gravedadEstimada = false,
        notaMotor = c.optString("m").ifEmpty { null },
        relacionados = generica?.optJSONArray("r").textos(),
        fuente = FuenteInfo.CURADA,
        enIngles = false,
    )

    private fun deCuradaEnIngles(c: JSONObject, codigo: String, g: JSONObject, categoria: Categoria): InfoDtc {
        val base = deObdex(codigo, g, categoria)
        val curada = deCurada(c, g)
        return InfoDtc(
            titulo = base.titulo, explicacion = base.explicacion, causas = base.causas, sintomas = base.sintomas,
            queRevisar = curada.queRevisar, gravedad = curada.gravedad, gravedadEstimada = false,
            notaMotor = curada.notaMotor, relacionados = base.relacionados, fuente = FuenteInfo.CURADA, enIngles = true,
            ajenos = curada.queRevisar + listOfNotNull(curada.notaMotor),
        )
    }

    private fun deObdex(codigo: String, g: JSONObject, categoria: Categoria): InfoDtc {
        val banderas = g.optInt("f")
        val causas = ArrayList<Causa>()
        g.optJSONArray("c")?.let { arr ->
            for (i in 0 until arr.length()) {
                val par = arr.getJSONArray(i)
                val prob = when (par.getString(0)) { "h" -> "alta"; "l" -> "baja"; else -> "media" }
                causas += Causa(par.getString(1), prob)
            }
        }
        return InfoDtc(
            titulo = g.getString("t"),
            explicacion = g.optString("d").ifEmpty { null },
            causas = causas,
            sintomas = g.optJSONArray("s").textos(),
            queRevisar = emptyList(),
            gravedad = estimarGravedad(codigo, banderas, categoria),
            gravedadEstimada = true,
            notaMotor = null,
            relacionados = g.optJSONArray("r").textos(),
            fuente = FuenteInfo.OBDEX,
            enIngles = true,
            ajenos = if (Idioma.ingles) emptyList()
            else listOf(g.getString("t")) + listOfNotNull(g.optString("d").ifEmpty { null }) + causas.map { it.texto } +
                g.optJSONArray("s").textos(),
        )
    }

    companion object {
        /**
         * Sin ficha curada, la gravedad se estima con reglas simples y siempre se
         * muestra como "estimada".
         */
        fun estimarGravedad(codigo: String, banderas: Int, categoria: Categoria): Gravedad = when {
            codigo.startsWith("P030") -> Gravedad.ROJO // fallas de encendido: pueden arruinar el catalizador
            categoria.seguridad || codigo[0] == 'C' || codigo.startsWith("B00") -> Gravedad.AMARILLO
            banderas and 4 != 0 -> Gravedad.AMARILLO // puede entrar en modo emergencia
            banderas and 1 != 0 -> Gravedad.AMARILLO // enciende el testigo de motor
            else -> Gravedad.VERDE
        }

        private fun JSONArray?.textos(): List<String> =
            if (this == null) emptyList() else (0 until length()).map { getString(it) }
    }
}
