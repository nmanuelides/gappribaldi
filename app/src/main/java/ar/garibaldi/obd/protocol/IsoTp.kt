package ar.garibaldi.obd.protocol

/** Una trama CAN tal como la entrega el adaptador (con cabeceras activadas). */
class CanFrame(val id: Int, val datos: ByteArray) {
    override fun toString() = "%03X %s".format(id, datos.hex())
}

/** Mensaje de diagnóstico ya rearmado. [completo] es false si faltaron tramas. */
class MensajeIsoTp(val id: Int, val datos: ByteArray, val completo: Boolean)

fun ByteArray.hex(): String = joinToString("") { "%02X".format(it) }

fun ByteArray.u(i: Int): Int = this[i].toInt() and 0xFF

fun hexABytes(hex: String): ByteArray {
    val limpio = hex.filter { !it.isWhitespace() }
    require(limpio.length % 2 == 0) { "Hex de largo impar: $hex" }
    return ByteArray(limpio.length / 2) { limpio.substring(it * 2, it * 2 + 2).toInt(16).toByte() }
}

/**
 * ISO 15765-2 (ISO-TP) sobre CAN de 11 bits: interpreta las líneas del adaptador
 * y junta los mensajes de varias tramas.
 */
object IsoTp {
    private val soloHex = Regex("^[0-9A-Fa-f]+$")

    /** Interpreta una línea tipo "7E8 06 41 00 BE 3E A8 13" o "7E8064100BE3EA813". */
    fun parsearLinea(linea: String): CanFrame? {
        val l = linea.filter { !it.isWhitespace() }
        if (l.length < 5 || l.length > 19 || l.length % 2 == 0 || !soloHex.matches(l)) return null
        return CanFrame(l.substring(0, 3).toInt(16), hexABytes(l.substring(3)))
    }

    /** Rearma los mensajes de cada emisor, respetando el orden de llegada. */
    fun reensamblar(tramas: List<CanFrame>): List<MensajeIsoTp> {
        class Parcial(val largo: Int) {
            val buf = ArrayList<Byte>()
            var proximo = 1
        }

        val salida = ArrayList<MensajeIsoTp>()
        val abiertos = LinkedHashMap<Int, Parcial>()
        fun cerrar(id: Int, p: Parcial, completo: Boolean) {
            salida += MensajeIsoTp(id, p.buf.take(p.largo).toByteArray(), completo)
            abiertos.remove(id)
        }
        for (t in tramas) {
            val d = t.datos
            if (d.isEmpty()) continue
            val pci = d.u(0)
            when (pci shr 4) {
                0 -> { // trama única
                    val largo = pci and 0x0F
                    if (largo == 0 || largo > d.size - 1) continue
                    salida += MensajeIsoTp(t.id, d.copyOfRange(1, 1 + largo), true)
                }
                1 -> { // primera trama
                    if (d.size < 3) continue
                    abiertos[t.id]?.let { cerrar(t.id, it, false) }
                    val p = Parcial(((pci and 0x0F) shl 8) or d.u(1))
                    for (i in 2 until d.size) p.buf += d[i]
                    abiertos[t.id] = p
                    if (p.buf.size >= p.largo) cerrar(t.id, p, true)
                }
                2 -> { // trama consecutiva
                    val p = abiertos[t.id] ?: continue
                    if ((pci and 0x0F) != (p.proximo and 0x0F)) {
                        cerrar(t.id, p, false) // se perdió una trama
                        continue
                    }
                    p.proximo++
                    for (i in 1 until d.size) p.buf += d[i]
                    if (p.buf.size >= p.largo) cerrar(t.id, p, true)
                }
                else -> Unit // control de flujo u otra cosa: no es dato
            }
        }
        abiertos.toList().forEach { (id, p) -> cerrar(id, p, false) }
        return salida
    }
}
