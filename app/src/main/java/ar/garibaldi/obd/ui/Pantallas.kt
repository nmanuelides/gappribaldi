package ar.garibaldi.obd.ui

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.foundation.Canvas
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import ar.garibaldi.obd.R
import ar.garibaldi.obd.auto.Auto
import ar.garibaldi.obd.i18n.Idioma
import ar.garibaldi.obd.dtc.FuenteInfo
import ar.garibaldi.obd.dtc.Gravedad
import ar.garibaldi.obd.modules.Categoria
import ar.garibaldi.obd.scan.ResultadoEscaneo
import ar.garibaldi.obd.scan.ResultadoModulo
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun Dialogo(
    titulo: String,
    texto: String,
    confirmar: String,
    alConfirmar: () -> Unit,
    alCancelar: () -> Unit,
    cancelar: String? = "Cancelar",
    extra: (@Composable ColumnScope.() -> Unit)? = null,
) {
    AlertDialog(
        onDismissRequest = alCancelar,
        containerColor = Tinta.TarjetaAlta,
        shape = RoundedCornerShape(18.dp),
        title = { Text(titulo, style = MaterialTheme.typography.titleLarge, color = Tinta.Texto) },
        text = {
            Column(
                Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                if (texto.isNotEmpty()) Text(texto, style = MaterialTheme.typography.bodyMedium, color = Tinta.Texto)
                extra?.invoke(this)
            }
        },
        confirmButton = { BotonAcento(confirmar, alHacerClic = alConfirmar) },
        dismissButton = cancelar?.let { { BotonOscuro(it, alHacerClic = alCancelar) } },
    )
}

@Composable
fun Lista(contenido: @Composable ColumnScope.() -> Unit) {
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp).padding(bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        content = contenido,
    )
}

/** Como [Lista], pero si el contenido entra en la pantalla queda apoyado contra el borde de abajo. */
@Composable
fun ListaAbajo(contenido: @Composable ColumnScope.() -> Unit) {
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val alto = maxHeight
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
            Column(
                Modifier.fillMaxWidth().heightIn(min = alto).padding(horizontal = 16.dp).padding(bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.Bottom),
                content = contenido,
            )
        }
    }
}

fun fechaCorta(ms: Long): String = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.US).format(Date(ms))

// ── Inicio ───────────────────────────────────────────────────────────────────

/**
 * Portada: el nombre del modelo en letras gigantes con degradé, de borde a borde de la pantalla,
 * y el auto por delante tapándolo en parte (como en el diseño de referencia).
 */
