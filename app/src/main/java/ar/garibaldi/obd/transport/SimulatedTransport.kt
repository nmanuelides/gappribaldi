package ar.garibaldi.obd.transport

import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.delay

/**
 * Adaptador y auto simulados ("modo demo"): emula un ELM327/STN conectado a un W204
 * inventado, con módulos KWP2000 y UDS y algunas fallas típicas. Sirve para probar
 * toda la app sin estar en el auto, y lo usan los tests.
 * Las direcciones y códigos de este simulador son de ejemplo, no datos reales del auto.
 */
class SimulatedTransport(private val demoraMs: Long = 12) : ObdTransport {

    private class Falla(val alto: Int, val bajo: Int, val tercero: Int, val estado: Int)

    private class Ecu(
        val pedido: Int,
        val respuesta: Int,
        val uds: Boolean,
        val obd: Boolean,
        val parte: String,
        val exigeSesion: Boolean = false,
        val fallas: MutableList<Falla> = ArrayList(),
    ) {
        var sesionExtendida = false
    }

    private val canal = Channel<String>(Channel.UNLIMITED)
    private var abierto = false
    private var cabecera = 0x7DF
    private var filtro: Int? = null // null = automático (7E8..7EF)
    private var filtroAbierto = false
    private var monitoresListos = true

    private val ecus = listOf(
        Ecu(0x7E0, 0x7E8, uds = false, obd = true, parte = "2729004400", fallas = mutableListOf(
            Falla(0x01, 0x71, 0, 0xE0), // P0171 presente, con testigo
            Falla(0x00, 0x16, 0, 0x20), // P0016 guardado
            Falla(0x20, 0x06, 0, 0x40), // P2006 intermitente / pendiente
        )),
        Ecu(0x7E1, 0x7E9, uds = false, obd = true, parte = "0034460310", fallas = mutableListOf(
            Falla(0x07, 0x17, 0, 0x20), // P0717 guardado
        )),
        Ecu(0x612, 0x482, uds = true, obd = false, parte = "2049052103"),
        Ecu(0x632, 0x486, uds = false, obd = false, parte = "2049014502", fallas = mutableListOf(
            Falla(0x51, 0x20, 0, 0x60),
        )),
        Ecu(0x642, 0x488, uds = false, obd = false, parte = "2049008602", fallas = mutableListOf(
            Falla(0x90, 0x41, 0, 0x20),
        )),
        Ecu(0x662, 0x48C, uds = true, obd = false, parte = "2049003107", fallas = mutableListOf(
            Falla(0xC1, 0x55, 0x00, 0x08), // U0155-00 confirmado
            Falla(0x9F, 0x4A, 0x13, 0x50), // solo "prueba incompleta": no debe mostrarse
        )),
        Ecu(0x6A2, 0x494, uds = false, obd = false, parte = "2049015204", exigeSesion = true),
    )

    override val nombre = "Auto de demostración"
    override val conectado: Boolean get() = abierto
    override val entrada: ReceiveChannel<String> get() = canal

    override suspend fun conectar() {
        delay(demoraMs * 20)
        abierto = true
    }

    override suspend fun escribir(texto: String) {
        for (cmd in texto.split('\r').map { it.trim() }.filter { it.isNotEmpty() }) {
            if (demoraMs > 0) delay(demoraMs)
            canal.trySend(responder(cmd.uppercase().replace(" ", "")) + "\r\r>")
        }
    }

    override fun cerrar() {
        abierto = false
        canal.close()
    }

