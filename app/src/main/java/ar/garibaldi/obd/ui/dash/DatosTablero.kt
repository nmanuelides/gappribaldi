package ar.garibaldi.obd.ui.dash

import java.util.Calendar
import java.util.Locale

/**
 * Lo que muestran los tableros. Los datos crudos salen del modo 01 del OBD; el resto se calcula
 * (consumo, autonomía, marcha estimada, viaje). Un dato que el auto no informa queda en null y el
 * tablero muestra "--" en su lugar.
 */
data class DatosTablero(
    val rpm: Float = 0f,
    val velocidad: Float = 0f,
    val refrigerante: Float? = null,
    val aceite: Float? = null,
    val admision: Float? = null,
    val ambiente: Float? = null,
    val nafta: Float? = null,
    val tension: Float? = null,
    val carga: Float? = null,
    val mariposa: Float? = null,
    val multiple: Float? = null,
    val atmosferica: Float? = null,
    val maf: Float? = null,
    val odometro: Float? = null,
    val testigoMotor: Boolean = false,
    /** Kilómetros y segundos desde que se abrió el tablero. */
    val viajeKm: Float = 0f,
    val viajeSeg: Float = 0f,
    val litrosViaje: Float = 0f,
    /** Animación de encendido: 0..1 (agujas al máximo y testigos prendidos), 0 en marcha normal. */
    val barrido: Float = 0f,
    val marca: String = "",
    val modelo: String = "",
    val tanqueLitros: Float = 60f,
    /** Mostrar la velocidad en millas por hora (por defecto km/h). */
    val millas: Boolean = false,
) {
    private val factor: Float get() = if (millas) 0.621371f else 1f

    /** Velocidad en la unidad elegida, y su unidad. */
    val vel: Float get() = velocidad * factor
    val uVel: String get() = if (millas) "mph" else "km/h"

    /** La otra unidad, para los tableros que muestran las dos. */
    val velOtra: Float get() = if (millas) velocidad else velocidad * 0.621371f
    val uOtra: String get() = if (millas) "km/h" else "mph"

    /** Prueba de lámparas mientras dura la animación de encendido. */
    val prueba: Boolean get() = barrido > 0.02f

    fun rpmEn(max: Float) = maxOf(rpm, barrido * max)
    fun velEn(max: Float) = maxOf(vel, barrido * max)
    fun deA(v: Float?, max: Float, min: Float = 0f) = maxOf(v ?: min, min + barrido * (max - min))

    val motorEnMarcha: Boolean get() = rpm > 350f

    /** Litros por hora a partir del caudal de aire (nafta, mezcla estequiométrica). */
    val litrosHora: Float? get() = maf?.let { it * 3600f / (14.7f * 745f) }

    /** Consumo instantáneo en l/100 km (en marcha). */
    val consumo: Float? get() = litrosHora?.let { if (velocidad > 3f) it * 100f / velocidad else null }

    val consumoPromedio: Float? get() = if (viajeKm > 0.3f) litrosViaje * 100f / viajeKm else null

    val velocidadPromedio: Float get() = if (viajeSeg > 5f) viajeKm / (viajeSeg / 3600f) else 0f
    val velPromedio: Float get() = velocidadPromedio * factor

    /** Kilómetros que quedan con la nafta del tanque, con el consumo del viaje (o uno típico). */
    val autonomia: Float? get() = nafta?.let { n ->
        val c = (consumoPromedio ?: consumo ?: 10.5f).coerceIn(4f, 30f)
        n / 100f * tanqueLitros / c * 100f
    }

    /** Presión de turbo / vacío en el múltiple (kPa sobre la atmosférica). */
    val presionRelativa: Float? get() = multiple?.let { it - (atmosferica ?: 101f) }

    /** 0..100: lo que se le pide al motor (carga, o mariposa si no hay carga). */
    val potencia: Float get() = (carga ?: mariposa ?: 0f).coerceIn(0f, 100f)

    /** Marcha estimada por la relación entre velocidad y vueltas (null parado o embragado). */
    val marcha: Int? get() {
        if (velocidad < 4f || rpm < 800f) return null
        val kmh1000 = velocidad / (rpm / 1000f)
        val cortes = floatArrayOf(10.5f, 16.5f, 23f, 30f, 38f, 46f)
        return (cortes.indexOfFirst { kmh1000 < it }.takeIf { it >= 0 } ?: cortes.size) + 1
    }

    /** Palanca: P parado con el motor tranquilo, N parado acelerando, D andando. */
    val palanca: Char get() = when {
        velocidad >= 2f -> 'D'
        rpm > 1300f -> 'N'
        else -> 'P'
    }

    /** "D3", "D" o "P": lo que muestran los tableros que indican la marcha. */
    val textoMarcha: String get() = if (palanca == 'D') "D" + (marcha?.toString() ?: "") else palanca.toString()

    /** Modo de manejo según lo que se le pide al motor (los tableros deportivos lo muestran). */
    val modo: String get() = when {
        rpm > 5000f || potencia > 85f -> "SPORT+"
        rpm > 3500f || potencia > 60f -> "SPORT"
        potencia < 25f && velocidad > 5f -> "ECO"
        else -> "COMFORT"
    }

    companion object {
        fun num(v: Float?, decimales: Int = 0, guion: String = "--"): String =
            if (v == null) guion else if (decimales == 0) Math.round(v).toString()
            else String.format(Locale.US, "%.${decimales}f", v).replace('.', if (ar.garibaldi.obd.i18n.Idioma.ingles) '.' else ',')

        fun hora(): String {
            val c = Calendar.getInstance()
            return String.format(Locale.US, "%d:%02d", c.get(Calendar.HOUR_OF_DAY), c.get(Calendar.MINUTE))
        }

        fun hora24(): String {
            val c = Calendar.getInstance()
            return String.format(Locale.US, "%02d:%02d", c.get(Calendar.HOUR_OF_DAY), c.get(Calendar.MINUTE))
        }

        fun fecha(): String {
            val c = Calendar.getInstance()
            return String.format(Locale.US, "%02d/%02d/%04d", c.get(Calendar.DAY_OF_MONTH), c.get(Calendar.MONTH) + 1, c.get(Calendar.YEAR))
        }

        fun fechaCorta(): String {
            val c = Calendar.getInstance()
            return String.format(Locale.US, "%02d/%02d/%02d", c.get(Calendar.DAY_OF_MONTH), c.get(Calendar.MONTH) + 1, c.get(Calendar.YEAR) % 100)
        }

        /** "m:ss" o "h:mm:ss". */
        fun duracion(seg: Float): String {
            val s = seg.toLong()
            return if (s >= 3600) String.format(Locale.US, "%d:%02d:%02d", s / 3600, s / 60 % 60, s % 60)
            else String.format(Locale.US, "%d:%02d", s / 60, s % 60)
        }
    }
}