@Composable
private fun Portada(vm: AppViewModel) {
    val marca = LocalMarca.current
    val foto = remember(vm.fotoAuto, vm.versionFoto) {
        vm.fotoAuto?.let { f ->
            val limites = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(f.path, limites)
            var muestra = 1
            while (limites.outWidth / muestra > 1600) muestra *= 2
            BitmapFactory.decodeFile(f.path, BitmapFactory.Options().apply { inSampleSize = muestra })?.asImageBitmap()
        }
    }
    val densidad = LocalDensity.current
    BoxWithConstraints(
        // La lista tiene margen lateral: la portada lo ignora para ocupar todo el ancho
        Modifier.layout { medible, limites ->
            val extra = 32.dp.roundToPx()
            val ancho = limites.maxWidth + extra
            val p = medible.measure(limites.copy(minWidth = ancho, maxWidth = ancho))
            layout(limites.maxWidth, p.height) { p.place(-extra / 2, 0) }
        }
    ) {
        val anchoPx = with(densidad) { maxWidth.toPx() }
        // Se mide el contorno real de las letras (sin los márgenes propios de la tipografía)
        // y se escala para que la tinta toque exactamente los dos bordes de la pantalla.
        val contexto = LocalContext.current
        val pincel = remember(contexto) {
            android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                typeface = androidx.core.content.res.ResourcesCompat.getFont(contexto, R.font.anton)
                textSize = 100f
            }
        }
        val contorno = remember(vm.modelo, pincel) {
            android.graphics.Rect().also { pincel.textSize = 100f; pincel.getTextBounds(vm.modelo, 0, vm.modelo.length, it) }
        }
        val escala = anchoPx / contorno.width().coerceAtLeast(1)
        val altoTintaPx = contorno.height() * escala
        val altoTexto = with(densidad) { altoTintaPx.toDp() }
        Canvas(Modifier.align(Alignment.TopStart).fillMaxWidth().height(altoTexto)) {
            pincel.textSize = 100f * escala
            pincel.shader = android.graphics.LinearGradient(
                0f, 0f, 0f, altoTintaPx, marca.claro.toArgb(), marca.oscuro.toArgb(), android.graphics.Shader.TileMode.CLAMP,
            )
            drawContext.canvas.nativeCanvas.drawText(
                vm.modelo, -contorno.left * escala, -contorno.top * escala, pincel,
            )
        }
        // El auto un poco más chico que la pantalla para que se vea entero; el texto queda por detrás
        Box(
            Modifier.align(Alignment.TopCenter).padding(top = altoTexto * 0.5f).fillMaxWidth()
                // Resplandor del color de la marca detrás del auto, para que un auto oscuro se despegue del fondo
                .drawBehind {
                    drawOval(
                        Brush.radialGradient(
                            listOf(marca.claro.copy(alpha = 0.45f), marca.oscuro.copy(alpha = 0.25f), Color.Transparent),
                            center = Offset(size.width / 2f, size.height * 0.62f),
                            radius = size.width * 0.55f,
                        ),
                        topLeft = Offset(0f, size.height * 0.25f),
                        size = Size(size.width, size.height * 0.75f),
                    )
                },
            contentAlignment = Alignment.TopCenter,
        ) {
            val modImagen = Modifier.fillMaxWidth(0.94f)
            val fotoMarca = marca.fotoDe(vm.modelo)
            if (foto != null) {
                Image(foto, "Foto del auto", modImagen, contentScale = ContentScale.FillWidth)
            } else if (fotoMarca != null) {
                Image(painterResource(fotoMarca), "${marca.nombre} ${vm.modelo}", modImagen, contentScale = ContentScale.FillWidth)
            }
        }
    }
}

