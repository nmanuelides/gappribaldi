package ar.garibaldi.obd.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import ar.garibaldi.obd.i18n.Idioma
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.height
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import ar.garibaldi.obd.R
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import java.io.File

class MainActivity : ComponentActivity() {
    private var modelo: AppViewModel? = null

    override fun onResume() {
        super.onResume()
        // Como en MTG Reforged: cada vez que la app vuelve a primer plano se fija si hay versión nueva
        modelo?.buscarActualizacion()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val vm: AppViewModel = viewModel()
            modelo = vm
            TemaGaribaldi(vm.marca) { App(vm) }
        }
    }
}

@Composable
private fun App(vm: AppViewModel) {
    BackHandler(enabled = vm.pila.size > 1) { vm.volver() }
    val pantalla = vm.pantalla

    // Mientras trabaja con el auto o muestra datos en vivo, que no se apague la pantalla
    val vista = LocalView.current
    val mantener = vm.trabajo != null || pantalla == Pantalla.EnVivo
    DisposableEffect(mantener) {
        vista.keepScreenOn = mantener
        onDispose { vista.keepScreenOn = false }
    }

    FondoApp(intensidad = if (pantalla == Pantalla.Inicio) 0.75f else 0.4f) {
        Column(Modifier.fillMaxSize().systemBarsPadding()) {
            BarraSuperior(vm, pantalla)
            Box(Modifier.weight(1f).fillMaxWidth()) {
                when (pantalla) {
                    Pantalla.Inicio -> PantallaInicio(vm)
                    Pantalla.Resultado -> PantallaResultado(vm)
                    is Pantalla.Detalle -> PantallaDetalle(vm, pantalla)
                    Pantalla.Modulos -> PantallaModulos(vm)
                    Pantalla.EnVivo -> PantallaEnVivo(vm)
                    Pantalla.Emisiones -> PantallaEmisiones(vm)
                    Pantalla.Conexion -> PantallaConexion(vm)
                    Pantalla.Registro -> PantallaRegistro(vm)
                    Pantalla.HistorialLista -> PantallaHistorial(vm)
                    Pantalla.Ajustes -> PantallaAjustes(vm)
                }
            }
            vm.trabajo?.let { PanelTrabajo(it, vm::cancelarTrabajo) }
        }
        vm.mensaje?.let { texto ->
            Dialogo(
                titulo = "Aviso", texto = texto, confirmar = "Entendido",
                alConfirmar = { vm.mensaje = null }, alCancelar = { vm.mensaje = null }, cancelar = null,
            )
        }
    }
}

@Composable
private fun BarraSuperior(vm: AppViewModel, pantalla: Pantalla) {
    val titulo = when (pantalla) {
        Pantalla.Inicio -> vm.marca.nombre
        Pantalla.Resultado -> "Resultado"
        is Pantalla.Detalle -> "Detalle"
        Pantalla.Modulos -> "Módulos"
        Pantalla.EnVivo -> "Datos en vivo"
        Pantalla.Emisiones -> "Emisiones"
        Pantalla.Conexion -> "Conexión"
        Pantalla.Registro -> "Registro"
        Pantalla.HistorialLista -> "Historial"
        Pantalla.Ajustes -> "Ajustes"
    }
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Los dos costados con el mismo ancho para que el título quede centrado
        Box(Modifier.width(100.dp)) {
            if (pantalla != Pantalla.Inicio) BotonIcono(Icons.AutoMirrored.Filled.ArrowBack, "Volver") { vm.volver() }
        }
        if (pantalla == Pantalla.Inicio && vm.marca.nombre == "Mercedes-Benz") {
            Image(
                painterResource(R.drawable.logo_mercedes), "Mercedes-Benz",
                modifier = Modifier.weight(1f).height(30.dp), contentScale = ContentScale.Fit,
            )
        } else {
            Text(
                titulo,
                style = if (pantalla == Pantalla.Inicio) MaterialTheme.typography.titleLarge else MaterialTheme.typography.headlineSmall,
                color = Tinta.Texto,
                textAlign = TextAlign.Center,
                modifier = Modifier.weight(1f),
            )
        }
        Row(Modifier.width(100.dp), horizontalArrangement = Arrangement.End, verticalAlignment = Alignment.CenterVertically) {
            SelectorIdioma(vm)
            if (pantalla != Pantalla.Ajustes) BotonIcono(Icons.Filled.Settings, "Ajustes") { vm.ir(Pantalla.Ajustes) }
        }
    }
}

