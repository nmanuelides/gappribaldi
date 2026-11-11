package ar.garibaldi.obd.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ar.garibaldi.obd.BuildConfig
import ar.garibaldi.obd.ai.ClaudeClient
import ar.garibaldi.obd.modules.CatalogoModulos
import ar.garibaldi.obd.modules.Modulo
import ar.garibaldi.obd.obd.Obd2

@Composable
private fun Campo(valor: String, alCambiar: (String) -> Unit, etiqueta: String, secreto: Boolean = false, mayusculas: Boolean = false) {
    val marca = LocalMarca.current
    OutlinedTextField(
        value = valor, onValueChange = alCambiar, singleLine = true,
        label = { Text(etiqueta, style = MaterialTheme.typography.labelMedium) },
        visualTransformation = if (secreto) PasswordVisualTransformation() else androidx.compose.ui.text.input.VisualTransformation.None,
        keyboardOptions = KeyboardOptions(capitalization = if (mayusculas) KeyboardCapitalization.Characters else KeyboardCapitalization.None),
        textStyle = MaterialTheme.typography.bodyLarge,
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = marca.claro, unfocusedBorderColor = Tinta.Borde,
            focusedLabelColor = marca.claro, cursorColor = marca.claro,
        ),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth(),
    )
}

// ── Módulos ──────────────────────────────────────────────────────────────────

@Composable
fun PantallaModulos(vm: AppViewModel) {
    var editar by remember { mutableStateOf<Modulo?>(null) }
    var confirmarCompleta by remember { mutableStateOf(false) }
    val libre = vm.conectado && vm.trabajo == null
    Lista {
        Suave(
            "Cada módulo es una computadora del auto. La app los encuentra preguntando dirección por dirección y " +
                "guarda el mapa en el teléfono. Los nombres marcados como \"probable\" son una suposición: " +
                "tocá un módulo para ponerle el nombre correcto.",
            estilo = MaterialTheme.typography.bodyMedium,
        )
        FilaBotones {
            BotonAcento("Búsqueda rápida", Modifier.weight(1f), habilitado = libre) { vm.buscarModulos(false) }
            BotonOscuro("Búsqueda completa", Modifier.weight(1f), habilitado = libre) { confirmarCompleta = true }
        }
        if (!vm.conectado) Suave("Conectate al adaptador para buscar módulos.")
        if (vm.modulos.isEmpty()) Tarjeta { Suave("Todavía no hay módulos guardados.") }
        for (m in vm.modulos) {
            Tarjeta(alHacerClic = { editar = m }) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(m.nombre, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                    Etiqueta(m.protocolo?.name ?: "?", LocalMarca.current.claro)
                }
                Suave("Dirección ${m.direccion} · ${m.categoria.etiqueta}")
                m.identificacion.forEach { (k, v) -> Suave("$k: $v") }
            }
        }
        if (vm.modulos.isNotEmpty()) {
            BotonOscuro("Olvidar el mapa de módulos", Modifier.fillMaxWidth(), colorTexto = Tinta.Rojo) { vm.olvidarModulos() }
        }
    }
    if (confirmarCompleta) {
        Dialogo(
            titulo = "Búsqueda completa",
            texto = "Recorre unas 900 direcciones del bus de diagnóstico con pedidos inofensivos de lectura. " +
                "Tarda entre 3 y 5 minutos. Dejá el contacto puesto y no uses otras funciones del auto mientras tanto.\n\n" +
                "Conviene hacerla una vez, con la batería bien cargada.",
            confirmar = "Empezar",
            alConfirmar = { confirmarCompleta = false; vm.buscarModulos(true) },
            alCancelar = { confirmarCompleta = false },
        )
    }
    editar?.let { m ->
        var propio by remember(m) { mutableStateOf("") }
        Dialogo(
            titulo = "¿Qué módulo es?",
            texto = "Dirección ${m.direccion}. Elegí de la lista o escribí un nombre.",
            confirmar = "Guardar nombre",
            alConfirmar = {
                if (propio.isNotBlank()) vm.renombrarModulo(m, propio.trim(), m.categoria)
                editar = null
            },
            alCancelar = { editar = null },
        ) {
            Campo(propio, { propio = it }, "Nombre propio")
            for ((nombre, categoria) in CatalogoModulos.opciones) {
                Text(
                    nombre, style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp))
                        .clickable { vm.renombrarModulo(m, nombre, categoria); editar = null }
                        .background(Tinta.Tarjeta).padding(12.dp),
                )
            }
        }
    }
}