@Composable
fun PantallaInicio(vm: AppViewModel) {
    var elegirAdaptador by remember { mutableStateOf(false) }
    var avisoEscaneo by remember { mutableStateOf(false) }
    val contexto = LocalContext.current
    val pedirPermiso = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
        if (ok) elegirAdaptador = true
        else vm.mensaje = "Sin el permiso de \"Dispositivos cercanos\" no se puede usar el Bluetooth. Podés darlo desde los ajustes del teléfono."
    }

    fun abrirAdaptadores() {
        val falta = Build.VERSION.SDK_INT >= 31 &&
            ContextCompat.checkSelfPermission(contexto, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED
        if (falta) pedirPermiso.launch(Manifest.permission.BLUETOOTH_CONNECT) else elegirAdaptador = true
    }

    ListaAbajo {
        BannerActualizacion(vm)
        Portada(vm)
        when (val c = vm.conexion) {
            is Conexion.Conectado -> {
                Tarjeta(alHacerClic = { vm.ir(Pantalla.Conexion) }) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Punto(if (c.info.busOk) Tinta.Verde else Tinta.Amarillo)
                        Text(if (c.demo) "Modo demostración" else "Conectado a ${c.nombre}", style = MaterialTheme.typography.titleMedium)
                    }
                    Suave(c.info.detalleBus)
                    c.info.voltaje?.let { Suave("Batería: $it") }
                }
                BotonAcento("Escanear el auto  →", Modifier.fillMaxWidth(), habilitado = vm.trabajo == null) { avisoEscaneo = true }
                FilaBotones {
                    BotonOscuro("Datos en vivo", Modifier.weight(1f)) { vm.ir(Pantalla.EnVivo) }
                    BotonOscuro("Emisiones", Modifier.weight(1f)) { vm.ir(Pantalla.Emisiones) }
                }
                FilaBotones {
                    BotonOscuro("Módulos", Modifier.weight(1f)) { vm.ir(Pantalla.Modulos) }
                    BotonOscuro("Desconectar", Modifier.weight(1f)) { vm.desconectar() }
                }
            }
            is Conexion.Conectando -> Tarjeta { FilaCargando(c.texto) }
            else -> {
                if (c is Conexion.Error) {
                    Tarjeta {
                        Text("No se pudo conectar", style = MaterialTheme.typography.titleMedium, color = Tinta.Rojo)
                        Suave(c.texto)
                        Suave("Revisá que el OBDLink esté enchufado, con el contacto puesto, y que ninguna otra app lo esté usando.")
                    }
                } else {
                    Text(
                        "Enchufá el OBDLink MX+ al auto, poné el contacto y conectate para leer los códigos de falla.",
                        style = MaterialTheme.typography.bodyMedium, color = Tinta.TextoSuave,
                        textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp),
                    )
                }
                BotonOscuro("Probar sin el auto (modo demo)", Modifier.fillMaxWidth()) { vm.conectarDemo() }
            }
        }
        vm.resultado?.let { r ->
            Tarjeta(alHacerClic = { vm.ir(Pantalla.Resultado) }) {
                Text("Último escaneo", style = MaterialTheme.typography.titleMedium)
                Suave("${fechaCorta(r.fecha)} · ${r.totalCodigos} códigos en ${r.modulos.size} módulos")
            }
        }
        FilaBotones {
            BotonOscuro("Historial", Modifier.weight(1f)) { vm.ir(Pantalla.HistorialLista) }
            BotonOscuro("Registro", Modifier.weight(1f)) { vm.ir(Pantalla.Registro) }
        }
        // Sin conexión, la acción principal queda abajo de todo, a mano del pulgar
        val c = vm.conexion
        if (c !is Conexion.Conectado && c !is Conexion.Conectando) {
            BotonAcento("Conectar al adaptador  →", Modifier.fillMaxWidth()) { abrirAdaptadores() }
        }
    }

    if (elegirAdaptador) DialogoAdaptadores(vm) { elegirAdaptador = false }
    // Novedades del desarrollador: de a una, cada una se muestra una sola vez
    if (vm.trabajo == null && !elegirAdaptador) vm.novedades.firstOrNull()?.let { m -> DialogoNovedad(vm, m) }
    if (avisoEscaneo) {
        val voltaje = (vm.conexion as? Conexion.Conectado)?.info?.voltaje
        val bajo = voltaje?.filter { it.isDigit() || it == '.' }?.toDoubleOrNull()?.let { it < 11.8 } == true
        Dialogo(
            titulo = "Antes de escanear",
            texto = "• Motor apagado y contacto en posición 2 (o motor en marcha).\n" +
                "• La batería tiene que estar bien: con poca carga aparecen fallas falsas.\n" +
                "• No apagues el contacto ni desenchufes el adaptador hasta que termine." +
                (if (vm.modulos.isEmpty()) "\n\nEs la primera vez: primero se buscan los módulos del auto (tarda unos segundos)." else "") +
                (if (bajo) "\n\n⚠ La batería marca $voltaje: está baja. Conviene cargarla o poner el motor en marcha." else ""),
            confirmar = "Escanear",
            alConfirmar = { avisoEscaneo = false; vm.escanear() },
            alCancelar = { avisoEscaneo = false },
        )
    }
}

