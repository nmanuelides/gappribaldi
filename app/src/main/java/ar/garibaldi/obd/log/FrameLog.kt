package ar.garibaldi.obd.log

import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Registro de todo lo que va y viene del adaptador (TX/RX con hora).
 * Queda en memoria para mostrarlo en pantalla y, si se le da una carpeta,
 * también en un archivo por sesión para poder compartirlo.
 */
class FrameLog(private val carpeta: File? = null, private val maxLineas: Int = 6000) {
    private val hora = SimpleDateFormat("HH:mm:ss.SSS", Locale.US)
    private val lineas = ArrayDeque<String>()
    private var archivo: File? = null

    /** Se llama en cada línea nueva (puede ser desde cualquier hilo). */
    @Volatile var alAgregar: (() -> Unit)? = null

    @Synchronized
    fun nuevaSesion(encabezado: String) {
        lineas.clear()
        archivo = carpeta?.let { dir ->
            dir.mkdirs()
            // Conservamos solo las últimas sesiones para no llenar el teléfono
            dir.listFiles()?.sortedByDescending { it.name }?.drop(14)?.forEach { it.delete() }
            File(dir, "sesion-" + SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date()) + ".txt")
        }
        encabezado.lines().forEach { agregar("##", it) }
    }

    fun tx(texto: String) = agregar("TX", texto)
    fun rx(texto: String) = agregar("RX", texto)
    fun nota(texto: String) = agregar("--", texto)

    @Synchronized
    private fun agregar(tipo: String, texto: String) {
        val linea = "${hora.format(Date())} $tipo $texto"
        lineas.addLast(linea)
        while (lineas.size > maxLineas) lineas.removeFirst()
        try {
            archivo?.appendText(linea + "\n")
        } catch (_: Exception) {
        }
        alAgregar?.invoke()
    }

    @Synchronized fun copia(): List<String> = lineas.toList()

    @Synchronized fun texto(): String = lineas.joinToString("\n")

    @Synchronized fun archivoActual(): File? = archivo
}
