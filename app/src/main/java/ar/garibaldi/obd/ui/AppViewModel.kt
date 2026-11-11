package ar.garibaldi.obd.ui

import android.app.Application
import android.bluetooth.BluetoothDevice
import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import ar.garibaldi.obd.BuildConfig
import ar.garibaldi.obd.adapter.AdaptadorException
import ar.garibaldi.obd.adapter.Elm327
import ar.garibaldi.obd.adapter.InfoAdaptador
import ar.garibaldi.obd.ai.AlmacenClave
import ar.garibaldi.obd.auto.Auto
import ar.garibaldi.obd.ai.ClaudeClient
import ar.garibaldi.obd.dtc.Dtc
import ar.garibaldi.obd.dtc.DtcRepositorio
import ar.garibaldi.obd.dtc.FuenteInfo
import ar.garibaldi.obd.dtc.InfoDtc
import ar.garibaldi.obd.dtc.Traductor
import ar.garibaldi.obd.i18n.Idioma
import ar.garibaldi.obd.log.FrameLog
import ar.garibaldi.obd.modules.AlmacenModulos
import ar.garibaldi.obd.modules.Categoria
import ar.garibaldi.obd.modules.Descubridor
import ar.garibaldi.obd.modules.Modulo
import ar.garibaldi.obd.obd.CuadroCongelado
import ar.garibaldi.obd.obd.EstadoEmisiones
import ar.garibaldi.obd.obd.Obd2
import ar.garibaldi.obd.obd.Pid
import ar.garibaldi.obd.obd.Pids
import ar.garibaldi.obd.protocol.Diagnostico
import ar.garibaldi.obd.protocol.Protocolo
import ar.garibaldi.obd.protocol.SolicitudBloqueada
import ar.garibaldi.obd.scan.Escaner
import ar.garibaldi.obd.scan.Historial
import ar.garibaldi.obd.scan.Informe
import ar.garibaldi.obd.scan.ResultadoEscaneo
import ar.garibaldi.obd.scan.ResultadoModulo
import ar.garibaldi.obd.transport.BluetoothSppTransport
import ar.garibaldi.obd.transport.ObdTransport
import ar.garibaldi.obd.transport.SimulatedTransport
import ar.garibaldi.obd.update.Actualizador
import ar.garibaldi.obd.update.BuildEnCurso
import ar.garibaldi.obd.update.BuildsEnCurso
import ar.garibaldi.obd.update.Novedades
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File

sealed interface Pantalla {
    data object Inicio : Pantalla
    data object Resultado : Pantalla
    data class Detalle(val claveModulo: String, val codigo: String) : Pantalla
    data object Modulos : Pantalla
    data object EnVivo : Pantalla
    data object Emisiones : Pantalla
    data object Conexion : Pantalla
    data object Registro : Pantalla
    data object HistorialLista : Pantalla
    data object Ajustes : Pantalla
}

sealed interface Conexion {
    data object Desconectado : Conexion
    data class Conectando(val texto: String) : Conexion
    data class Conectado(val nombre: String, val info: InfoAdaptador, val demo: Boolean) : Conexion
    data class Error(val texto: String) : Conexion
}

/** Tarea larga en curso (escaneo, búsqueda de módulos, borrado). */
data class Trabajo(val titulo: String, val detalle: String, val fraccion: Float?)

class AppViewModel(app: Application) : AndroidViewModel(app) {
    private val dir: File = app.filesDir
    private val prefs = app.getSharedPreferences("ajustes", Context.MODE_PRIVATE)

    val log = FrameLog(File(dir, "logs"))
    val repo = DtcRepositorio { nombre -> runCatching { app.assets.open("dtc/$nombre") }.getOrNull() }
    // Cada marca tiene su propio mapa de módulos (el del Mercedes queda en el archivo de siempre)
    private val marcaInicial = Marcas.porNombre(prefs.getString("marca", null))
    private var almacenModulos = AlmacenModulos(dir, archivoModulos(marcaInicial))
    private val historialStore = Historial(File(dir, "historial"))
    private val traductor = Traductor(dir)
    private val almacenClave = AlmacenClave(app)

