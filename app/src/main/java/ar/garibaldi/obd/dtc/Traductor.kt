package ar.garibaldi.obd.dtc

import com.google.mlkit.common.model.DownloadConditions
import com.google.mlkit.nl.translate.TranslateLanguage
import com.google.mlkit.nl.translate.Translation
import com.google.mlkit.nl.translate.TranslatorOptions
import kotlinx.coroutines.suspendCancellableCoroutine
import org.json.JSONObject
import java.io.File
import kotlin.coroutines.resume

/**
 * Traduce dentro del teléfono (ML Kit de Google, sin costo) los textos de la base de códigos
 * que están en el otro idioma: inglés → español, o las fichas en español → inglés. La primera vez baja un modelo de
 * unos 30 MB; después funciona sin internet. Lo traducido se guarda para no repetirlo.
 */
class Traductor(carpeta: File) {
    private val archivo = File(carpeta, "traducciones.json")
    private val cache: JSONObject = try {
        JSONObject(archivo.readText())
    } catch (_: Exception) {
        JSONObject()
    }

    private fun cliente(origen: String, destino: String) = Translation.getClient(
        TranslatorOptions.Builder().setSourceLanguage(origen).setTargetLanguage(destino).build()
    )

    private val haciaEspanol by lazy { cliente(TranslateLanguage.ENGLISH, TranslateLanguage.SPANISH) }
    private val haciaIngles by lazy { cliente(TranslateLanguage.SPANISH, TranslateLanguage.ENGLISH) }
    private val listos = HashSet<Boolean>()

    /** La clave incluye el destino: el mismo texto puede pedirse en los dos sentidos. */
    private fun clave(texto: String, aIngles: Boolean) = (if (aIngles) "en|" else "") + texto

    @Synchronized fun enCache(texto: String, aIngles: Boolean = false): String? = cache.optString(clave(texto, aIngles)).ifEmpty { null }

    /** Devuelve true si el modelo de traducción está disponible (lo baja si hace falta). */
    suspend fun preparar(aIngles: Boolean = false): Boolean {
        if (aIngles in listos) return true
        val c = if (aIngles) haciaIngles else haciaEspanol
        val ok = suspendCancellableCoroutine { k ->
            c.downloadModelIfNeeded(DownloadConditions.Builder().build())
                .addOnSuccessListener { k.resume(true) }
                .addOnFailureListener { k.resume(false) }
        }
        if (ok) listos += aIngles
        return ok
    }

    /** Traduce (o saca del guardado). Devuelve null si no se pudo. */
    suspend fun traducir(texto: String, aIngles: Boolean = false): String? {
        enCache(texto, aIngles)?.let { return it }
        if (!preparar(aIngles)) return null
        val c = if (aIngles) haciaIngles else haciaEspanol
        val traducido: String? = suspendCancellableCoroutine { k ->
            c.translate(texto)
                .addOnSuccessListener { k.resume(it) }
                .addOnFailureListener { k.resume(null) }
        }
        val final = traducido?.let { if (aIngles) it else pulir(it) } ?: return null
        synchronized(this) { cache.put(clave(texto, aIngles), final) }
        return final
    }

    @Synchronized
    fun guardar() {
        try {
            archivo.parentFile?.mkdirs()
            archivo.writeText(cache.toString())
        } catch (_: Exception) {
        }
    }

    /** Retoques a los tropiezos más comunes de la traducción automática en jerga de autos. */
    private fun pulir(t: String): String = t
        .replace("Banco ", "banco ").replace("gasolina", "nafta").replace("Gasolina", "Nafta")
        .replace("acelerador electrónico", "mariposa electrónica")
        .replace("bujías de encendido", "bujías")
        .replace("luz de verificación del motor", "testigo de motor")
        .replace("Luz de verificación del motor", "Testigo de motor")
        .replace("luz de control del motor", "testigo de motor")
        .replace("Luz de control del motor", "Testigo de motor")
        .replace("neumático", "cubierta")
        .replace("maletero", "baúl")
}