// ── Datos en vivo ────────────────────────────────────────────────────────────

@Composable
fun PantallaEnVivo(vm: AppViewModel) {
    val marca = LocalMarca.current
    Lista {
        if (!vm.conectado) {
            Suave("Conectate al adaptador para ver datos en vivo.")
            return@Lista
        }
        if (vm.pidsVivo.isEmpty()) Tarjeta { FilaCargando("Preguntándole al motor qué datos ofrece…") }
        Suave("Valores que informa la computadora del motor, en tiempo real. Son solo de lectura.")
        for (pid in vm.pidsVivo) {
            Tarjeta {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(pid.nombre, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                    Text(
                        vm.valoresVivo[pid.pid]?.let { Obd2.formatear(it) } ?: "—",
                        style = TextStyle(brush = marca.degradeVertical, fontFamily = Titular, fontSize = 32.sp),
                    )
                    Suave(" " + pid.unidad)
                }
                pid.ayuda?.let { Suave(it) }
            }
        }
    }
}

// ── Emisiones ────────────────────────────────────────────────────────────────

@Composable
fun PantallaEmisiones(vm: AppViewModel) {
    Lista {
        val estados = vm.emisiones
        if (!vm.conectado) {
            Suave("Conectate al adaptador para leer los monitores.")
            return@Lista
        }
        if (estados == null) {
            Tarjeta { FilaCargando("Leyendo…") }
            return@Lista
        }
        if (estados.isEmpty()) Tarjeta { Suave("El auto no respondió al pedido de estado de emisiones.") }
        for (e in estados) {
            Tarjeta {
                Text(if (e.idEcu == 0x7E8) "Motor" else "Módulo %03X".format(e.idEcu), style = MaterialTheme.typography.titleMedium)
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Punto(if (e.testigoEncendido) Tinta.Amarillo else Tinta.Verde)
                    Text(
                        "Testigo de motor " + (if (e.testigoEncendido) "encendido" else "apagado") + " · ${e.cantidadCodigos} códigos confirmados",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
                for (mon in e.monitores) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Punto(if (mon.listo) Tinta.Verde else Tinta.Apagado, 8)
                        Text(mon.nombre, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                        Suave(if (mon.listo) "listo" else "sin completar")
                    }
                }
            }
        }
        Suave(
            "Los monitores son autochequeos que el motor hace mientras andás. Después de borrar códigos o desconectar " +
                "la batería quedan \"sin completar\" y se van completando solos con el uso normal."
        )
        vm.cuadro?.let { c ->
            Tarjeta {
                Text("Foto del momento de la falla", style = MaterialTheme.typography.titleMedium)
                Suave("Así estaba el motor cuando se guardó el código ${c.codigo}:")
                for (v in c.valores) {
                    Row {
                        Text(v.nombre, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
                        Text(v.valor, style = MaterialTheme.typography.bodySmall, color = LocalMarca.current.claro)
                    }
                }
            }
        }
    }
}

// ── Diagnóstico de la conexión ───────────────────────────────────────────────

@Composable
fun PantallaConexion(vm: AppViewModel) {
    Lista {
        when (val c = vm.conexion) {
            is Conexion.Conectado -> {
                Tarjeta {
                    Text(c.nombre, style = MaterialTheme.typography.titleMedium)
                    Suave("Chip (ATI): ${c.info.elm}")
                    Suave("Firmware (STI): ${c.info.stn ?: "no es un adaptador STN"}")
                    c.info.dispositivo?.let { Suave("Modelo (STDI): $it") }
                    Suave("Tensión (ATRV): ${c.info.voltaje ?: "sin dato"}")
                }
                Tarjeta {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Punto(if (c.info.busOk) Tinta.Verde else Tinta.Amarillo)
                        Text("Comunicación con el auto", style = MaterialTheme.typography.titleMedium)
                    }
                    Suave(c.info.detalleBus)
                    Suave("Protocolo: CAN 11 bits, 500 kbps (ISO 15765-4), pines 6 y 14 del conector.")
                }
                Tarjeta {
                    Text("Seguridad", style = MaterialTheme.typography.titleMedium)
                    Suave(
                        "La app solo manda pedidos de lectura (identificación, códigos, datos OBD) y el borrado de códigos " +
                            "con confirmación. Cualquier otro pedido (codificación, escritura, pruebas de actuadores, " +
                            "acceso de seguridad, flasheo) queda bloqueado antes de salir del teléfono."
                    )
                }
            }
            is Conexion.Conectando -> Tarjeta { FilaCargando(c.texto) }
            is Conexion.Error -> Tarjeta {
                Text("Error de conexión", style = MaterialTheme.typography.titleMedium, color = Tinta.Rojo)
                Suave(c.texto)
            }
            Conexion.Desconectado -> Tarjeta { Suave("Sin conexión.") }
        }
        BotonOscuro("Ver registro de comunicación", Modifier.fillMaxWidth()) { vm.ir(Pantalla.Registro) }
    }
}