    // ── Estado que mira la interfaz ──────────────────────────────────────────
    val pila = mutableStateListOf<Pantalla>(Pantalla.Inicio)
    var conexion by mutableStateOf<Conexion>(Conexion.Desconectado); private set
    var trabajo by mutableStateOf<Trabajo?>(null); private set
    var modulos by mutableStateOf(almacenModulos.cargar()); private set
    var resultado by mutableStateOf<ResultadoEscaneo?>(null); private set
    var resultadoEsActual by mutableStateOf(false); private set
    var clavesAnteriores by mutableStateOf<Set<String>?>(null); private set
    var historial by mutableStateOf(listOf<ResultadoEscaneo>()); private set
    var mensaje by mutableStateOf<String?>(null)
    var versionLog by mutableIntStateOf(0); private set
    var emisiones by mutableStateOf<List<EstadoEmisiones>?>(null); private set
    var cuadro by mutableStateOf<CuadroCongelado?>(null); private set
    var pidsVivo by mutableStateOf(listOf<Pid>()); private set
    val valoresVivo = mutableStateMapOf<Int, Double>()
    val traducciones = mutableStateMapOf<String, String>()
    val explicaciones = mutableStateMapOf<String, String>()
    var iaCargando by mutableStateOf<String?>(null); private set
    var traducirAuto by mutableStateOf(prefs.getString("traducir", null)); private set
    var tieneClave by mutableStateOf(false); private set
    var bajandoTraduccion by mutableStateOf(false); private set

    // Apariencia: marca (colores de acento), texto del modelo y foto del auto para la portada
    var marca by mutableStateOf(marcaInicial); private set
    var modelo by mutableStateOf(prefs.getString("modelo", null) ?: marcaInicial.modelo); private set
    private val archivoFoto = File(dir, "auto.img")
    var fotoAuto by mutableStateOf(archivoFoto.takeIf { it.exists() }); private set
    var versionFoto by mutableIntStateOf(0); private set

    /** Marca que la app abre directo, sin preguntar. Sin favorita, al abrirse pregunta la marca cada vez. */
    var marcaFavorita by mutableStateOf(prefs.getString("marcaFavorita", null)); private set
    var eligiendoMarca by mutableStateOf(prefs.getString("marcaFavorita", null) == null); private set

    fun abrirSelectorMarca() { eligiendoMarca = true }

    fun cerrarSelectorMarca() { eligiendoMarca = false }

    fun confirmarMarca(m: Marca, favorita: Boolean) {
        elegirMarca(m)
        marcaFavorita = if (favorita) m.nombre else marcaFavorita?.takeIf { it != m.nombre }
        prefs.edit().putString("marcaFavorita", marcaFavorita).apply()
        eligiendoMarca = false
    }

    fun elegirMarca(m: Marca) {
        if (m.nombre == marca.nombre) return
        // Si el modelo era el de fábrica de la marca anterior, pasa al de la nueva
        if (modelo.equals(marca.modelo, ignoreCase = true)) modelo = m.modelo
        marca = m
        prefs.edit().putString("marca", m.nombre).putString("modelo", modelo).apply()
        sincronizarAuto()
        almacenModulos = AlmacenModulos(getApplication<Application>().filesDir, archivoModulos(m))
        if (!esDemo) modulos = almacenModulos.cargar()
        resultado = null
        resultadoEsActual = false
    }

    fun cambiarModelo(texto: String) {
        modelo = texto.trim().take(14).ifEmpty { marca.modelo }
        prefs.edit().putString("modelo", modelo).apply()
        sincronizarAuto()
    }

    private fun sincronizarAuto() {
        Auto.marca = marca.nombre
        Auto.modelo = modelo
    }