/** Interruptor ES / EN: está en la barra de arriba de todas las pantallas. */
@Composable
private fun SelectorIdioma(vm: AppViewModel) {
    val marca = LocalMarca.current
    Row(
        Modifier.clip(RoundedCornerShape(50)).background(Tinta.TarjetaAlta).clickable { vm.cambiarIdioma(!Idioma.ingles) }
            .padding(3.dp),
    ) {
        for ((codigo, ingles) in listOf("ES" to false, "EN" to true)) {
            val activo = Idioma.ingles == ingles
            androidx.compose.material3.Text(
                codigo,
                style = MaterialTheme.typography.labelSmall,
                color = Tinta.Texto,
                modifier = Modifier.clip(RoundedCornerShape(50))
                    .then(if (activo) Modifier.background(marca.degrade) else Modifier)
                    .padding(horizontal = 8.dp, vertical = 4.dp),
            )
        }
    }
}

@Composable
fun BotonIcono(icono: ImageVector, descripcion: String, alHacerClic: () -> Unit) {
    Box(
        Modifier.size(44.dp).clip(CircleShape).clickable(onClick = alHacerClic),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icono, contentDescription = descripcion, tint = Tinta.Texto)
    }
}

@Composable
private fun PanelTrabajo(t: Trabajo, alCancelar: () -> Unit) {
    Column(
        Modifier.fillMaxWidth().background(Tinta.Tarjeta).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(t.titulo, style = MaterialTheme.typography.titleMedium, color = Tinta.Texto)
                if (t.detalle.isNotEmpty()) Suave(t.detalle)
            }
            BotonOscuro("Cancelar", alHacerClic = alCancelar)
        }
        val marca = LocalMarca.current
        if (t.fraccion != null) {
            LinearProgressIndicator(
                progress = { t.fraccion }, modifier = Modifier.fillMaxWidth(),
                color = marca.claro, trackColor = Tinta.Borde,
            )
        } else {
            LinearProgressIndicator(Modifier.fillMaxWidth(), color = marca.claro, trackColor = Tinta.Borde)
        }
    }
}

// ── Compartir ────────────────────────────────────────────────────────────────

fun compartirTexto(contexto: Context, titulo: String, texto: String) {
    val intent = Intent(Intent.ACTION_SEND).setType("text/plain")
        .putExtra(Intent.EXTRA_SUBJECT, titulo).putExtra(Intent.EXTRA_TEXT, texto)
    contexto.startActivity(Intent.createChooser(intent, titulo))
}

fun compartirArchivo(contexto: Context, archivo: File) {
    val uri = FileProvider.getUriForFile(contexto, contexto.packageName + ".archivos", archivo)
    val intent = Intent(Intent.ACTION_SEND).setType("text/plain")
        .putExtra(Intent.EXTRA_SUBJECT, "Registro Gappribaldi").putExtra(Intent.EXTRA_STREAM, uri)
        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    contexto.startActivity(Intent.createChooser(intent, "Compartir registro"))
}

fun buscarEnLaWeb(contexto: Context, consulta: String) {
    val uri = Uri.parse("https://www.google.com/search").buildUpon().appendQueryParameter("q", consulta).build()
    try {
        contexto.startActivity(Intent(Intent.ACTION_VIEW, uri))
    } catch (_: Exception) {
    }
}
