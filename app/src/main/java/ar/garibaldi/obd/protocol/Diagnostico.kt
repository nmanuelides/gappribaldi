package ar.garibaldi.obd.protocol

import ar.garibaldi.obd.adapter.Elm327
import ar.garibaldi.obd.dtc.Dtc
import ar.garibaldi.obd.dtc.DtcDecoder

enum class Protocolo { KWP, UDS }

sealed interface DiagResultado {
    class Positiva(val datos: ByteArray, val idRespuesta: Int) : DiagResultado
    class Negativa(val nrc: Int, val idRespuesta: Int) : DiagResultado
    data object SinRespuesta : DiagResultado
    class Error(val texto: String) : DiagResultado
}

object Nrc {
    const val PENDIENTE = 0x78

    fun texto(nrc: Int): String = when (nrc) {
        0x10 -> "rechazo general"
        0x11 -> "servicio no soportado"
        0x12 -> "subfunción no soportada"
        0x13 -> "largo de mensaje incorrecto"
        0x21 -> "módulo ocupado, reintentar"
        0x22 -> "condiciones no correctas (p. ej. motor en marcha o contacto mal puesto)"
        0x31 -> "pedido fuera de rango"
        0x33 -> "requiere acceso de seguridad (no lo hacemos)"
        0x78 -> "el módulo sigue procesando"
        0x7E, 0x7F, 0x80 -> "no disponible en la sesión actual"
        else -> "motivo desconocido"
    } + " (0x%02X)".format(nrc)

    fun esDeSesion(nrc: Int) = nrc == 0x7E || nrc == 0x7F || nrc == 0x80 || nrc == 0x22
}

class LecturaDtc(val dtcs: List<Dtc>, val protocolo: Protocolo?, val error: String?)

class Identificacion(val protocolo: Protocolo?, val datos: Map<String, String>)

/** Pedidos de diagnóstico a un módulo (KWP2000 sobre CAN o UDS), siempre pasando por [SafetyGuard]. */
class Diagnostico(private val elm: Elm327) {

    suspend fun fisico(
        idPedido: Int,
        idRespuesta: Int?,
        pedido: ByteArray,
        esperaMs: Int = 200,
        borradoConfirmado: Boolean = false,
    ): DiagResultado {
        SafetyGuard.verificar(pedido, borradoConfirmado)
        val r = elm.enviarFisico(idPedido, idRespuesta, pedido.hex(), esperaMs)
        val sid = pedido.u(0)
        val mensajes = IsoTp.reensamblar(r.tramas).filter {
            it.id != idPedido && (idRespuesta == null || it.id == idRespuesta) && it.datos.isNotEmpty()
        }
        mensajes.lastOrNull { it.datos.u(0) == sid + 0x40 }?.let {
            return DiagResultado.Positiva(it.datos, it.id)
        }
        val negativas = mensajes.filter { it.datos.size >= 3 && it.datos.u(0) == 0x7F && it.datos.u(1) == sid }
        (negativas.lastOrNull { it.datos.u(2) != Nrc.PENDIENTE } ?: negativas.lastOrNull())?.let {
            return DiagResultado.Negativa(it.datos.u(2), it.id)
        }
        return if (r.error != null) DiagResultado.Error(r.error) else DiagResultado.SinRespuesta
    }

    /** Pedido OBD estándar a todos los módulos de emisiones; devuelve solo las respuestas positivas. */
    suspend fun obd(pedido: ByteArray, esperaMs: Int = 300, borradoConfirmado: Boolean = false): List<MensajeIsoTp> {
        SafetyGuard.verificar(pedido, borradoConfirmado)
        val r = elm.enviarFuncionalObd(pedido.hex(), esperaMs)
        val sid = pedido.u(0)
        return IsoTp.reensamblar(r.tramas).filter {
            it.id in 0x7E8..0x7EF && it.datos.isNotEmpty() && it.datos.u(0) == sid + 0x40
        }
    }

    /**
     * Averigua qué "idioma" habla el módulo y lee lo que se pueda de su identificación.
     * Solo usa pedidos de lectura inofensivos.
     */
    suspend fun identificar(idPedido: Int, idRespuesta: Int): Identificacion {
        val datos = LinkedHashMap<String, String>()
        var protocolo: Protocolo? = null
        when (val r = fisico(idPedido, idRespuesta, byteArrayOf(0x1A, 0x87.toByte()), 300)) {
            is DiagResultado.Positiva -> {
                protocolo = Protocolo.KWP
                agregarIdent(datos, "Identificación (1A 87)", r.datos.drop(2))
            }
            is DiagResultado.Negativa -> if (r.nrc != 0x11) protocolo = Protocolo.KWP
            else -> Unit
        }
        if (protocolo != Protocolo.UDS) {
            val r = fisico(idPedido, idRespuesta, byteArrayOf(0x1A, 0x86.toByte()), 300)
            if (r is DiagResultado.Positiva) {
                protocolo = Protocolo.KWP
                agregarIdent(datos, "Identificación (1A 86)", r.datos.drop(2))
            }
        }
        if (datos.isEmpty()) {
            for ((did, nombre) in listOf(0x11 to "Hardware", 0x21 to "Software", 0x00 to "Variante")) {
                when (val r = fisico(idPedido, idRespuesta, byteArrayOf(0x22, 0xF1.toByte(), did.toByte()), 300)) {
                    is DiagResultado.Positiva -> {
                        protocolo = Protocolo.UDS
                        agregarIdent(datos, "$nombre (22 F1 %02X)".format(did), r.datos.drop(3))
                    }
                    is DiagResultado.Negativa -> if (r.nrc != 0x11 && protocolo == null) protocolo = Protocolo.UDS
                    else -> Unit
                }
            }
        }
        return Identificacion(protocolo, datos)
    }