// ── Registro crudo ───────────────────────────────────────────────────────────

@Composable
fun PantallaRegistro(vm: AppViewModel) {
    val contexto = LocalContext.current
    val lineas = remember(vm.versionLog) { vm.log.copia() }
    val estado = rememberLazyListState()
    LaunchedEffect(lineas.size) { if (lineas.isNotEmpty()) estado.scrollToItem(lineas.size - 1) }
    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Suave("Todo lo que la app le manda al adaptador (TX) y lo que recibe (RX). Si algo falla, compartí este archivo.")
        FilaBotones {
            BotonAcento("Compartir registro", Modifier.weight(1f), habilitado = lineas.isNotEmpty()) {
                val archivo = vm.log.archivoActual()
                if (archivo != null && archivo.exists()) compartirArchivo(contexto, archivo)
                else compartirTexto(contexto, "Registro Gappribaldi", vm.log.texto())
            }
        }
        LazyColumn(
            Modifier.weight(1f).fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Tinta.Tarjeta).padding(8.dp),
            state = estado,
        ) {
            items(lineas) { l ->
                val color = when {
                    " TX " in l -> LocalMarca.current.claro
                    " RX " in l -> Tinta.Texto
                    else -> Tinta.TextoSuave
                }
                androidx.compose.material3.Text(l, style = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 11.sp, lineHeight = 15.sp), color = color)
            }
        }
        Box(Modifier.size(4.dp))
    }
}

// ── Historial ────────────────────────────────────────────────────────────────

@Composable
fun PantallaHistorial(vm: AppViewModel) {
    Lista {
        if (vm.historial.isEmpty()) Tarjeta { Suave("Todavía no hay escaneos guardados. Cada escaneo del auto queda acá.") }
        for (r in vm.historial) {
            Tarjeta(alHacerClic = { vm.verHistorico(r) }) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(fechaCorta(r.fecha), style = MaterialTheme.typography.titleMedium)
                        Suave("${r.totalCodigos} códigos · ${r.modulos.size} módulos" + (r.voltaje?.let { " · $it" } ?: ""))
                    }
                    Text(
                        "Borrar", style = MaterialTheme.typography.labelMedium, color = Tinta.TextoSuave,
                        modifier = Modifier.clip(RoundedCornerShape(8.dp)).clickable { vm.borrarHistorico(r) }.padding(10.dp),
                    )
                }
            }
        }
    }
}

