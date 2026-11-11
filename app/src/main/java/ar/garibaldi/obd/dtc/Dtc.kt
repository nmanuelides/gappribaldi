package ar.garibaldi.obd.dtc

/** De dónde salió el código. */
enum class OrigenDtc { OBD_CONFIRMADO, OBD_PENDIENTE, OBD_PERMANENTE, KWP, UDS }

/** Estado que informa el módulo, ya interpretado. */
data class EstadoDtc(
    val actual: Boolean,
    val almacenado: Boolean,
    val pendiente: Boolean,
    val luzTestigo: Boolean,
    val byteCrudo: Int? = null,
) {
    fun texto(): String {
        val partes = ArrayList<String>()
        if (actual) partes += "Presente ahora"
        if (almacenado && !actual) partes += "Guardado (no presente ahora)"
        if (almacenado && actual) partes += "guardado"
        if (pendiente) partes += "Pendiente (todavía no confirmado)"
        if (luzTestigo) partes += "enciende testigo"
        return if (partes.isEmpty()) "Sin estado informado" else partes.joinToString(" · ")
    }
}

/**
 * Un código de falla leído del auto.
 * [codigo] es lo que se muestra (p. ej. "P0171", o el hex crudo "9041" en módulos de carrocería).
 * [claveGenerica] es el código SAE con el que se puede buscar una descripción genérica,
 * o null si el código es propio de Mercedes y no hay que adivinar su significado.
 */
data class Dtc(
    val codigo: String,
    val crudoHex: String,
    val formatoSae: String,
    val claveGenerica: String?,
    val tipoFalla: Int?,
    val estado: EstadoDtc,
    val origen: OrigenDtc,
)

object DtcDecoder {
    private const val LETRAS = "PCBU"

    /** Dos bytes en formato SAE J2012 / ISO 15031-6 → "P0171". */
    fun sae(alto: Int, bajo: Int): String {
        val letra = LETRAS[(alto shr 6) and 3]
        val d1 = (alto shr 4) and 3
        return "%c%d%X%02X".format(letra, d1, alto and 0x0F, bajo)
    }

    /** ¿El código cae en un rango cuyo significado define la norma (igual en todas las marcas)? */
    fun esRangoGenerico(codigoSae: String): Boolean {
        if (codigoSae.length != 5) return false
        val d = codigoSae[1]
        return when (codigoSae[0]) {
            'P' -> d == '0' || d == '2' || (d == '3' && codigoSae[2] in '4'..'9')
            'B', 'C' -> d == '0'
            'U' -> d == '0' || d == '3'
            else -> false
        }
    }

    /** Respuesta a los modos OBD 03 / 07 / 0A sobre CAN: [SID+0x40][cantidad][2 bytes por código]. */
    fun obd(datos: ByteArray, origen: OrigenDtc): List<Dtc> {
        val salida = ArrayList<Dtc>()
        var i = 2
        while (i + 1 < datos.size) {
            val a = datos[i].toInt() and 0xFF
            val b = datos[i + 1].toInt() and 0xFF
            i += 2
            if (a == 0 && b == 0) continue
            val sae = sae(a, b)
            val estado = EstadoDtc(
                actual = false,
                almacenado = origen != OrigenDtc.OBD_PENDIENTE,
                pendiente = origen == OrigenDtc.OBD_PENDIENTE,
                luzTestigo = false,
            )
            salida += Dtc(sae, "%02X%02X".format(a, b), sae, sae.takeIf(::esRangoGenerico), null, estado, origen)
        }
        return salida
    }

    /**
     * KWP2000 servicio 0x18: 58 [cantidad] y por cada código [alto][bajo][estado].
     * Estado (ISO 14230-3): bit 7 testigo, bits 6-5 almacenamiento
     * (3 = presente ahora, 1 = guardado y no presente, 2 = intermitente), bit 4 prueba incompleta.
     * [tremMotriz]: en motor y caja los códigos siguen la numeración SAE (0171 = P0171);
     * en el resto de los módulos son números propios de Mercedes y se muestran en hex.
     */
    fun kwp(datos: ByteArray, trenMotriz: Boolean): List<Dtc> {
        val salida = ArrayList<Dtc>()
        var i = 2
        while (i + 2 < datos.size) {
            val a = datos[i].toInt() and 0xFF
            val b = datos[i + 1].toInt() and 0xFF
            val st = datos[i + 2].toInt() and 0xFF
            i += 3
            if (a == 0 && b == 0) continue
            val almacen = (st shr 5) and 3
            if (almacen == 0) continue // el módulo lo lista pero no detectó la falla
            val estado = EstadoDtc(
                actual = almacen == 3,
                almacenado = almacen == 1 || almacen == 3,
                pendiente = almacen == 2,
                luzTestigo = st and 0x80 != 0,
                byteCrudo = st,
            )
            val sae = sae(a, b)
            val hex = "%02X%02X".format(a, b)
            salida += Dtc(
                codigo = if (trenMotriz) sae else hex,
                crudoHex = hex,
                formatoSae = sae,
                claveGenerica = sae.takeIf { trenMotriz && esRangoGenerico(it) },
                tipoFalla = null,
                estado = estado,
                origen = OrigenDtc.KWP,
            )
        }
        return salida
    }

    /**
     * UDS servicio 0x19 02: 59 02 [máscara] y por cada código [alto][medio][tipo de falla][estado].
     * Estado (ISO 14229): bit 0 falla ahora, bit 2 pendiente, bit 3 confirmado, bit 7 testigo.
     */
    fun uds(datos: ByteArray): List<Dtc> {
        val salida = ArrayList<Dtc>()
        var i = 3
        while (i + 3 < datos.size) {
            val a = datos[i].toInt() and 0xFF
            val b = datos[i + 1].toInt() and 0xFF
            val ftb = datos[i + 2].toInt() and 0xFF
            val st = datos[i + 3].toInt() and 0xFF
            i += 4
            if (a == 0 && b == 0 && ftb == 0) continue
            if (st and 0x0D == 0) continue // ni presente, ni pendiente, ni confirmado
            val estado = EstadoDtc(
                actual = st and 0x01 != 0,
                almacenado = st and 0x08 != 0,
                pendiente = st and 0x04 != 0 && st and 0x08 == 0,
                luzTestigo = st and 0x80 != 0,
                byteCrudo = st,
            )
            val sae = sae(a, b)
            salida += Dtc(
                codigo = "%s%02X".format(sae, ftb),
                crudoHex = "%02X%02X%02X".format(a, b, ftb),
                formatoSae = sae,
                claveGenerica = sae.takeIf(::esRangoGenerico),
                tipoFalla = ftb,
                estado = estado,
                origen = OrigenDtc.UDS,
            )
        }
        return salida
    }
}
