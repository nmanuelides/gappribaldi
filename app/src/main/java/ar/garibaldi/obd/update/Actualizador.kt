package ar.garibaldi.obd.update

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.core.content.FileProvider
import ar.garibaldi.obd.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.time.Instant

/**
 * Autoactualización desde los Releases de GitHub (mismo sistema que MTG Reforged). Cada push a main
 * hace que CI compile el APK firmado y publique un release con tag `build-<versionCode>`; la app compara
 * ese número con el suyo, baja el APK y se lo pasa al instalador de Android.
 */
class Actualizador(private val context: Context) {

    data class Version(val versionCode: Int, val nombre: String, val notas: String, val urlApk: String, val bytes: Long)

    /** El último release, si es más nuevo que lo instalado. */
    suspend fun buscar(): Version? = withContext(Dispatchers.IO) {
        val json = leer("https://api.github.com/repos/${BuildConfig.UPDATE_REPO}/releases/latest") ?: return@withContext null
        val release = JSONObject(json)
        val code = release.optString("tag_name").removePrefix("build-").toIntOrNull() ?: return@withContext null
        if (code <= BuildConfig.VERSION_CODE) return@withContext null
        val assets = release.optJSONArray("assets") ?: return@withContext null
        val apk = (0 until assets.length()).map(assets::getJSONObject)
            .firstOrNull { it.optString("name").endsWith(".apk") } ?: return@withContext null
        Version(
            versionCode = code,
            nombre = release.optString("name"),
            notas = release.optString("body"),
            urlApk = apk.getString("browser_download_url"),
            bytes = apk.optLong("size"),
        )
    }

    suspend fun bajar(version: Version, progreso: (Float) -> Unit): File = withContext(Dispatchers.IO) {
        val dir = File(context.cacheDir, "updates").apply { deleteRecursively(); mkdirs() }
        val destino = File(dir, "gappribaldi-${version.versionCode}.apk")
        val conexion = URL(version.urlApk).openConnection() as HttpURLConnection
        conexion.instanceFollowRedirects = true
        try {
            val total = conexion.contentLengthLong.takeIf { it > 0 } ?: version.bytes
            conexion.inputStream.use { entrada ->
                destino.outputStream().use { salida ->
                    val buffer = ByteArray(64 * 1024)
                    var leidos = 0L
                    while (true) {
                        val n = entrada.read(buffer)
                        if (n < 0) break
                        salida.write(buffer, 0, n)
                        leidos += n
                        if (total > 0) progreso(leidos.toFloat() / total)
                    }
                }
            }
        } finally {
            conexion.disconnect()
        }
        destino
    }

    fun puedeInstalar(): Boolean = context.packageManager.canRequestPackageInstalls()

    fun abrirPermisoDeInstalacion() {
        context.startActivity(
            Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${context.packageName}"))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
    }

    /** Le pasa el APK al instalador del sistema; el usuario confirma ahí. */
    fun instalar(apk: File) {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.archivos", apk)
        context.startActivity(
            Intent(Intent.ACTION_VIEW)
                .setDataAndType(uri, "application/vnd.android.package-archive")
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK),
        )
    }
}

/**
 * Una versión nueva que GitHub está compilando ahora: [version] ("v0.02.0", si el build la nombra),
 * empezó en [inicio] y se espera que tarde [duracionMs] (lo que tardaron los últimos).
 */
data class BuildEnCurso(val version: String?, val inicio: Long, val duracionMs: Long) {
    /** Cuánto debería llevar; nunca llega al 100 % hasta que sale el release. */
    fun progreso(ahora: Long = System.currentTimeMillis()): Float =
        ((ahora - inicio).toFloat() / duracionMs).coerceIn(0f, 0.99f)
}

/** Mira el workflow de GitHub que compila y publica las versiones nuevas. */
object BuildsEnCurso {
    private const val WORKFLOW = "release.yml"
    private val VERSION = Regex("""^v(\d+)\.(\d+)\.\d+""")

    /** El build que está corriendo, si arma una versión más nueva que la instalada. */
    suspend fun actual(): BuildEnCurso? = withContext(Dispatchers.IO) {
        val json = leer("https://api.github.com/repos/${BuildConfig.UPDATE_REPO}/actions/workflows/$WORKFLOW/runs?per_page=10")
            ?: return@withContext null
        val arr = JSONObject(json).optJSONArray("workflow_runs") ?: return@withContext null
        val runs = (0 until arr.length()).map(arr::getJSONObject)
        val activo = runs.firstOrNull { it.optString("status") != "completed" } ?: return@withContext null
        val version = VERSION.find(activo.optString("display_title"))
        // Un build de la versión instalada (o una vieja) no es una actualización
        if (version != null) {
            val armada = version.groupValues[1].toInt() * 100 + version.groupValues[2].toInt()
            val mia = VERSION.find("v" + BuildConfig.VERSION_NAME)?.let { it.groupValues[1].toInt() * 100 + it.groupValues[2].toInt() }
            if (mia != null && armada <= mia) return@withContext null
        }
        val duraciones = runs.filter { it.optString("conclusion") == "success" }
            .mapNotNull { r -> hora(r.optString("run_started_at"))?.let { ini -> hora(r.optString("updated_at"))?.minus(ini) } }
            .filter { it > 0 }
        BuildEnCurso(
            version?.value,
            hora(activo.optString("run_started_at")) ?: System.currentTimeMillis(),
            if (duraciones.isEmpty()) 8 * 60_000L else duraciones.average().toLong(),
        )
    }

    private fun hora(iso: String?): Long? = iso?.takeIf { it.isNotBlank() }?.let { runCatching { Instant.parse(it).toEpochMilli() }.getOrNull() }
}

/** GET a la API de GitHub; null si falla (sin internet, límite de consultas…). */
internal fun leer(url: String): String? {
    val conexion = URL(url).openConnection() as HttpURLConnection
    conexion.setRequestProperty("Accept", "application/vnd.github+json")
    conexion.connectTimeout = 10_000
    conexion.readTimeout = 10_000
    conexion.useCaches = false
    return try {
        if (conexion.responseCode == 200) conexion.inputStream.bufferedReader().readText() else null
    } catch (e: Exception) {
        null
    } finally {
        conexion.disconnect()
    }
}