@SuppressLint("MissingPermission")
@Composable
private fun DialogoAdaptadores(vm: AppViewModel, cerrar: () -> Unit) {
    val contexto = LocalContext.current
    val bt = remember { contexto.getSystemService(BluetoothManager::class.java)?.adapter }
    val dispositivos: List<BluetoothDevice> = remember {
        try {
            bt?.bondedDevices.orEmpty().sortedBy { d ->
                val n = d.name.orEmpty()
                when {
                    d.address == vm.ultimoDispositivo -> 0
                    n.contains("OBD", true) -> 1
                    else -> 2
                }
            }
        } catch (_: SecurityException) {
            emptyList()
        }
    }
    val texto = when {
        bt == null -> "Este teléfono no tiene Bluetooth."
        !bt.isEnabled -> "El Bluetooth está apagado. Prendelo y volvé a entrar."
        dispositivos.isEmpty() -> "No hay dispositivos emparejados."
        else -> "Elegí el adaptador:"
    }
    Dialogo(
        titulo = "Adaptador OBD",
        texto = texto,
        confirmar = "Ajustes de Bluetooth",
        alConfirmar = {
            cerrar()
            try {
                contexto.startActivity(Intent(Settings.ACTION_BLUETOOTH_SETTINGS))
            } catch (_: Exception) {
            }
        },
        alCancelar = cerrar,
        cancelar = "Cerrar",
    ) {
        if (bt?.isEnabled == true) {
            for (d in dispositivos) {
                Tarjeta(alHacerClic = { cerrar(); vm.conectarBluetooth(d) }, modifier = Modifier.border(1.dp, Tinta.Borde, RoundedCornerShape(16.dp))) {
                    Text(d.name ?: "Sin nombre", style = MaterialTheme.typography.titleMedium)
                    Suave(d.address + if (d.address == vm.ultimoDispositivo) " · último usado" else "")
                }
            }
        }
        Suave("¿No aparece el OBDLink MX+? Apretá el botón del adaptador (la luz azul parpadea rápido) y emparejalo desde los ajustes de Bluetooth del teléfono.")
    }
}

// ── Resultado ────────────────────────────────────────────────────────────────

private fun peorGravedad(vm: AppViewModel, r: ResultadoEscaneo): Gravedad? =
    r.modulos.flatMap { m -> m.dtcs.map { vm.info(it, m.categoria, m.nombre).gravedad } }.maxByOrNull { it.ordinal }

