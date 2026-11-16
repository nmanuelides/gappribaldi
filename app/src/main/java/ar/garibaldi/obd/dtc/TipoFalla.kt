package ar.garibaldi.obd.dtc

import ar.garibaldi.obd.i18n.Idioma

/**
 * Tipo de falla de un código UDS de 3 bytes (el último byte, "failure type byte", SAE J2012-DA).
 * Es igual en todas las marcas: aunque no sepamos qué componente es, dice qué le pasa
 * (circuito abierto, en corto, señal inválida…).
 */
object TipoFalla {
    private val textos: Map<Int, Pair<String, String>> = mapOf(
        0x00 to ("sin detalle del tipo de falla" to "no failure type detail"),
        0x01 to ("falla eléctrica" to "general electrical failure"),
        0x02 to ("falla de la señal" to "general signal failure"),
        0x04 to ("falla interna del sistema" to "system internal failure"),
        0x07 to ("falla mecánica" to "mechanical failure"),
        0x08 to ("falla en el mensaje del bus" to "bus signal / message failure"),
        0x09 to ("falla del componente" to "component failure"),
        0x11 to ("circuito en corto a masa" to "circuit short to ground"),
        0x12 to ("circuito en corto a positivo" to "circuit short to battery"),
        0x13 to ("circuito abierto" to "circuit open"),
        0x14 to ("circuito en corto a masa o abierto" to "circuit short to ground or open"),
        0x15 to ("circuito abierto o en corto a positivo" to "circuit short to battery or open"),
        0x16 to ("tensión del circuito demasiado baja" to "circuit voltage below threshold"),
        0x17 to ("tensión del circuito demasiado alta" to "circuit voltage above threshold"),
        0x18 to ("corriente del circuito demasiado baja" to "circuit current below threshold"),
        0x19 to ("corriente del circuito demasiado alta" to "circuit current above threshold"),
        0x1A to ("resistencia demasiado baja" to "circuit resistance below threshold"),
        0x1B to ("resistencia demasiado alta" to "circuit resistance above threshold"),
        0x1C to ("tensión fuera de rango" to "circuit voltage out of range"),
        0x1D to ("corriente fuera de rango" to "circuit current out of range"),
        0x1E to ("resistencia fuera de rango" to "circuit resistance out of range"),
        0x1F to ("circuito intermitente" to "circuit intermittent"),
        0x23 to ("señal trabada abajo" to "signal stuck low"),
        0x24 to ("señal trabada arriba" to "signal stuck high"),
        0x29 to ("señal inválida" to "signal invalid"),
        0x2A to ("señal trabada (no cambia)" to "signal stuck in range"),
        0x2F to ("señal errática" to "signal erratic"),
        0x31 to ("no hay señal" to "no signal"),
        0x41 to ("error de suma de control" to "general checksum failure"),
        0x42 to ("falla de memoria" to "general memory failure"),
        0x49 to ("falla electrónica interna" to "internal electronic failure"),
        0x4A to ("componente incorrecto instalado" to "incorrect component installed"),
        0x4B to ("temperatura demasiado alta" to "over temperature"),
        0x51 to ("módulo sin programar" to "not programmed"),
        0x54 to ("falta calibración" to "missing calibration"),
        0x55 to ("módulo sin configurar" to "not configured"),
        0x62 to ("las señales no coinciden" to "signal compare failure"),
        0x64 to ("señal no creíble" to "signal plausibility failure"),
        0x71 to ("actuador trabado" to "actuator stuck"),
        0x72 to ("actuador trabado abierto" to "actuator stuck open"),
        0x73 to ("actuador trabado cerrado" to "actuator stuck closed"),
        0x76 to ("posición incorrecta" to "wrong mounting position"),
        0x77 to ("no llega a la posición pedida" to "commanded position not reachable"),
        0x81 to ("mensaje del bus inválido" to "invalid serial data received"),
        0x82 to ("contador del mensaje incorrecto" to "alive / sequence counter incorrect"),
        0x83 to ("suma de control del mensaje incorrecta" to "value of signal protection calculation incorrect"),
        0x86 to ("señal del bus inválida" to "signal invalid"),
        0x87 to ("falta un mensaje del bus" to "missing message"),
        0x88 to ("bus apagado (bus OFF)" to "bus off"),
        0x92 to ("rendimiento o funcionamiento incorrecto" to "performance or incorrect operation"),
        0x95 to ("montaje incorrecto" to "incorrect assembly"),
        0x96 to ("falla interna del componente" to "component internal failure"),
        0x98 to ("temperatura del componente demasiado alta" to "component or system over temperature"),
    )

    /** Texto del tipo de falla, o null si el byte no tiene un significado estándar conocido. */
    fun texto(ftb: Int?): String? {
        val t = textos[ftb ?: return null] ?: return null
        return if (Idioma.ingles) t.second else t.first
    }

    /** ¿Es una falla del circuito eléctrico (abierto o en corto)? En luces suele ser una lámpara o un conector. */
    fun esDeCircuito(ftb: Int?): Boolean = ftb in 0x11..0x15
}