    private fun responder(cmd: String): String = when {
        cmd == "ATZ" -> { cabecera = 0x7DF; filtro = null; filtroAbierto = false; "ELM327 v1.4b" }
        cmd == "ATI" -> "ELM327 v1.4b"
        cmd == "STI" -> "STN2255 v5.6.19"
        cmd == "STDI" -> "OBDLink MX+ r3.1 (simulado)"
        cmd == "ATRV" -> "12.4V"
        cmd.startsWith("ATSH") -> { cabecera = cmd.substring(4).toInt(16); "OK" }
        cmd.startsWith("ATCRA") -> {
            filtroAbierto = false
            filtro = cmd.substring(5).takeIf { it.isNotEmpty() }?.toInt(16)
            "OK"
        }
        cmd == "ATAR" -> { filtro = null; filtroAbierto = false; "OK" }
        cmd == "ATCM000" -> { filtroAbierto = true; "OK" }
        cmd.startsWith("AT") -> "OK"
        cmd.startsWith("ST") -> "?"
        cmd.all { it.isDigit() || it in 'A'..'F' } && cmd.length % 2 == 0 -> pedido(cmd)
        else -> "?"
    }

    private fun pedido(hex: String): String {
        val req = IntArray(hex.length / 2) { hex.substring(it * 2, it * 2 + 2).toInt(16) }
        val lineas = ArrayList<String>()
        for (ecu in ecus) {
            val funcional = cabecera == 0x7DF && ecu.obd
            if (!funcional && cabecera != ecu.pedido) continue
            val pasa = when {
                filtroAbierto -> true
                filtro != null -> filtro == ecu.respuesta
                else -> ecu.respuesta in 0x7E8..0x7EF
            }
            if (!pasa) continue
            val resp = atender(ecu, req, funcional) ?: continue
            lineas += tramas(ecu.respuesta, resp)
        }
        return if (lineas.isEmpty()) "NO DATA" else lineas.joinToString("\r")
    }

    private fun tramas(id: Int, datos: List<Int>): List<String> {
        fun linea(bytes: List<Int>) = "%03X".format(id) + (bytes + List(8 - bytes.size) { 0 }).joinToString("") { "%02X".format(it) }
        if (datos.size <= 7) return listOf(linea(listOf(datos.size) + datos))
        val salida = ArrayList<String>()
        salida += linea(listOf(0x10 or (datos.size shr 8), datos.size and 0xFF) + datos.take(6))
        var n = 1
        datos.drop(6).chunked(7).forEach { salida += linea(listOf(0x20 or (n++ and 0x0F)) + it) }
        return salida
    }

    private fun negativa(sid: Int, nrc: Int) = listOf(0x7F, sid, nrc)

    private fun atender(ecu: Ecu, req: IntArray, funcional: Boolean): List<Int>? {
        val sid = req[0]
        if (sid <= 0x0A) return if (ecu.obd) obd(ecu, req) else if (funcional) null else negativa(sid, 0x11)
        if (funcional) return null
        val ascii = ecu.parte.map { it.code }
        return when (sid) {
            0x3E -> listOf(0x7E, 0x00)
            0x10 -> { ecu.sesionExtendida = req.getOrNull(1) in listOf(0x92, 0x03); listOf(0x50, req.getOrElse(1) { 0 }) }
            0x1A -> if (ecu.uds) negativa(sid, 0x11) else listOf(0x5A, req.getOrElse(1) { 0x87 }, 0x01, 0x08, 0x03, 0x00) + ascii
            0x22 -> when {
                !ecu.uds -> negativa(sid, 0x11)
                req.getOrNull(2) == 0x11 -> listOf(0x62, 0xF1, 0x11) + ascii
                else -> negativa(sid, 0x31)
            }
            0x18 -> when {
                ecu.uds -> negativa(sid, 0x11)
                ecu.exigeSesion && !ecu.sesionExtendida -> negativa(sid, 0x80)
                else -> listOf(0x58, ecu.fallas.size) + ecu.fallas.flatMap { listOf(it.alto, it.bajo, it.estado) }
            }
            0x19 -> if (!ecu.uds) negativa(sid, 0x11)
            else listOf(0x59, 0x02, 0xFF) + ecu.fallas.flatMap { listOf(it.alto, it.bajo, it.tercero, it.estado) }
            0x14 -> { ecu.fallas.clear(); if (ecu.obd) monitoresListos = false; listOf(0x54) + req.drop(1).take(if (ecu.uds) 0 else 2) }
            else -> negativa(sid, 0x11)
        }
    }