@Composable
fun PantallaResultado(vm: AppViewModel) {
    val r = vm.resultado
    if (r == null) {
        Lista { Suave("Todavía no hay ningún escaneo.") }
        return
    }
    val contexto = LocalContext.current
    var borrar by remember { mutableStateOf<String?>(null) } // clave del módulo, o "*" para todos
    val puedeBorrar = vm.resultadoEsActual && vm.conectado && vm.trabajo == null
    val peor = peorGravedad(vm, r)

    Lista {
        Tarjeta {
            Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    r.totalCodigos.toString(),
                    style = TextStyle(brush = LocalMarca.current.degradeVertical, fontFamily = Titular, fontSize = 64.sp, lineHeight = 64.sp),
                )
                Column(Modifier.padding(bottom = 10.dp)) {
                    Text(if (r.totalCodigos == 1) "código de falla" else "códigos de falla", style = MaterialTheme.typography.titleMedium)
                    Suave(fechaCorta(r.fecha) + if (vm.resultadoEsActual) "" else " · del historial")
                }
            }
            if (peor != null) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Punto(peor.color())
                    Text("Lo más grave: ${peor.titulo}", style = MaterialTheme.typography.bodyMedium, color = peor.color())
                }
                Suave(peor.consejo)
            } else if (r.modulos.none { it.error != null }) {
                Text("No se encontraron fallas guardadas.", style = MaterialTheme.typography.bodyMedium, color = Tinta.Verde)
            }
            r.testigoMotor?.let { Suave("Testigo de motor: " + if (it) "encendido" else "apagado") }
            r.vin?.let { Suave("Chasis: $it") }
            r.voltaje?.let { Suave("Batería: $it") }
        }

        val hayAjenos = r.modulos.any { m -> m.dtcs.any { vm.info(it, m.categoria, m.nombre).ajenos.isNotEmpty() } }
        if (hayAjenos && vm.traducirAuto == null) {
            Tarjeta {
                // En inglés lo que queda en otro idioma son las notas en español de las fichas curadas
                Text(
                    if (Idioma.ingles) "Algunas notas están en español" else "Descripciones en inglés",
                    style = MaterialTheme.typography.titleMedium,
                )
                Suave(
                    if (Idioma.ingles) "Algunas notas solo están en español. Se pueden traducir en el teléfono bajando, una sola vez, un paquete de unos 30 MB."
                    else "Algunos códigos solo tienen descripción en inglés. Se pueden traducir en el teléfono bajando, una sola vez, un paquete de unos 30 MB."
                )
                FilaBotones {
                    BotonOscuro("Ahora no", Modifier.weight(1f)) { vm.decidirTraduccion(false) }
                    BotonAcento("Bajar traducción", Modifier.weight(1f)) { vm.decidirTraduccion(true) }
                }
            }
        }
        if (vm.bajandoTraduccion) Tarjeta { FilaCargando("Bajando el paquete de traducción…") }

        for (m in r.modulos) {
            TarjetaModulo(vm, m, puedeBorrar) { borrar = m.clave }
        }

        if (r.monitoresPendientes.isNotEmpty()) {
            Tarjeta {
                Text("Monitores de emisiones sin completar", style = MaterialTheme.typography.titleMedium)
                Suave(r.monitoresPendientes.joinToString(" · "))
                Suave("Es normal después de borrar códigos o desconectar la batería: se completan solos andando unos días.")
            }
        }
        FilaBotones {
            BotonOscuro("Compartir informe", Modifier.weight(1f)) {
                vm.informe()?.let { compartirTexto(contexto, "Diagnóstico del auto", it) }
            }
            if (puedeBorrar && r.totalCodigos > 0) {
                BotonOscuro("Borrar todo", Modifier.weight(1f), colorTexto = Tinta.Rojo) { borrar = "*" }
            }
        }
        if (vm.resultadoEsActual && vm.conectado) {
            BotonAcento("Volver a escanear", Modifier.fillMaxWidth(), habilitado = vm.trabajo == null) { vm.escanear() }
        }
    }

    borrar?.let { clave ->
        val objetivos = if (clave == "*") r.modulos else r.modulos.filter { it.clave == clave }
        val delicado = objetivos.any { it.dtcs.isNotEmpty() && (it.categoria.seguridad || it.categoria == Categoria.DESCONOCIDO) }
        Dialogo(
            titulo = if (clave == "*") "¿Borrar todos los códigos?" else "¿Borrar los códigos de ${objetivos.firstOrNull()?.nombre}?",
            texto = "Borrar no arregla la causa: si la falla sigue, el código va a volver.\n\n" +
                "Antes de borrar conviene compartir o anotar el informe, porque los códigos guardados son la pista para el mecánico." +
                (if (objetivos.any { it.esObd }) "\n\nAl borrar los códigos de emisiones también se reinician los monitores del motor." else ""),
            confirmar = "Sí, borrar",
            alConfirmar = { borrar = null; vm.borrar(clave.takeIf { it != "*" }) },
            alCancelar = { borrar = null },
        ) {
            if (delicado) {
                Tarjeta(Modifier.border(1.dp, Tinta.Rojo, RoundedCornerShape(16.dp))) {
                    Text("Atención: seguridad", style = MaterialTheme.typography.titleMedium, color = Tinta.Rojo)
                    Text(
                        "Hay códigos en módulos de airbag, frenos/ABS/ESP o en módulos sin identificar. " +
                            "Una falla ahí puede significar que el sistema no te va a proteger. Hacelo revisar antes de borrar.",
                        style = MaterialTheme.typography.bodySmall, color = Tinta.Texto,
                    )
                }
            }
        }
    }
}

