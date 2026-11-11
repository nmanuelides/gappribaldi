package ar.garibaldi.obd.protocol

class SolicitudBloqueada(mensaje: String) : IllegalArgumentException(mensaje)

/**
 * Filtro de seguridad: TODO pedido de diagnóstico pasa por acá antes de salir.
 * Solo se permite leer (identificación, códigos, datos OBD estándar), mantener la
 * sesión y borrar códigos con confirmación. Nada de escritura, codificación,
 * rutinas, control de actuadores, acceso de seguridad ni flasheo.
 */
object SafetyGuard {
    /** Sesiones permitidas: por defecto y extendida (KWP y UDS). Nunca programación. */
    private val sesiones = setOf(0x01, 0x03, 0x81, 0x92)

    fun verificar(pedido: ByteArray, borradoConfirmado: Boolean = false) {
        if (pedido.isEmpty()) throw SolicitudBloqueada("Pedido vacío")
        val sid = pedido.u(0)
        val sub = if (pedido.size > 1) pedido.u(1) else -1
        val ok = when (sid) {
            0x01, 0x02, 0x03, 0x07, 0x09, 0x0A -> true // OBD estándar, solo lectura
            0x04 -> borradoConfirmado && pedido.size == 1 // borrar códigos OBD
            0x10 -> pedido.size == 2 && sub in sesiones
            0x14 -> borradoConfirmado // borrar códigos del módulo
            0x18, 0x19 -> true // leer códigos (KWP / UDS)
            0x1A -> true // identificación KWP
            0x22 -> pedido.size == 3 && sub == 0xF1 // identificación UDS (DID F1xx)
            0x3E -> true // TesterPresent
            else -> false
        }
        if (!ok) {
            throw SolicitudBloqueada(
                "Pedido ${pedido.hex()} bloqueado: esta app solo lee y borra códigos."
            )
        }
    }

    fun permitido(pedido: ByteArray, borradoConfirmado: Boolean = false): Boolean = try {
        verificar(pedido, borradoConfirmado)
        true
    } catch (_: SolicitudBloqueada) {
        false
    }
}
