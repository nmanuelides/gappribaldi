package ar.garibaldi.obd.adapter

import ar.garibaldi.obd.log.FrameLog
import ar.garibaldi.obd.protocol.CanFrame
import ar.garibaldi.obd.protocol.IsoTp
import ar.garibaldi.obd.transport.ObdTransport
import kotlinx.coroutines.channels.ClosedReceiveChannelException
import kotlinx.coroutines.withTimeoutOrNull
import java.io.IOException

class AdaptadorException(mensaje: String) : IOException(mensaje)

/** Lo que devolvió el adaptador ante un pedido al auto. */
class RespuestaCan(val tramas: List<CanFrame>, val sinDatos: Boolean, val error: String?)

class InfoAdaptador(
    val elm: String,
    val stn: String?,
    val dispositivo: String?,
    val voltaje: String?,
    val busOk: Boolean,
    val detalleBus: String,
)

/**
 * Capa ELM327/STN. No es segura para uso concurrente: quien la usa (el ViewModel)
 * serializa las operaciones con un único candado.
 */
class Elm327(private val transporte: ObdTransport, private val log: FrameLog) {
    private var cabecera = -1
    private var filtro = FILTRO_DESCONOCIDO
    private var flujoManual: Boolean? = null
    private var timeoutActual = -1
    private var flujoCabecera = -1

    /** Manda un comando y devuelve las líneas de respuesta (sin eco ni prompt). */
    suspend fun crudo(comando: String, timeoutMs: Long = 4000): List<String> {
        // Tiramos cualquier resto de una respuesta anterior
        while (transporte.entrada.tryReceive().getOrNull() != null) Unit
        log.tx(comando)
        try {
            transporte.escribir(comando + "\r")
        } catch (e: IOException) {
            throw AdaptadorException("Se cortó la conexión con el adaptador (${e.message})")
        }
        val sb = StringBuilder()
        val completo = withTimeoutOrNull(timeoutMs) {
            try {
                while (!sb.contains('>')) sb.append(transporte.entrada.receive())
            } catch (_: ClosedReceiveChannelException) {
                throw AdaptadorException("Se cortó la conexión con el adaptador")
            }
            true
        }
        if (completo == null) {
            log.nota("Sin respuesta a '$comando' en $timeoutMs ms. Recibido: ${sb.toString().trim()}")
            throw AdaptadorException("El adaptador no respondió a '$comando'")
        }
        val lineas = sb.substring(0, sb.indexOf('>'))
            .split('\r', '\n')
            .map { it.trim().trim('\u0000') }
            .filter { it.isNotEmpty() && !it.equals(comando, ignoreCase = true) }
        lineas.forEach { log.rx(it) }
        return lineas
    }

    private suspend fun at(comando: String): Boolean {
        val r = crudo(comando)
        return r.any { it.contains("OK", ignoreCase = true) }
    }

    /** Reinicia y configura el adaptador para CAN de 11 bits a 500 kbps (ISO 15765-4). */
    suspend fun inicializar(): InfoAdaptador {
        cabecera = -1; filtro = FILTRO_DESCONOCIDO; flujoManual = null; timeoutActual = -1; flujoCabecera = -1
        try {
            crudo("ATZ", 7000)
        } catch (_: AdaptadorException) {
            crudo("ATZ", 7000) // a veces el primer reset llega cortado
        }
        at("ATE0"); at("ATL0"); at("ATS0"); at("ATH1"); at("ATCAF1")
        at("ATAT0") // sin tiempos adaptativos: usamos esperas fijas y predecibles
        if (!at("ATSP6")) log.nota("El adaptador no aceptó ATSP6")
        val elm = crudo("ATI").firstOrNull() ?: "?"
        val stn = crudo("STI").firstOrNull()?.takeUnless { it.contains('?') }
        val disp = if (stn != null) crudo("STDI").firstOrNull()?.takeUnless { it.contains('?') } else null
        val voltaje = leerVoltaje()
        // Probamos el bus con el pedido OBD más básico
        val prueba = enviarFuncionalObd("0100", 400)
        val busOk = prueba.tramas.isNotEmpty()
        val detalle = when {
            busOk -> "El auto responde (${prueba.tramas.map { "%03X".format(it.id) }.distinct().joinToString()})"
            prueba.error != null -> "Sin comunicación con el auto: ${prueba.error}. ¿Está el contacto puesto?"
            else -> "El auto no respondió. Revisá que el contacto esté en posición 2."
        }
        return InfoAdaptador(elm, stn, disp, voltaje, busOk, detalle)
    }