@Composable
private fun TarjetaModulo(vm: AppViewModel, m: ResultadoModulo, puedeBorrar: Boolean, alBorrar: () -> Unit) {
    Tarjeta {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(m.nombre, style = MaterialTheme.typography.titleMedium)
                Suave(m.detalle)
            }
            when {
                m.error != null -> Etiqueta("SIN LEER", Tinta.Amarillo)
                m.dtcs.isEmpty() -> Etiqueta("SIN FALLAS", Tinta.Verde)
                else -> Etiqueta("${m.dtcs.size}", LocalMarca.current.claro)
            }
        }
        m.error?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = Tinta.Amarillo) }
        for (d in m.dtcs) {
            val info = vm.info(d, m.categoria, m.nombre)
            val nuevo = vm.clavesAnteriores?.let { (m.clave + "|" + d.codigo) !in it } == true
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp))
                    .clickable { vm.ir(Pantalla.Detalle(m.clave, d.codigo)) }
                    .drawBehind { drawRect(Tinta.TarjetaAlta) }
                    .padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Punto(info.gravedad.color(), 12)
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(d.codigo, style = MaterialTheme.typography.titleLarge)
                        if (nuevo) Etiqueta("NUEVO", LocalMarca.current.claro)
                    }
                    Text(
                        if (info.fuente == FuenteInfo.NINGUNA) "Código propio de ${Auto.marcaCorta} · tocá para ver opciones" else vm.tr(info.titulo),
                        style = MaterialTheme.typography.bodyMedium, color = Tinta.Texto,
                    )
                    Suave(d.estado.texto())
                }
                Text("›", style = MaterialTheme.typography.headlineSmall, color = Tinta.TextoSuave)
            }
        }
        if (puedeBorrar && m.dtcs.isNotEmpty()) {
            Spacer(Modifier.height(2.dp))
            BotonOscuro("Borrar códigos de este módulo", Modifier.fillMaxWidth(), colorTexto = Tinta.Rojo, alHacerClic = alBorrar)
        }
    }
}

// ── Detalle de un código ─────────────────────────────────────────────────────