// ── Ajustes ──────────────────────────────────────────────────────────────────

@Composable
fun PantallaAjustes(vm: AppViewModel) {
    var clave by remember { mutableStateOf("") }
    var modelo by remember { mutableStateOf(vm.modelo) }
    val elegirFoto = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) vm.usarFoto(uri)
    }
    Lista {
        Seccion("Apariencia")
        Suave("Elegí la marca: cambia el color de acento de toda la app.")
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            for (m in Marcas.lista) {
                val elegida = m.nombre == vm.marca.nombre
                Column(
                    Modifier.clip(RoundedCornerShape(14.dp))
                        .border(2.dp, if (elegida) Color.White else Color.Transparent, RoundedCornerShape(14.dp))
                        .background(m.degrade).clickable { vm.elegirMarca(m) }
                        .padding(horizontal = 16.dp, vertical = 18.dp),
                ) {
                    Text(m.nombre, style = MaterialTheme.typography.titleMedium, color = Color.White)
                }
            }
        }
        Campo(modelo, { modelo = it.take(8); vm.cambiarModelo(it) }, "Texto del modelo en la portada", mayusculas = true)
        FilaBotones {
            BotonOscuro("Elegir foto del auto", Modifier.weight(1f)) {
                elegirFoto.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
            }
            if (vm.fotoAuto != null) BotonOscuro("Quitar foto", Modifier.weight(1f)) { vm.usarFoto(null) }
        }
        Suave("Para que quede como en el diseño, usá un PNG del auto con fondo transparente: el nombre del modelo queda por detrás.")

        Seccion("Traducción de códigos")
        Suave(
            "La app trae fichas en español para los códigos más comunes de este auto y una base abierta de 9.533 códigos " +
                "genéricos en inglés. Esos textos se pueden traducir en el teléfono (paquete de ~30 MB, una sola descarga)."
        )
        FilaBotones {
            if (vm.traducirAuto == "si") {
                BotonOscuro("Desactivar traducción", Modifier.weight(1f)) { vm.decidirTraduccion(false) }
            } else {
                BotonAcento("Activar traducción", Modifier.weight(1f), habilitado = !vm.bajandoTraduccion) { vm.decidirTraduccion(true) }
            }
        }
        if (vm.bajandoTraduccion) FilaCargando("Bajando el paquete de traducción…")

        Seccion("Explicación con IA (opcional)")
        Suave(
            "El botón \"Explicame este error\" le consulta a Claude (${ClaudeClient.MODELO}). Necesita una API key de Anthropic " +
                "(console.anthropic.com); cada consulta tiene un costo que se cobra en tu cuenta. La key se guarda cifrada " +
                "en este teléfono y solo se manda a Anthropic. Sin key, el resto de la app funciona igual."
        )
        Suave(if (vm.tieneClave) "Estado: hay una API key guardada." else "Estado: sin API key.")
        Campo(clave, { clave = it }, "API key (sk-ant-…)", secreto = true)
        FilaBotones {
            BotonAcento("Guardar key", Modifier.weight(1f), habilitado = clave.isNotBlank()) { vm.guardarClave(clave); clave = "" }
            if (vm.tieneClave) BotonOscuro("Borrar key", Modifier.weight(1f), colorTexto = Tinta.Rojo) { vm.guardarClave(null) }
        }

        Seccion("Acerca de")
        Suave(
            "Gappribaldi ${BuildConfig.VERSION_NAME}. Hecha para un Mercedes-Benz C350 W204 (M272, 7G-Tronic) con OBDLink MX+.\n\n" +
                "Solo lee y borra códigos: no codifica ni escribe en los módulos.\n\n" +
                "Códigos genéricos: base OBDex (github.com/foerbsnavi/OBDex), dominio público CC0. " +
                "Tipografías Anton y Jost (licencia OFL). Las notas \"frecuente en este auto\" son orientativas y no reemplazan " +
                "el diagnóstico de un mecánico."
        )
    }
}
