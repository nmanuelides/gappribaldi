package ar.garibaldi.obd.obd

import ar.garibaldi.obd.dtc.Dtc
import ar.garibaldi.obd.dtc.DtcDecoder
import ar.garibaldi.obd.dtc.OrigenDtc
import ar.garibaldi.obd.protocol.Diagnostico
import ar.garibaldi.obd.protocol.u

/** Un dato en vivo del modo 01. [calc] recibe los bytes de datos (A, B, ...). */
class Pid(
    val pid: Int,
    val nombre: String,
    val unidad: String,
    val bytes: Int,
    val ayuda: String? = null,
    val calc: (IntArray) -> Double,
)

object Pids {
    private const val AYUDA_AJUSTE =
        "Cuánto corrige la computadora la nafta. Normal: entre -10 % y +10 %. Muy positivo = mezcla pobre " +
            "(entra aire de más o falta nafta); muy negativo = mezcla rica."

    val lista = listOf(
        Pid(0x0C, "Vueltas del motor", "rpm", 2) { (it[0] * 256 + it[1]) / 4.0 },
        Pid(0x05, "Temperatura del refrigerante", "°C", 1, "En caliente, normal entre 85 y 105 °C.") { it[0] - 40.0 },
        Pid(0x04, "Carga del motor", "%", 1) { it[0] * 100 / 255.0 },
        Pid(0x06, "Ajuste corto de nafta, banco 1", "%", 1, AYUDA_AJUSTE) { (it[0] - 128) * 100 / 128.0 },
        Pid(0x07, "Ajuste largo de nafta, banco 1", "%", 1, AYUDA_AJUSTE) { (it[0] - 128) * 100 / 128.0 },
        Pid(0x08, "Ajuste corto de nafta, banco 2", "%", 1, AYUDA_AJUSTE) { (it[0] - 128) * 100 / 128.0 },
        Pid(0x09, "Ajuste largo de nafta, banco 2", "%", 1, AYUDA_AJUSTE) { (it[0] - 128) * 100 / 128.0 },
        Pid(0x10, "Caudal de aire (MAF)", "g/s", 2, "En ralentí caliente un V6 3.5 ronda 4 a 7 g/s.") { (it[0] * 256 + it[1]) / 100.0 },
        Pid(0x0B, "Presión en el múltiple", "kPa", 1) { it[0].toDouble() },
        Pid(0x0F, "Temperatura del aire de admisión", "°C", 1) { it[0] - 40.0 },
        Pid(0x11, "Mariposa", "%", 1) { it[0] * 100 / 255.0 },
        Pid(0x0E, "Avance de encendido", "°", 1) { it[0] / 2.0 - 64 },
        Pid(0x0D, "Velocidad", "km/h", 1) { it[0].toDouble() },
        Pid(0x42, "Tensión en la computadora", "V", 2, "Con motor en marcha, normal entre 13,5 y 14,7 V.") { (it[0] * 256 + it[1]) / 1000.0 },
        Pid(0x14, "Sonda lambda banco 1, sensor 1", "V", 2) { it[0] / 200.0 },
        Pid(0x15, "Sonda lambda banco 1, sensor 2", "V", 2) { it[0] / 200.0 },
        Pid(0x18, "Sonda lambda banco 2, sensor 1", "V", 2) { it[0] / 200.0 },
        Pid(0x19, "Sonda lambda banco 2, sensor 2", "V", 2) { it[0] / 200.0 },
        Pid(0x2F, "Nivel de nafta", "%", 1) { it[0] * 100 / 255.0 },
        Pid(0x33, "Presión atmosférica", "kPa", 1) { it[0].toDouble() },
        Pid(0x1F, "Tiempo desde el arranque", "s", 2) { it[0] * 256.0 + it[1] },
        Pid(0x21, "Distancia con el testigo encendido", "km", 2) { it[0] * 256.0 + it[1] },
        Pid(0x31, "Distancia desde el último borrado", "km", 2) { it[0] * 256.0 + it[1] },
        Pid(0x46, "Temperatura exterior", "°C", 1) { it[0] - 40.0 },
        Pid(0x5C, "Temperatura del aceite", "°C", 1) { it[0] - 40.0 },
        Pid(0xA6, "Odómetro", "km", 4) { ((it[0].toLong() shl 24) + (it[1] shl 16) + (it[2] shl 8) + it[3]) / 10.0 },
    )

    fun porId(pid: Int) = lista.firstOrNull { it.pid == pid }
}

class Monitor(val nombre: String, val listo: Boolean)

class EstadoEmisiones(val idEcu: Int, val testigoEncendido: Boolean, val cantidadCodigos: Int, val monitores: List<Monitor>)

class ValorCongelado(val nombre: String, val valor: String)

class CuadroCongelado(val codigo: String, val valores: List<ValorCongelado>)

/** OBD-II estándar (lo que por ley expone cualquier auto): modos 01, 02, 03, 04, 07, 09 y 0A. */
class Obd2(private val diag: Diagnostico) {