    private val pidsMotor = setOf(
        0x01, 0x04, 0x05, 0x06, 0x07, 0x08, 0x09, 0x0B, 0x0C, 0x0D, 0x0E, 0x0F, 0x10, 0x11,
        0x14, 0x15, 0x18, 0x19, 0x1F, 0x20, 0x21, 0x2F, 0x31, 0x33, 0x40, 0x42,
    )

    private fun valorPid(pid: Int): List<Int>? = when (pid) {
        0x04 -> listOf(0x38)
        0x05 -> listOf(0x82) // 90 °C
        0x06 -> listOf(0x86); 0x07 -> listOf(0xA2) // ajuste largo banco 1 alto: coherente con P0171
        0x08 -> listOf(0x81); 0x09 -> listOf(0x84)
        0x0B -> listOf(0x21)
        0x0C -> { val rpm = (680 + (System.nanoTime() / 300_000_000 % 5) * 6).toInt() * 4; listOf(rpm shr 8, rpm and 0xFF) }
        0x0D -> listOf(0)
        0x0E -> listOf(0x92)
        0x0F -> listOf(0x41)
        0x10 -> listOf(0x02, 0x1C)
        0x11 -> listOf(0x24)
        0x14, 0x18 -> listOf(0x5A, 0x80)
        0x15, 0x19 -> listOf(0x8C, 0xFF)
        0x1F -> listOf(0x01, 0x2C)
        0x21 -> listOf(0x00, 0x57)
        0x2F -> listOf(0x99)
        0x31 -> listOf(0x0B, 0xB8)
        0x33 -> listOf(0x65)
        0x42 -> listOf(0x36, 0x4C)
        else -> null
    }

    private fun confirmadas(ecu: Ecu) = ecu.fallas.filter { (it.estado shr 5) and 1 == 1 }

    private fun obd(ecu: Ecu, req: IntArray): List<Int>? {
        val motor = ecu.pedido == 0x7E0
        return when (req[0]) {
            0x01 -> {
                val pid = req.getOrNull(1) ?: return null
                when {
                    pid == 0x01 -> {
                        val n = confirmadas(ecu).size
                        val mil = if (ecu.fallas.any { it.estado and 0x80 != 0 }) 0x80 else 0
                        if (motor) listOf(0x41, 0x01, mil or n, 0x07, 0x65, if (monitoresListos) 0x04 else 0x65)
                        else listOf(0x41, 0x01, n, 0x00, 0x00, 0x00)
                    }
                    !motor -> null
                    pid % 0x20 == 0 -> {
                        val bytes = IntArray(4)
                        for (i in 0 until 32) if (pid + i + 1 in pidsMotor) bytes[i / 8] = bytes[i / 8] or (0x80 shr (i % 8))
                        listOf(0x41, pid) + bytes.toList()
                    }
                    else -> valorPid(pid)?.let { listOf(0x41, pid) + it }
                }
            }
            0x02 -> {
                if (!motor || ecu.fallas.isEmpty()) return null
                val pid = req.getOrNull(1) ?: return null
                if (pid == 0x02) listOf(0x42, 0x02, 0x00, 0x01, 0x71)
                else valorPid(pid)?.let { listOf(0x42, pid, 0x00) + it }
            }
            0x03 -> confirmadas(ecu).let { f -> listOf(0x43, f.size) + f.flatMap { listOf(it.alto, it.bajo) } }
            0x07 -> ecu.fallas.filter { (it.estado shr 5) and 3 == 2 }.let { f -> listOf(0x47, f.size) + f.flatMap { listOf(it.alto, it.bajo) } }
            0x0A -> listOf(0x4A, 0x00)
            0x04 -> { ecu.fallas.clear(); monitoresListos = false; listOf(0x44) }
            0x09 -> if (motor && req.getOrNull(1) == 0x02) listOf(0x49, 0x02, 0x01) + "WDDGF56X07F123456".map { it.code } else null
            else -> null
        }
    }
}