@Composable
fun PantallaDetalle(vm: AppViewModel, p: Pantalla.Detalle) {
    val par = vm.buscarDtc(p)
    if (par == null) {
        Lista { Suave("Ese código ya no está en el resultado.") }
        return
    }
    val (m, d) = par
    val info = vm.info(d, m.categoria, m.nombre)
    val contexto = LocalContext.current
    val marca = LocalMarca.current
    val idIa = m.clave + "|" + d.codigo

    Lista {
        Text(
            d.codigo,
            style = TextStyle(brush = marca.degradeVertical, fontFamily = Titular, fontSize = 84.sp, lineHeight = 88.sp),
            modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center, maxLines = 1,
        )
        Text(
            if (info.fuente == FuenteInfo.NINGUNA) info.titulo else vm.tr(info.titulo),
            style = MaterialTheme.typography.headlineSmall, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center,
        )
        Suave("${m.nombre} · ${d.estado.texto()}", Modifier.fillMaxWidth().padding(horizontal = 8.dp))

        Tarjeta(Modifier.border(1.dp, info.gravedad.color().copy(alpha = 0.6f), RoundedCornerShape(16.dp))) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Punto(info.gravedad.color(), 14)
                Text(info.gravedad.titulo, style = MaterialTheme.typography.titleLarge, color = info.gravedad.color())
                if (info.gravedadEstimada) Etiqueta("ESTIMADA", Tinta.TextoSuave)
            }
            Text(info.gravedad.consejo, style = MaterialTheme.typography.bodyMedium)
            if (info.gravedadEstimada) Suave("La gravedad se calculó con reglas generales; tomala como orientación.")
        }

        if (info.mercedes.isNotEmpty()) TarjetaMercedes(vm, info, d.crudoHex)
        if (info.fuente == FuenteInfo.NINGUNA) {
            Tarjeta {
                Text("Sin descripción local", style = MaterialTheme.typography.titleMedium)
                Text(
                    "Este código es propio de ${Auto.marcaCorta} para este módulo y su significado exacto solo figura en la " +
                        "documentación del fabricante. No lo vamos a adivinar. Podés buscarlo en la web o pedirle una " +
                        "explicación orientativa a la IA.",
                    style = MaterialTheme.typography.bodyMedium,
                )
                Suave("Dato crudo: ${d.crudoHex} · en formato SAE: ${d.formatoSae}")
            }
        }
        info.explicacion?.let {
            Seccion("Qué significa")
            Text(vm.tr(it), style = MaterialTheme.typography.bodyMedium)
        }
        info.notaMotor?.let {
            Tarjeta(Modifier.border(1.dp, marca.claro.copy(alpha = 0.7f), RoundedCornerShape(16.dp))) {
                Etiqueta("FRECUENTE EN ESTE AUTO", marca.claro)
                Text(it, style = MaterialTheme.typography.bodyMedium)
            }
        }
        if (info.causas.isNotEmpty()) {
            Seccion("Causas probables")
            info.causas.forEachIndexed { i, c ->
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("${i + 1}", style = MaterialTheme.typography.titleMedium, color = marca.claro, modifier = Modifier.width(18.dp))
                    Column(Modifier.weight(1f)) {
                        Text(vm.tr(c.texto), style = MaterialTheme.typography.bodyMedium)
                        c.probabilidad?.let { Suave("Probabilidad $it") }
                    }
                }
            }
        }
        if (info.sintomas.isNotEmpty()) {
            Seccion("Qué podés notar")
            info.sintomas.forEach { Text("• " + vm.tr(it), style = MaterialTheme.typography.bodyMedium) }
        }
        if (info.queRevisar.isNotEmpty()) {
            Seccion("Qué revisar")
            info.queRevisar.forEach { Text("• $it", style = MaterialTheme.typography.bodyMedium) }
        }
        when {
            info.fuente == FuenteInfo.OBDEX && vm.tr(info.titulo) != info.titulo ->
                Suave("Traducción automática de la base abierta OBDex: puede tener errores. Título original: ${info.titulo}")
            info.fuente == FuenteInfo.OBDEX -> Suave("Descripción en inglés de la base abierta OBDex.")
            else -> Unit
        }
        if (d.claveGenerica != null && d.origen != ar.garibaldi.obd.dtc.OrigenDtc.OBD_CONFIRMADO && !m.esObd && m.categoria != Categoria.MOTOR && m.categoria != Categoria.CAJA) {
            Suave("Ojo: es la descripción genérica de la norma para ${d.formatoSae}. Este módulo podría usarlo con un matiz distinto.")
        }

        Seccion("¿Querés saber más?")
        val explicacion = vm.explicaciones[idIa]
        if (explicacion != null) {
            Tarjeta {
                Etiqueta("EXPLICACIÓN DE LA IA · ORIENTATIVA", marca.claro)
                Text(explicacion, style = MaterialTheme.typography.bodyMedium)
                Suave("Generada por Claude. Puede equivocarse: confirmá con un mecánico antes de gastar plata.")
            }
        } else if (vm.iaCargando == idIa) {
            Tarjeta { FilaCargando("Consultando a la IA…") }
        }
        FilaBotones {
            BotonOscuro("Buscar en la web", Modifier.weight(1f)) {
                buscarEnLaWeb(contexto, "${if (Auto.esMercedes) "Mercedes W204 C350" else Auto.descripcion} ${m.nombre.substringBefore(" (")} código ${d.codigo}")
            }
            if (explicacion == null) {
                BotonAcento("Explicame este error", Modifier.weight(1f), habilitado = vm.iaCargando == null) {
                    if (vm.tieneClave) vm.explicarConIa(m, d) else vm.ir(Pantalla.Ajustes)
                }
            }
        }
        if (!vm.tieneClave) Suave("La explicación con IA es opcional: necesita una API key de Anthropic, que se carga en Ajustes.")
        if (info.relacionados.isNotEmpty()) Suave("Códigos relacionados: ${info.relacionados.joinToString()}")
    }
}