    /** Modo 01 PID 01: testigo de motor, cantidad de códigos y monitores de emisiones. */
    suspend fun estado(): List<EstadoEmisiones> =
        diag.obd(byteArrayOf(0x01, 0x01)).filter { it.datos.size >= 6 }.map { m ->
            val a = m.datos.u(2); val b = m.datos.u(3); val c = m.datos.u(4); val d = m.datos.u(5)
            val monitores = ArrayList<Monitor>()
            listOf("Fallas de encendido", "Sistema de combustible", "Componentes generales").forEachIndexed { i, n ->
                if (b and (1 shl i) != 0) monitores += Monitor(n, b and (1 shl (i + 4)) == 0)
            }
            if (b and 0x08 == 0) { // motor naftero
                listOf(
                    "Catalizador", "Catalizador calefaccionado", "Sistema evaporativo (vapores de nafta)",
                    "Aire secundario", "Refrigerante del A/C", "Sondas lambda", "Calefacción de sondas lambda", "EGR",
                ).forEachIndexed { i, n ->
                    if (c and (1 shl i) != 0) monitores += Monitor(n, d and (1 shl i) == 0)
                }
            }
            EstadoEmisiones(m.id, a and 0x80 != 0, a and 0x7F, monitores)
        }

    /** Códigos confirmados (03), pendientes (07) y permanentes (0A), agrupados por módulo que responde. */
    suspend fun leerDtc(): Map<Int, List<Dtc>> {
        val porEcu = LinkedHashMap<Int, MutableList<Dtc>>()
        val modos = listOf(
            0x03 to OrigenDtc.OBD_CONFIRMADO, 0x07 to OrigenDtc.OBD_PENDIENTE, 0x0A to OrigenDtc.OBD_PERMANENTE,
        )
        for ((modo, origen) in modos) {
            for (m in diag.obd(byteArrayOf(modo.toByte()), 400)) {
                val lista = porEcu.getOrPut(m.id) { ArrayList() }
                for (dtc in DtcDecoder.obd(m.datos, origen)) {
                    // Un confirmado suele aparecer también como pendiente: lo mostramos una sola vez
                    if (lista.none { it.codigo == dtc.codigo && it.origen == dtc.origen }) lista += dtc
                }
            }
        }
        return porEcu
    }

    /** Modo 04: borra códigos y datos de emisiones en todos los módulos OBD. */
    suspend fun borrar(): Boolean = diag.obd(byteArrayOf(0x04), 1000, borradoConfirmado = true).isNotEmpty()

    /** Modo 09 PID 02: número de chasis (VIN). */
    suspend fun vin(): String? {
        val m = diag.obd(byteArrayOf(0x09, 0x02), 500).firstOrNull() ?: return null
        val texto = String(m.datos.drop(3).toByteArray(), Charsets.ISO_8859_1).filter { it.isLetterOrDigit() }
        return texto.takeIf { it.length >= 11 }
    }

    suspend fun pidsSoportados(): Set<Int> {
        val soportados = HashSet<Int>()
        var base = 0
        while (base <= 0xA0) {
            val m = diag.obd(byteArrayOf(0x01, base.toByte())).firstOrNull { it.datos.size >= 6 } ?: break
            for (i in 0 until 32) {
                if (m.datos.u(2 + i / 8) and (0x80 shr (i % 8)) != 0) soportados += base + i + 1
            }
            if (base + 0x20 !in soportados) break
            base += 0x20
        }
        return soportados
    }

    suspend fun leerPid(pid: Pid): Double? {
        val m = diag.obd(byteArrayOf(0x01, pid.pid.toByte()), 150)
            .firstOrNull { it.datos.size >= 2 + pid.bytes && it.datos.u(1) == pid.pid } ?: return null
        return pid.calc(IntArray(pid.bytes) { m.datos.u(2 + it) })
    }

    /** Modo 02: las condiciones del motor en el momento en que se guardó la falla principal. */
    suspend fun cuadroCongelado(soportados: Set<Int>): CuadroCongelado? {
        val m = diag.obd(byteArrayOf(0x02, 0x02, 0x00)).firstOrNull { it.datos.size >= 5 } ?: return null
        val a = m.datos.u(3); val b = m.datos.u(4)
        if (a == 0 && b == 0) return null
        val valores = ArrayList<ValorCongelado>()
        for (pid in Pids.lista) {
            if (pid.pid !in soportados || pid.pid in setOf(0x1F, 0x21, 0x31, 0x2F, 0x33, 0x46, 0xA6)) continue
            val r = diag.obd(byteArrayOf(0x02, pid.pid.toByte(), 0x00), 150)
                .firstOrNull { it.datos.size >= 3 + pid.bytes && it.datos.u(1) == pid.pid } ?: continue
            val v = pid.calc(IntArray(pid.bytes) { r.datos.u(3 + it) })
            valores += ValorCongelado(pid.nombre, formatear(v) + " " + pid.unidad)
        }
        return CuadroCongelado(DtcDecoder.sae(a, b), valores)
    }

    companion object {
        fun formatear(v: Double): String =
            if (v == Math.rint(v) && Math.abs(v) < 1e6) v.toLong().toString() else "%.1f".format(java.util.Locale.US, v).let { if (ar.garibaldi.obd.i18n.Idioma.ingles) it else it.replace('.', ',') }
    }
}