    /** Copia la foto elegida (idealmente un PNG con fondo transparente) para usarla en la portada. */
    fun usarFoto(uri: android.net.Uri?) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                if (uri == null) {
                    archivoFoto.delete()
                } else {
                    getApplication<Application>().contentResolver.openInputStream(uri)?.use { entrada ->
                        archivoFoto.outputStream().use { entrada.copyTo(it) }
                    }
                }
                fotoAuto = archivoFoto.takeIf { it.exists() }
                versionFoto++
            } catch (e: Exception) {
                mensaje = "No se pudo usar esa imagen: ${e.message}"
            }
        }
    }

    private var transporte: ObdTransport? = null
    private var elm: Elm327? = null
    private var diag: Diagnostico? = null
    private var obd: Obd2? = null
    private val candado = Mutex()
    private var tarea: Job? = null
    private var tareaVivo: Job? = null

    init {
        Idioma.ingles = prefs.getString("idioma", "es") == "en"
        sincronizarAuto()
        log.alAgregar = { versionLog++ }
        viewModelScope.launch(Dispatchers.IO) {
            val h = historialStore.listar()
            val clave = almacenClave.leer() != null
            withContext(Dispatchers.Main) { historial = h; tieneClave = clave }
        }
    }

    // ── Navegación ───────────────────────────────────────────────────────────
    val pantalla: Pantalla get() = pila.last()

    fun ir(p: Pantalla) {
        if (pila.last() == Pantalla.EnVivo) detenerVivo()
        pila += p
        when (p) {
            Pantalla.EnVivo -> iniciarVivo()
            Pantalla.Emisiones -> leerEmisiones()
            is Pantalla.Detalle -> traducirDetalle(p)
            else -> Unit
        }
    }

    /** Devuelve false si ya no hay a dónde volver (la actividad se cierra). */
    fun volver(): Boolean {
        if (pila.size <= 1) return false
        if (pila.last() == Pantalla.EnVivo) detenerVivo()
        pila.removeAt(pila.lastIndex)
        return true
    }

    // ── Conexión ─────────────────────────────────────────────────────────────
    fun conectarBluetooth(d: BluetoothDevice) {
        prefs.edit().putString("ultimo", d.address).apply()
        conectar(BluetoothSppTransport(d), demo = false)
    }

    fun conectarDemo() = conectar(SimulatedTransport(), demo = true)

    val ultimoDispositivo: String? get() = prefs.getString("ultimo", null)

    private fun conectar(t: ObdTransport, demo: Boolean) {
        desconectar()
        tarea = viewModelScope.launch {
            conexion = Conexion.Conectando("Conectando con ${t.nombre}…")
            try {
                candado.withLock {
                    log.nuevaSesion(
                        "Gappribaldi ${BuildConfig.VERSION_NAME} · ${android.os.Build.MANUFACTURER} ${android.os.Build.MODEL} · " +
                            "Android ${android.os.Build.VERSION.RELEASE}\nAdaptador: ${t.nombre}"
                    )
                    withContext(Dispatchers.IO) { t.conectar() }
                    transporte = t
                    conexion = Conexion.Conectando("Configurando el adaptador…")
                    val e = Elm327(t, log)
                    val info = withContext(Dispatchers.IO) { e.inicializar() }
                    elm = e
                    diag = Diagnostico(e).also { obd = Obd2(it) }
                    conexion = Conexion.Conectado(t.nombre, info, demo)
                    if (demo) modulos = emptyList() // el auto de demostración tiene su propio mapa de módulos
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                log.nota("Fallo de conexión: ${e.message}")
                t.cerrar()
                conexion = Conexion.Error(e.message ?: "No se pudo conectar")
            }
        }
    }

    fun desconectar() {
        tarea?.cancel(); detenerVivo()
        transporte?.cerrar()
        transporte = null; elm = null; diag = null; obd = null
        trabajo = null
        if (conexion is Conexion.Conectado) {
            if ((conexion as Conexion.Conectado).demo) modulos = almacenModulos.cargar()
            log.nota("Desconectado")
        }
        conexion = Conexion.Desconectado
    }

    val conectado: Boolean get() = conexion is Conexion.Conectado
    private val esDemo: Boolean get() = (conexion as? Conexion.Conectado)?.demo == true

    /** Corre una tarea larga contra el adaptador, de a una por vez, y traduce los errores a mensajes. */
    private fun lanzar(titulo: String, bloque: suspend (Diagnostico, Obd2, Elm327) -> Unit) {
        val d = diag; val o = obd; val e = elm
        if (d == null || o == null || e == null) {
            mensaje = "Primero conectate al adaptador."
            return
        }
        if (trabajo != null) return
        detenerVivo()
        trabajo = Trabajo(titulo, "", null)
        tarea = viewModelScope.launch {
            try {
                candado.withLock { withContext(Dispatchers.IO) { bloque(d, o, e) } }
            } catch (ex: CancellationException) {
                log.nota("$titulo: cancelado")
            } catch (ex: SolicitudBloqueada) {
                mensaje = ex.message
            } catch (ex: AdaptadorException) {
                transporte?.cerrar()
                transporte = null; elm = null; diag = null; obd = null
                conexion = Conexion.Error(ex.message ?: "Se perdió la conexión")
                mensaje = "Se perdió la conexión con el adaptador. Volvé a conectar."
            } catch (ex: Exception) {
                log.nota("$titulo: error inesperado ${ex.javaClass.simpleName}: ${ex.message}")
                mensaje = "Algo salió mal: ${ex.message}. Mirá el registro para más detalle."
            } finally {
                trabajo = null
                if (pantalla == Pantalla.EnVivo) iniciarVivo()
            }
        }
    }

    fun cancelarTrabajo() {
        tarea?.cancel()
    }

    private fun avance(titulo: String, hecho: Int, total: Int, detalle: String) {
        trabajo = Trabajo(titulo, detalle, if (total > 0) hecho.toFloat() / total else null)
    }

    private fun guardarModulos(nuevos: List<Modulo>) {
        modulos = nuevos
        if (!esDemo) almacenModulos.guardar(nuevos)
    }

    // ── Módulos ──────────────────────────────────────────────────────────────
    fun buscarModulos(completo: Boolean, escanearDespues: Boolean = false) {
        val titulo = if (completo) "Búsqueda completa de módulos" else "Buscando módulos"
        lanzar(titulo) { d, o, e ->
            val encontrados = Descubridor(d, log).buscar(completo, modulos) { hecho, total, n ->
                avance(titulo, hecho, total, "Dirección $hecho de $total · $n módulos encontrados")
            }
            // En la búsqueda rápida no perdemos módulos que ya conocíamos de una búsqueda completa
            val conservados = if (completo) emptyList() else modulos.filter { m -> encontrados.none { it.idPedido == m.idPedido } }
            guardarModulos((encontrados + conservados).sortedBy { it.idPedido })
            mensaje = "Se encontraron ${encontrados.size} módulos."
            if (escanearDespues) hacerEscaneo(d, o, e)
        }
    }

    fun renombrarModulo(m: Modulo, nombre: String, categoria: Categoria) {
        guardarModulos(modulos.map {
            if (it.idPedido == m.idPedido) it.copy(nombre = nombre, categoria = categoria, nombreConfirmado = true) else it
        })
    }

    fun olvidarModulos() {
        guardarModulos(emptyList())
    }

    // ── Escaneo ──────────────────────────────────────────────────────────────
    fun escanear() {
        if (modulos.isEmpty()) {
            buscarModulos(completo = false, escanearDespues = true)
        } else {
            lanzar("Escaneando el auto") { d, o, e -> hacerEscaneo(d, o, e) }
        }
    }

    private suspend fun hacerEscaneo(d: Diagnostico, o: Obd2, e: Elm327) {
        val titulo = "Escaneando el auto"
        val previo = historial.firstOrNull()
        val voltaje = e.leerVoltaje()
        val r = Escaner(d, o).escanear(
            modulos, voltaje,
            progreso = { hecho, total, texto -> avance(titulo, hecho, total, texto) },
            protocoloDetectado = { m, p -> recordarProtocolo(m, p) },
        )
        if (!esDemo) {
            historialStore.guardar(r)
            historial = historialStore.listar()
        }
        clavesAnteriores = if (esDemo) null else previo?.clavesDeCodigos()
        resultado = r
        resultadoEsActual = true
        withContext(Dispatchers.Main) {
            if (pila.last() != Pantalla.Resultado) pila += Pantalla.Resultado
        }
        traducirTitulos(r)
    }

    private fun recordarProtocolo(m: Modulo, p: Protocolo) {
        guardarModulos(modulos.map { if (it.idPedido == m.idPedido) it.copy(protocolo = p) else it })
    }

    fun verHistorico(r: ResultadoEscaneo) {
        resultado = r
        resultadoEsActual = false
        clavesAnteriores = null
        ir(Pantalla.Resultado)
        viewModelScope.launch { traducirTitulos(r) }
    }

    fun borrarHistorico(r: ResultadoEscaneo) {
        historialStore.borrar(r.fecha)
        historial = historialStore.listar()
    }

    fun informe(): String? = resultado?.let { r ->
        // El informe busca los títulos por código: código → título ya traducido al idioma actual
        val titulos = r.modulos.flatMap { m ->
            m.dtcs.mapNotNull { d -> d.claveGenerica?.let { it to tr(repo.buscar(d, m.categoria, m.nombre).titulo) } }
        }.toMap()
        Informe.texto(r, repo, titulos)
    }

    /** Cambia el idioma de toda la app (queda guardado) y traduce lo que haga falta del resultado abierto. */
    fun cambiarIdioma(ingles: Boolean) {
        Idioma.ingles = ingles
        prefs.edit().putString("idioma", if (ingles) "en" else "es").apply()
        viewModelScope.launch {
            resultado?.let { traducirTitulos(it) }
            (pantalla as? Pantalla.Detalle)?.let { traducirDetalle(it) }
        }
    }

    // ── Borrado ──────────────────────────────────────────────────────────────
    /** Borra los códigos de un módulo (o de todos si [clave] es null) y vuelve a leer. */
    fun borrar(clave: String?) {
        val r = resultado ?: return
        val titulo = if (clave == null) "Borrando todos los códigos" else "Borrando códigos"
        lanzar(titulo) { d, o, e ->
            val fallos = ArrayList<String>()
            val objetivos = r.modulos.filter { clave == null || it.clave == clave }
            if (objetivos.any { it.esObd }) {
                avance(titulo, 0, 0, "Borrando códigos de emisiones (OBD)…")
                if (!o.borrar()) fallos += "OBD estándar: el auto no confirmó el borrado"
            }
            for (rm in objetivos.filter { !it.esObd && (it.dtcs.isNotEmpty() || clave != null) }) {
                val m = modulos.firstOrNull { it.idPedido == rm.idPedido } ?: continue
                avance(titulo, 0, 0, "Borrando ${m.nombre}…")
                val proto = m.protocolo ?: continue
                d.borrarDtc(m.idPedido, m.idRespuesta, proto)?.let { fallos += "${m.nombre}: $it" }
            }
            delay(800) // les damos un respiro a los módulos antes de releer
            hacerEscaneo(d, o, e)
            mensaje = if (fallos.isEmpty()) "Borrado hecho. Abajo está lo que quedó después de volver a leer."
            else "No se pudo borrar todo:\n" + fallos.joinToString("\n")
        }
    }

    // ── Emisiones y datos en vivo ────────────────────────────────────────────
    private fun leerEmisiones() {
        emisiones = null; cuadro = null
        lanzar("Leyendo monitores de emisiones") { _, o, _ ->
            emisiones = o.estado()
            cuadro = o.cuadroCongelado(o.pidsSoportados())
        }
    }

    private fun iniciarVivo() {
        val o = obd ?: return
        if (tareaVivo?.isActive == true || trabajo != null) return
        tareaVivo = viewModelScope.launch(Dispatchers.IO) {
            try {
                if (pidsVivo.isEmpty()) {
                    val soportados = candado.withLock { o.pidsSoportados() }
                    pidsVivo = Pids.lista.filter { it.pid in soportados }
                }
                while (isActive) {
                    for (pid in pidsVivo) {
                        if (!isActive) break
                        candado.withLock { o.leerPid(pid) }?.let { valoresVivo[pid.pid] = it }
                    }
                    if (pidsVivo.isEmpty()) delay(1000)
                }
            } catch (_: CancellationException) {
            } catch (ex: Exception) {
                mensaje = "Se cortó la lectura en vivo: ${ex.message}"
            }
        }
    }

    private fun detenerVivo() {
        tareaVivo?.cancel()
        tareaVivo = null
    }

    // ── Traducción y explicación con IA ──────────────────────────────────────
    fun info(dtc: Dtc, categoria: Categoria, modulo: String = ""): InfoDtc = repo.buscar(dtc, categoria, modulo)

    /** Texto en español si ya está traducido; si no, el original. */
    fun tr(texto: String): String = traducciones[claveTr(texto)] ?: texto

    /** Las traducciones se guardan por sentido: un texto en español traducido al inglés no se muestra en español. */
    private fun claveTr(texto: String) = (if (Idioma.ingles) "en|" else "") + texto

    fun decidirTraduccion(aceptar: Boolean) {
        traducirAuto = if (aceptar) "si" else "no"
        prefs.edit().putString("traducir", traducirAuto).apply()
        if (aceptar) {
            viewModelScope.launch {
                bajandoTraduccion = true
                val ok = traductor.preparar(Idioma.ingles)
                bajandoTraduccion = false
                if (!ok) mensaje = "No se pudo bajar el paquete de traducción. Revisá la conexión a internet."
                resultado?.let { traducirTitulos(it) }
                (pantalla as? Pantalla.Detalle)?.let { traducirDetalle(it) }
            }
        }
    }

    private suspend fun traducirTextos(textos: List<String>) {
        val aIngles = Idioma.ingles
        if (traducirAuto != "si") {
            textos.forEach { t -> traductor.enCache(t, aIngles)?.let { traducciones[claveTr(t)] = it } }
            return
        }
        for (t in textos.distinct()) {
            if (claveTr(t) in traducciones) continue
            traductor.traducir(t, aIngles)?.let { traducciones[(if (aIngles) "en|" else "") + t] = it }
        }
        withContext(Dispatchers.IO) { traductor.guardar() }
    }

    private suspend fun traducirTitulos(r: ResultadoEscaneo) {
        val titulos = r.modulos.flatMap { m -> m.dtcs.map { repo.buscar(it, m.categoria, m.nombre) } }
            .filter { it.titulo in it.ajenos }.map { it.titulo }
        traducirTextos(titulos)
    }

    private fun traducirDetalle(p: Pantalla.Detalle) {
        val (m, d) = buscarDtc(p) ?: return
        val info = repo.buscar(d, m.categoria, m.nombre)
        if (info.ajenos.isEmpty()) return
        viewModelScope.launch { traducirTextos(info.ajenos) }
    }

    fun buscarDtc(p: Pantalla.Detalle): Pair<ResultadoModulo, Dtc>? {
        val m = resultado?.modulos?.firstOrNull { it.clave == p.claveModulo } ?: return null
        val d = m.dtcs.firstOrNull { it.codigo == p.codigo } ?: return null
        return m to d
    }

    fun guardarClave(clave: String?) {
        almacenClave.guardar(clave)
        tieneClave = !clave.isNullOrBlank()
        mensaje = if (tieneClave) "API key guardada (cifrada en este teléfono)." else "API key eliminada."
    }

    fun explicarConIa(m: ResultadoModulo, d: Dtc) {
        val clave = almacenClave.leer()
        if (clave == null) {
            mensaje = "Para usar la explicación con IA cargá tu API key en Ajustes."
            return
        }
        val id = m.clave + "|" + d.codigo
        if (iaCargando != null) return
        iaCargando = id
        viewModelScope.launch {
            val info = repo.buscar(d, m.categoria, m.nombre)
            val contexto = buildString {
                appendLine("Dato crudo: ${d.crudoHex} (en formato SAE sería ${d.formatoSae}). Estado: ${d.estado.texto()}.")
                appendLine("Detalle del módulo: ${m.detalle}.")
                if (info.fuente == FuenteInfo.CURADA || info.fuente == FuenteInfo.OBDEX) appendLine("Descripción genérica de la norma: ${info.titulo}.")
                if (info.mercedes.isNotEmpty()) {
                    appendLine("Según listas del DAS de Mercedes de autos de la misma época (no necesariamente iguales en el W204):")
                    info.mercedes.forEach { appendLine("- ${it.modulo} (${it.origen}): ${it.texto}") }
                } else if (info.fuente == FuenteInfo.NINGUNA) {
                    appendLine("No tengo descripción local: es un código propio de ${Auto.marcaCorta} para ese módulo.")
                }
                val otros = resultado?.modulos.orEmpty().flatMap { x -> x.dtcs.map { "${x.nombre}: ${it.codigo}" } }
                    .filter { !it.endsWith(d.codigo) }.take(15)
                if (otros.isNotEmpty()) appendLine("Otros códigos presentes en el auto: ${otros.joinToString("; ")}.")
            }
            try {
                explicaciones[id] = ClaudeClient.explicar(clave, d.codigo, Idioma.L(m.nombre), contexto, Idioma.ingles)
            } catch (e: IllegalStateException) {
                mensaje = e.message
            } catch (e: Exception) {
                mensaje = "No se pudo consultar a la IA: ${e.message}"
            } finally {
                iaCargando = null
            }
        }
    }

    // ── Actualizaciones y novedades (mismo sistema que MTG Reforged) ─────────
    private val actualizador = Actualizador(app)
    private var versionNueva: Actualizador.Version? = null
    var actualizacion by mutableStateOf<EstadoActualizacion>(EstadoActualizacion.Nada); private set
    val novedades = mutableStateListOf<Novedades.Mensaje>()
    private var vigilandoBuild: Job? = null

    init {
        viewModelScope.launch(Dispatchers.IO) {
            val pendientes = Novedades.pendientes(app)
            withContext(Dispatchers.Main) { novedades.clear(); novedades.addAll(pendientes) }
        }
        buscarActualizacion()
    }

    /** Se llama cada vez que la app vuelve a primer plano. */
    fun buscarActualizacion() {
        if (actualizacion is EstadoActualizacion.Bajando) return
        viewModelScope.launch { consultar(builds = false) }
    }

    /** El botón "Buscar actualizaciones" de Ajustes: también avisa si hay una versión compilándose. */
    fun buscarActualizacionAhora() {
        if (actualizacion is EstadoActualizacion.Bajando) return
        viewModelScope.launch {
            actualizacion = EstadoActualizacion.Buscando
            if (consultar(builds = true) == EstadoActualizacion.Nada) actualizacion = EstadoActualizacion.AlDia
        }
    }

    /**
     * Un release publicado o, solo si el usuario lo pidió ([builds]), una versión que todavía se está
     * compilando. Mientras compila vuelve a mirar cada minuto hasta que sale el release.
     */
    private suspend fun consultar(builds: Boolean): EstadoActualizacion {
        versionNueva = runCatching { actualizador.buscar() }.getOrNull()
        val encontrado = versionNueva?.let { EstadoActualizacion.Disponible(it.nombre) }
            ?: (if (builds) runCatching { BuildsEnCurso.actual() }.getOrNull() else null)?.let { EstadoActualizacion.Compilando(it) }
            ?: EstadoActualizacion.Nada
        actualizacion = encontrado
        if (encontrado is EstadoActualizacion.Compilando && vigilandoBuild?.isActive != true) {
            vigilandoBuild = viewModelScope.launch {
                while (actualizacion is EstadoActualizacion.Compilando) {
                    delay(60_000L)
                    if (actualizacion !is EstadoActualizacion.Compilando) break
                    consultar(builds = true)
                }
            }
        }
        return encontrado
    }

    fun instalarActualizacion() {
        val v = versionNueva ?: return
        if (!actualizador.puedeInstalar()) {
            mensaje = "Para actualizar, permití que Gappribaldi instale apps y volvé a tocar Actualizar."
            actualizador.abrirPermisoDeInstalacion()
            return
        }
        viewModelScope.launch {
            try {
                val apk = actualizador.bajar(v) { actualizacion = EstadoActualizacion.Bajando(it) }
                actualizador.instalar(apk)
                actualizacion = EstadoActualizacion.Disponible(v.nombre)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                actualizacion = EstadoActualizacion.Fallo(e.message ?: "error")
            }
        }
    }

    fun cerrarNovedad(m: Novedades.Mensaje) {
        Novedades.marcarVisto(getApplication(), m)
        novedades.remove(m)
    }

    override fun onCleared() {
        desconectar()
    }
}

sealed interface EstadoActualizacion {
    data object Nada : EstadoActualizacion
    data object Buscando : EstadoActualizacion
    data object AlDia : EstadoActualizacion
    data class Disponible(val nombre: String) : EstadoActualizacion
    data class Bajando(val progreso: Float) : EstadoActualizacion
    data class Compilando(val build: BuildEnCurso) : EstadoActualizacion
    data class Fallo(val texto: String) : EstadoActualizacion
}

/** Archivo del mapa de módulos de cada marca. */
private fun archivoModulos(m: Marca): String =
    if (m.nombre == Auto.MERCEDES) "modulos.json" else "modulos-" + m.nombre.lowercase().replace(Regex("[^a-z0-9]"), "") + ".json"