    suspend fun leerVoltaje(): String? =
        crudo("ATRV").firstOrNull()?.takeIf { it.any(Char::isDigit) }

    /** Espera máxima de respuesta del auto, en milisegundos (se redondea a pasos de 4 ms). */
    suspend fun timeout(ms: Int) {
        val pasos = (ms / 4).coerceIn(1, 255)
        if (pasos == timeoutActual) return
        at("ATST%02X".format(pasos))
        timeoutActual = pasos
    }

    /** Pedido OBD estándar a todos los módulos de emisiones (7DF → 7E8..7EF). */
    suspend fun enviarFuncionalObd(hex: String, esperaMs: Int = 300): RespuestaCan {
        if (flujoManual != false) { at("ATFCSM0"); flujoManual = false }
        if (cabecera != 0x7DF) { at("ATSH7DF"); cabecera = 0x7DF }
        if (filtro != FILTRO_AUTO) { at("ATAR"); filtro = FILTRO_AUTO }
        timeout(esperaMs)
        return enviar(hex, esperaMs)
    }

    /**
     * Pedido a un módulo puntual. Si [idRespuesta] es null se escucha cualquier ID
     * (sirve para descubrir quién contesta).
     */
    suspend fun enviarFisico(idPedido: Int, idRespuesta: Int?, hex: String, esperaMs: Int = 200): RespuestaCan {
        if (cabecera != idPedido) { at("ATSH%03X".format(idPedido)); cabecera = idPedido }
        if (idRespuesta == null) {
            if (flujoManual != false) { at("ATFCSM0"); flujoManual = false }
            if (filtro != FILTRO_ABIERTO) { at("ATCF000"); at("ATCM000"); filtro = FILTRO_ABIERTO }
        } else {
            if (filtro != idRespuesta) { at("ATCRA%03X".format(idRespuesta)); filtro = idRespuesta }
            // Control de flujo explícito: los IDs de Mercedes no siguen la regla "pedido + 8"
            if (flujoCabecera != idPedido) {
                at("ATFCSH%03X".format(idPedido)); at("ATFCSD300000")
                flujoCabecera = idPedido
            }
            if (flujoManual != true) { at("ATFCSM1"); flujoManual = true }
        }
        timeout(esperaMs)
        return enviar(hex, esperaMs)
    }

    private suspend fun enviar(hex: String, esperaMs: Int): RespuestaCan {
        val lineas = crudo(hex, esperaMs * 4L + 4000)
        val tramas = ArrayList<CanFrame>()
        var sinDatos = false
        var error: String? = null
        for (l in lineas) {
            val trama = IsoTp.parsearLinea(l)
            when {
                trama != null -> tramas += trama
                l.contains("NO DATA", true) -> sinDatos = true
                l.contains("SEARCHING", true) -> Unit
                ERRORES.any { l.contains(it, true) } || l == "?" -> error = l
            }
        }
        return RespuestaCan(tramas, sinDatos, error)
    }

    companion object {
        private const val FILTRO_DESCONOCIDO = -3
        private const val FILTRO_AUTO = -2
        private const val FILTRO_ABIERTO = -1
        private val ERRORES = listOf(
            "CAN ERROR", "BUS ERROR", "BUS BUSY", "UNABLE TO CONNECT", "BUFFER FULL", "FB ERROR",
            "DATA ERROR", "RX ERROR", "STOPPED", "ACT ALERT", "LV RESET", "ERROR",
        )
    }
}
