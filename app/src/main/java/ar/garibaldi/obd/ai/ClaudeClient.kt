package ar.garibaldi.obd.ai

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

/** Guarda la API key cifrada en el teléfono (EncryptedSharedPreferences). */
class AlmacenClave(context: Context) {
    private val prefs by lazy {
        val maestra = MasterKey.Builder(context).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build()
        EncryptedSharedPreferences.create(
            context, "claves", maestra,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
        )
    }

    fun leer(): String? = try {
        prefs.getString("anthropic", null)?.takeIf { it.isNotBlank() }
    } catch (_: Exception) {
        null
    }

    fun guardar(clave: String?) {
        try {
            prefs.edit().apply { if (clave.isNullOrBlank()) remove("anthropic") else putString("anthropic", clave.trim()) }.apply()
        } catch (_: Exception) {
        }
    }
}

/**
 * "Explicame este error": le pregunta a Claude por un código puntual. Opcional; requiere API key del usuario.
 *
 * Se llama a la Messages API por HTTPS directo en vez de usar el SDK oficial de Java: para una única
 * consulta de texto, el SDK sumaba unos 70 MB al APK.
 */
object ClaudeClient {
    const val MODELO = "claude-opus-5-5"
    private const val URL_API = "https://api.anthropic.com/v1/messages"

    private val SISTEMA = """
        Sos un mecánico argentino con mucha experiencia en Mercedes-Benz que le explica un código de falla
        al dueño de un Mercedes-Benz C350 W204 modelo 2007 (motor V6 nafta M272, caja automática 7G-Tronic 722.9).
        El dueño no es mecánico. Escribí en español rioplatense, claro y sin tecnicismos innecesarios.

        Estructura de la respuesta, en texto plano sin markdown:
        1. Qué significa (2 o 3 oraciones).
        2. Causas más probables en este auto, de la más a la menos probable.
        3. ¿Puedo seguir manejando? (sí / con cuidado / no, y por qué).
        4. Qué revisar o pedirle al mecánico.

        Reglas de honestidad: muchos códigos de Mercedes son propios de cada módulo y su significado exacto
        solo está en la documentación del fabricante. Si no estás seguro de qué significa este código en este
        módulo, decilo con todas las letras y aclará qué parte es una estimación; nunca presentes una
        suposición como si fuera la descripción oficial. No inventes números de parte ni procedimientos.
    """.trimIndent()

    private val SISTEMA_EN = """
        You are an experienced Mercedes-Benz mechanic explaining a fault code to the owner of a 2007
        Mercedes-Benz C350 W204 (M272 petrol V6, 7G-Tronic 722.9 automatic transmission).
        The owner is not a mechanic. Write in plain, clear English without unnecessary jargon.
        The context below may be in Spanish; always answer in English.

        Structure of the answer, plain text without markdown:
        1. What it means (2 or 3 sentences).
        2. Most likely causes on this car, from most to least likely.
        3. Can I keep driving? (yes / carefully / no, and why).
        4. What to check or ask the mechanic.

        Honesty rules: many Mercedes codes are specific to each module and their exact meaning is only in
        the manufacturer's documentation. If you're not sure what this code means in this module, say so
        plainly and state which part is an estimate; never present a guess as the official description.
        Don't make up part numbers or procedures.
    """.trimIndent()

    /** Devuelve la explicación, o lanza [IllegalStateException] con un mensaje listo para mostrar. */
    suspend fun explicar(apiKey: String, codigo: String, modulo: String, contexto: String, ingles: Boolean = false): String =
        withContext(Dispatchers.IO) {
            val cuerpo = JSONObject()
                .put("model", MODELO)
                .put("max_tokens", 6000)
                .put("system", if (ingles) SISTEMA_EN else SISTEMA)
                .put(
                    "messages",
                    JSONArray().put(
                        JSONObject().put("role", "user")
                            .put(
                                "content",
                                if (ingles) "Code: $codigo\nReporting module: $modulo\n$contexto"
                                else "Código: $codigo\nMódulo que lo reporta: $modulo\n$contexto",
                            )
                    ),
                )
            val conexion = URL(URL_API).openConnection() as HttpURLConnection
            try {
                conexion.requestMethod = "POST"
                conexion.connectTimeout = 15_000
                conexion.readTimeout = 180_000
                conexion.doOutput = true
                conexion.setRequestProperty("x-api-key", apiKey)
                conexion.setRequestProperty("anthropic-version", "2023-06-01")
                conexion.setRequestProperty("content-type", "application/json")
                conexion.outputStream.use { it.write(cuerpo.toString().toByteArray(Charsets.UTF_8)) }
                val estado = conexion.responseCode
                if (estado != 200) {
                    val detalle = runCatching {
                        JSONObject(conexion.errorStream.readBytes().toString(Charsets.UTF_8))
                            .getJSONObject("error").getString("message")
                    }.getOrNull()
                    throw IllegalStateException(
                        when (estado) {
                            401, 403 -> "La API key no es válida o no tiene permiso. Revisala en Ajustes."
                            429 -> "Demasiadas consultas seguidas o sin crédito. Esperá un momento y probá de nuevo."
                            in 500..599 -> "El servicio de IA está con problemas ($estado). Probá más tarde."
                            else -> "La IA rechazó la consulta ($estado)" + (detalle?.let { ": $it" } ?: ".")
                        }
                    )
                }
                val respuesta = JSONObject(conexion.inputStream.readBytes().toString(Charsets.UTF_8))
                if (respuesta.optString("stop_reason") == "refusal") {
                    throw IllegalStateException("La IA no pudo responder esta consulta.")
                }
                val bloques = respuesta.getJSONArray("content")
                val texto = (0 until bloques.length()).map { bloques.getJSONObject(it) }
                    .filter { it.optString("type") == "text" }
                    .joinToString("\n") { it.optString("text") }.trim()
                texto.ifEmpty { throw IllegalStateException("La IA devolvió una respuesta vacía. Probá de nuevo.") }
            } catch (e: IOException) {
                throw IllegalStateException("No hay conexión a internet para consultar a la IA.")
            } finally {
                conexion.disconnect()
            }
        }
}