    private fun agregarIdent(destino: MutableMap<String, String>, clave: String, bytes: List<Byte>) {
        if (bytes.isEmpty()) return
        val arr = bytes.toByteArray()
        val ascii = Regex("[A-Za-z0-9 ._/-]{6,}").findAll(String(arr, Charsets.ISO_8859_1))
            .map { it.value.trim() }.filter { it.isNotEmpty() }.joinToString(" ")
        destino[clave] = if (ascii.isNotEmpty()) "$ascii  [${arr.hex()}]" else arr.hex()
    }

    /**
     * Lee los códigos del módulo. Prueba primero el protocolo que ya funcionó
     * ([preferido]) y después el otro; devuelve cuál sirvió para recordarlo.
     */
    suspend fun leerDtc(idPedido: Int, idRespuesta: Int, preferido: Protocolo?, trenMotriz: Boolean): LecturaDtc {
        val orden = if (preferido == Protocolo.UDS) listOf(Protocolo.UDS, Protocolo.KWP)
        else listOf(Protocolo.KWP, Protocolo.UDS)
        var ultimoError = "El módulo no respondió"
        for (p in orden) {
            val pedidos = if (p == Protocolo.KWP) {
                listOf(byteArrayOf(0x18, 0x02, 0xFF.toByte(), 0x00), byteArrayOf(0x18, 0x00, 0xFF.toByte(), 0x00))
            } else {
                listOf(byteArrayOf(0x19, 0x02, 0x0D), byteArrayOf(0x19, 0x02, 0xFF.toByte()))
            }
            var sesionAbierta = false
            var i = 0
            while (i < pedidos.size) {
                when (val r = fisico(idPedido, idRespuesta, pedidos[i], 400)) {
                    is DiagResultado.Positiva -> {
                        val dtcs = if (p == Protocolo.KWP) DtcDecoder.kwp(r.datos, trenMotriz) else DtcDecoder.uds(r.datos)
                        return LecturaDtc(dtcs, p, null)
                    }
                    is DiagResultado.Negativa -> {
                        ultimoError = "El módulo rechazó la lectura: ${Nrc.texto(r.nrc)}"
                        if (Nrc.esDeSesion(r.nrc) && !sesionAbierta) {
                            // Algunos módulos solo dejan leer en sesión de diagnóstico extendida
                            sesionAbierta = true
                            val sesion = if (p == Protocolo.KWP) byteArrayOf(0x10, 0x92.toByte()) else byteArrayOf(0x10, 0x03)
                            if (fisico(idPedido, idRespuesta, sesion, 400) is DiagResultado.Positiva) continue
                        }
                        if (r.nrc == 0x11) break // este protocolo no es; probamos el otro
                    }
                    is DiagResultado.Error -> ultimoError = "Error del adaptador: ${r.texto}"
                    DiagResultado.SinRespuesta -> ultimoError = "El módulo no respondió"
                }
                i++
            }
        }
        return LecturaDtc(emptyList(), null, ultimoError)
    }

    /** Borra los códigos del módulo. Devuelve null si salió bien, o el motivo del fallo. */
    suspend fun borrarDtc(idPedido: Int, idRespuesta: Int, protocolo: Protocolo): String? {
        val pedido = if (protocolo == Protocolo.KWP) byteArrayOf(0x14, 0xFF.toByte(), 0x00)
        else byteArrayOf(0x14, 0xFF.toByte(), 0xFF.toByte(), 0xFF.toByte())
        var sesionAbierta = false
        while (true) {
            when (val r = fisico(idPedido, idRespuesta, pedido, 1000, borradoConfirmado = true)) {
                is DiagResultado.Positiva -> return null
                is DiagResultado.Negativa -> {
                    if (r.nrc == Nrc.PENDIENTE) return null // aceptó y sigue trabajando; la relectura confirma
                    if (Nrc.esDeSesion(r.nrc) && !sesionAbierta) {
                        sesionAbierta = true
                        val sesion = if (protocolo == Protocolo.KWP) byteArrayOf(0x10, 0x92.toByte()) else byteArrayOf(0x10, 0x03)
                        if (fisico(idPedido, idRespuesta, sesion, 400) is DiagResultado.Positiva) continue
                    }
                    return "El módulo no aceptó el borrado: ${Nrc.texto(r.nrc)}"
                }
                is DiagResultado.Error -> return "Error del adaptador: ${r.texto}"
                DiagResultado.SinRespuesta -> return "El módulo no respondió al borrado"
            }
        }
    }
}