/** Lo que dicen las listas del DAS de Mercedes sobre el código, con de dónde sale cada texto. */
@Composable
private fun TarjetaMercedes(vm: AppViewModel, info: ar.garibaldi.obd.dtc.InfoDtc, crudo: String) {
    val marca = LocalMarca.current
    Tarjeta(Modifier.border(1.dp, marca.claro.copy(alpha = 0.7f), RoundedCornerShape(16.dp))) {
        Etiqueta(if (info.mercedes.size > 1) "SIGNIFICADOS POSIBLES SEGÚN MERCEDES" else "TEXTO DE MERCEDES", marca.claro)
        if (info.mercedes.size > 1) {
            Suave("No sabemos qué módulo es, así que el mismo número puede significar cosas distintas. Ponele nombre al módulo (en Módulos) para quedarte con el que corresponde.")
        }
        for (t in info.mercedes) {
            if (info.mercedes.size > 1) Text(t.modulo, style = MaterialTheme.typography.titleMedium)
            Text(vm.tr(t.texto), style = MaterialTheme.typography.bodyMedium)
            if (t.evento) Suave("Es un evento: un registro informativo, no una falla.")
        }
        val origen = info.mercedes.first()
        Suave(
            if (origen.foro) "Fuente: foros y talleres especializados en el W204. Código $crudo."
            else "Fuente: lista de diagnóstico de Mercedes (DAS) de un ${origen.origen}, de la misma época que el W204. " +
                "En tu auto el texto puede variar. Código $crudo."
        )
        if (vm.tr(origen.texto) != origen.texto) Suave("Traducción automática del original en inglés: puede tener errores.")
    }
}

// ── Actualizaciones y novedades ──────────────────────────────────────────────

/** Aviso arriba de la portada cuando hay una versión nueva (como en MTG Reforged). */
@Composable
fun BannerActualizacion(vm: AppViewModel) {
    when (val a = vm.actualizacion) {
        is EstadoActualizacion.Disponible -> Tarjeta(Modifier.border(1.dp, LocalMarca.current.claro, RoundedCornerShape(16.dp))) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Column(Modifier.weight(1f)) {
                    Text("Versión nueva disponible", style = MaterialTheme.typography.titleMedium)
                    Suave(a.nombre)
                }
                BotonAcento("Actualizar") { vm.instalarActualizacion() }
            }
        }
        is EstadoActualizacion.Bajando -> Tarjeta {
            Text("Bajando la actualización…", style = MaterialTheme.typography.bodyMedium)
            androidx.compose.material3.LinearProgressIndicator(
                progress = { a.progreso }, modifier = Modifier.fillMaxWidth(),
                color = LocalMarca.current.claro, trackColor = Tinta.Borde,
            )
        }
        is EstadoActualizacion.Fallo -> Tarjeta {
            Text("No se pudo actualizar: ${a.texto}", style = MaterialTheme.typography.bodySmall, color = Tinta.Rojo)
        }
        else -> Unit // "compilando" y "al día" se ven solo en Ajustes
    }
}

@Composable
private fun DialogoNovedad(vm: AppViewModel, m: ar.garibaldi.obd.update.Novedades.Mensaje) {
    val uri = androidx.compose.ui.platform.LocalUriHandler.current
    val titulo = if (Idioma.ingles) m.tituloEn ?: m.titulo else m.titulo
    val cuerpo = if (Idioma.ingles) m.cuerpoEn ?: m.cuerpo else m.cuerpo
    // El texto ya viene en el idioma elegido: se dibuja sin pasar por el diccionario
    AlertDialog(
        onDismissRequest = { vm.cerrarNovedad(m) },
        containerColor = Tinta.TarjetaAlta,
        shape = RoundedCornerShape(18.dp),
        title = {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Etiqueta("NOVEDADES", LocalMarca.current.claro)
                if (titulo.isNotBlank()) androidx.compose.material3.Text(titulo, style = MaterialTheme.typography.titleLarge, color = Tinta.Texto)
            }
        },
        text = {
            androidx.compose.material3.Text(
                cuerpo, style = MaterialTheme.typography.bodyMedium, color = Tinta.Texto,
                modifier = Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState()),
            )
        },
        confirmButton = { BotonAcento("OK") { vm.cerrarNovedad(m) } },
        dismissButton = m.link?.let { link -> { BotonOscuro("Abrir link") { runCatching { uri.openUri(link) } } } },
    )
}
