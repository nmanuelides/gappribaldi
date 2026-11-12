package ar.garibaldi.obd.dtc

import ar.garibaldi.obd.auto.Auto
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

    /** Código propio de la marca sin descripción local: no se inventa nada. */
    NINGUNA,

    /** Código propio de Mercedes descripto por una lista del DAS de un auto de la misma época. */
    MERCEDES,

    /** Código propio de la marca descripto por una lista abierta de códigos de esa marca (en inglés). */
    MARCA,
}

/**
 * Texto de un código según una lista del DAS de Mercedes. [modulo] es de qué lista sale y [origen] de qué auto;
 * [evento] marca los registros informativos (no son fallas); [foro] los que salen de foros del W204.
 */
class TextoMercedes(val texto: String, val modulo: String, val origen: String, val evento: Boolean, val foro: Boolean)

class Causa(val texto: String, val probabilidad: String?)

/** Todo lo que sabemos de un código, listo para mostrar. */
data class InfoDtc(
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
    /** Lo que dicen las listas del DAS de Mercedes (vacío en las otras marcas). */
    val mercedes: List<TextoMercedes> = emptyList(),
    /** Con [FuenteInfo.MARCA]: la marca hermana de cuya lista sale el texto (null si es de la propia marca). */
    val marcaHermana: String? = null,
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

    private val listasMercedes: JSONObject by lazy {
        try {
            abrir("mercedes.json.gz")?.use { JSONObject(GZIPInputStream(it).readBytes().toString(Charsets.UTF_8)) }
                ?.optJSONObject("familias") ?: JSONObject()
        } catch (_: Exception) {
            JSONObject()
        }
    }

    private val listasMarcas: JSONObject by lazy {
        try {
            abrir("marcas.json.gz")?.use { JSONObject(GZIPInputStream(it).readBytes().toString(Charsets.UTF_8)) }
                ?.optJSONObject("marcas") ?: JSONObject()
        } catch (_: Exception) {
            JSONObject()
        }
    }

    /**
     * Texto del código en la lista abierta de la marca del auto. Solo para códigos en formato SAE (motor, caja y
     * módulos UDS): el hex crudo de los módulos KWP de carrocería no se puede buscar así.
     */
    private fun textoMarca(dtc: Dtc): JSONObject? {
        if (!dtc.codigo.startsWith(dtc.formatoSae)) return null
        return listasMarcas.optJSONObject(Auto.marca)?.optJSONObject(dtc.formatoSae)
    }

    /**
     * Qué listas del DAS corresponden a un módulo. Se decide por la categoría y el nombre que tiene el módulo;
     * si no se sabe qué módulo es, se buscan en todas las de carrocería y confort.
     */
    private fun familiasDe(categoria: Categoria, modulo: String): List<String> {
        val n = modulo.lowercase()
        return when {
            categoria == Categoria.MOTOR -> listOf("motor")
            categoria == Categoria.CAJA -> listOf("caja")
            categoria == Categoria.AIRBAG -> listOf("airbag")
            categoria == Categoria.ARRANQUE && "elv" !in n && "traba" !in n -> listOf("ezs")
            "sam delantero" in n -> listOf("sam_del")
            "sam trasero" in n -> listOf("sam_tras")
            "columna" in n -> listOf("columna")
            "climatizador" in n -> listOf("clima")
            "comand" in n || "radio" in n -> listOf("comand")
            "techo" in n -> listOf("techo")
            "distronic" in n -> listOf("distronic")
            "tele aid" in n -> listOf("teleaid")
            categoria == Categoria.TABLERO -> listOf("tablero")
            categoria == Categoria.FRENOS && "neum" !in n -> listOf("esp")
            else -> FAMILIAS_CARROCERIA
        }
    }

    /** Textos del DAS para el código, solo en el Mercedes. */
    fun textosMercedes(dtc: Dtc, categoria: Categoria, modulo: String = ""): List<TextoMercedes> {
        if (!Auto.esMercedes) return emptyList()
        val clave = dtc.crudoHex.take(4).uppercase()
        return familiasDe(categoria, modulo).mapNotNull { f ->
            val fam = listasMercedes.optJSONObject(f) ?: return@mapNotNull null
            val c = fam.optJSONObject("codigos")?.optJSONObject(clave) ?: return@mapNotNull null
            TextoMercedes(c.getString("d"), fam.getString("nombre"), fam.getString("origen"), c.optInt("e") == 1, c.optInt("w") == 1)
        }
    }

    /** Ficha del código. [categoria] y [modulo] (su nombre) eligen las listas de Mercedes que corresponden. */
    fun buscar(dtc: Dtc, categoria: Categoria, modulo: String = ""): InfoDtc {
        val base = buscarBase(dtc, categoria)
        val mb = textosMercedes(dtc, categoria, modulo)
        if (mb.isEmpty()) {
            if (base.fuente != FuenteInfo.NINGUNA) return base
            val t = textoMarca(dtc) ?: return base
            val texto = t.getString("d")
            return base.copy(
                titulo = texto,
                fuente = FuenteInfo.MARCA,
                enIngles = true,
                ajenos = if (Idioma.ingles) emptyList() else listOf(texto),
                marcaHermana = t.optString("o").ifEmpty { null },
            )
        }
        if (base.fuente != FuenteInfo.NINGUNA) {
            // Código con descripción: el texto de Mercedes se agrega como dato extra
            return base.copy(mercedes = mb, ajenos = base.ajenos + if (Idioma.ingles) emptyList() else mb.map { it.texto })
        }
        val soloEventos = mb.all { it.evento }
        return base.copy(
            titulo = if (mb.size == 1) mb[0].texto else "Código propio de ${Auto.marcaCorta} · ${mb.size} significados posibles",
            gravedad = if (soloEventos) Gravedad.VERDE else base.gravedad,
            fuente = FuenteInfo.MERCEDES,
            enIngles = true,
            ajenos = if (Idioma.ingles) emptyList() else mb.map { it.texto },
            mercedes = mb,
        )
    }

    private fun buscarBase(dtc: Dtc, categoria: Categoria): InfoDtc {
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
            titulo = "Código propio de ${Auto.marcaCorta}",
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
        // Las notas "frecuente en este auto" están escritas para el M272 / 722.9 del C350
        notaMotor = if (Auto.esMercedes) c.optString("m").ifEmpty { null } else null,
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

        /** Listas que se revisan cuando no se sabe qué módulo es (no se incluyen motor ni caja). */
        private val FAMILIAS_CARROCERIA = listOf(
            "esp", "airbag", "ezs", "sam_del", "sam_tras", "tablero", "clima", "columna", "techo", "comand", "teleaid", "distronic",
        )

        private fun JSONArray?.textos(): List<String> =
            if (this == null) emptyList() else (0 until length()).map { getString(it) }
    }
}
