package ar.garibaldi.obd.update

import android.content.Context
import ar.garibaldi.obd.BuildConfig
import org.json.JSONObject

/**
 * Mensajes del desarrollador para todos los que usan la app (mismo sistema que MTG Reforged):
 * `news.json` en la raíz del repo de GitHub, que se lee al abrir la app. Cada mensaje se muestra
 * una sola vez (se recuerda por su id).
 *
 * Cada mensaje puede traer su texto en inglés (`title_en`, `body_en`) para cuando la app está en inglés.
 */
object Novedades {
    /** [ids]: todos los mensajes que representa (varios changelogs mostrados juntos). */
    data class Mensaje(
        val id: String,
        val titulo: String,
        val cuerpo: String,
        val tituloEn: String?,
        val cuerpoEn: String?,
        val link: String?,
        val ids: List<String> = listOf(id),
    )

    private const val PREFS = "novedades"
    private const val VISTOS = "vistos"

    private val url get() = "https://raw.githubusercontent.com/${BuildConfig.UPDATE_REPO}/main/news.json"

    /** Llamar fuera del hilo principal. Si falla (sin internet…) simplemente no hay novedades esta vez. */
    fun pendientes(context: Context): List<Mensaje> {
        val vistos = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getStringSet(VISTOS, emptySet()).orEmpty()
        val todos = leerMensajes(url) ?: return emptyList()
        val nuevos = todos.filter { it.id !in vistos && esParaLaVersionInstalada(it.id) }
        // Los changelogs que se perdió (si salteó varias actualizaciones) van en un solo mensaje, el más nuevo arriba
        val (changelogs, otros) = nuevos.partition { VERSION.containsMatchIn(it.id) }
        val changelog = when (changelogs.size) {
            0 -> null
            1 -> changelogs[0]
            else -> Mensaje(
                id = "changelog",
                titulo = "Novedades desde tu última actualización",
                cuerpo = changelogs.reversed().joinToString("\n\n") { it.id + "\n" + it.cuerpo },
                tituloEn = "What's new since your last update",
                cuerpoEn = changelogs.reversed().joinToString("\n\n") { it.id + "\n" + (it.cuerpoEn ?: it.cuerpo) },
                link = null,
                ids = changelogs.map { it.id },
            )
        }
        return otros + listOfNotNull(changelog)
    }

    fun marcarVisto(context: Context, m: Mensaje) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val vistos = prefs.getStringSet(VISTOS, emptySet()).orEmpty() + m.ids
        prefs.edit().putStringSet(VISTOS, vistos).apply()
    }

    /** Interpreta el contenido de news.json; null si no es válido. */
    fun parsear(json: String): List<Mensaje>? = runCatching {
        val lista = JSONObject(json).optJSONArray("messages") ?: return null
        (0 until lista.length()).map { i ->
            val m = lista.getJSONObject(i)
            fun texto(clave: String) = m.optString(clave).takeIf { it.isNotBlank() && it != "null" }
            Mensaje(
                id = m.getString("id"),
                titulo = m.optString("title"),
                cuerpo = m.optString("body"),
                tituloEn = texto("title_en"),
                cuerpoEn = texto("body_en"),
                link = texto("link"),
            )
        }
    }.getOrNull()

    private fun leerMensajes(origen: String): List<Mensaje>? = leer(origen)?.let(::parsear)

    /**
     * Un changelog ("v0.02.0") es de esa versión: se muestra recién cuando está instalada esa versión
     * (o una más nueva), no en una copia vieja que baja las novedades nuevas. Los demás mensajes siempre.
     */
    private val VERSION = Regex("^v(\\d+)\\.(\\d+)\\.(\\d+)")

    fun esParaLaVersionInstalada(id: String, instalada: String = BuildConfig.VERSION_NAME): Boolean {
        val entrada = VERSION.find(id)?.groupValues?.drop(1)?.map { it.toInt() } ?: return true
        val mia = Regex("^(\\d+)\\.(\\d+)\\.(\\d+)").find(instalada)?.groupValues?.drop(1)?.map { it.toInt() } ?: return true
        for (i in 0..2) if (entrada[i] != mia[i]) return entrada[i] < mia[i]
        return true
    }
}
