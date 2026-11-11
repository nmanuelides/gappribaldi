package ar.garibaldi.obd.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

/**
 * Elegir la marca del auto. Es lo primero que muestra la app al abrirse y lo sigue mostrando cada vez,
 * salvo que se marque una marca como favorita. Mientras se elige, toda la pantalla toma los colores
 * de la marca tocada.
 */
@Composable
fun SelectorMarca(vm: AppViewModel) {
    var elegida by remember { mutableStateOf(vm.marca) }
    var favorita by remember { mutableStateOf(vm.marcaFavorita == vm.marca.nombre) }
    // Volver atrás sigue con la marca de antes, sin tocar la favorita
    BackHandler { vm.cerrarSelectorMarca() }

    TemaGaribaldi(elegida) {
        FondoApp(intensidad = 0.75f) {
            Column(Modifier.fillMaxSize().systemBarsPadding()) {
                Column(
                    Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 20.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Text(
                        "¿Qué auto tenés?", style = MaterialTheme.typography.headlineMedium,
                        modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center,
                    )
                    Suave(
                        "Elegí la marca: la app se adapta a tus colores y a lo que puede leer de ese auto.",
                        Modifier.fillMaxWidth().padding(horizontal = 12.dp),
                    )
                    Spacer(Modifier.height(4.dp))
                    for (fila in Marcas.lista.chunked(2)) {
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            for (m in fila) {
                                TarjetaMarca(m, m.nombre == elegida.nombre, vm.marcaFavorita == m.nombre, Modifier.weight(1f)) {
                                    elegida = m
                                    favorita = vm.marcaFavorita == m.nombre
                                }
                            }
                            if (fila.size == 1) Spacer(Modifier.weight(1f))
                        }
                    }
                }
                // Abajo, a mano del pulgar: recordar y continuar
                Column(
                    Modifier.fillMaxWidth().padding(horizontal = 16.dp).padding(top = 8.dp, bottom = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Row(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable { favorita = !favorita }
                            .padding(horizontal = 8.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Icon(
                            Icons.Filled.Star, contentDescription = null,
                            tint = if (favorita) elegida.claro else Tinta.Apagado, modifier = Modifier.size(28.dp),
                        )
                        Column(Modifier.weight(1f)) {
                            Text("Marcar ${elegida.nombre} como favorita", style = MaterialTheme.typography.bodyLarge)
                            Suave(
                                if (favorita) "La app va a abrir directo con esta marca. Se cambia desde Ajustes."
                                else "Si no marcás una favorita, la app te pregunta cada vez que se abre."
                            )
                        }
                    }
                    BotonAcento("Continuar con ${elegida.nombre}  →", Modifier.fillMaxWidth()) {
                        vm.confirmarMarca(elegida, favorita)
                    }
                }
            }
        }
    }
}

@Composable
private fun TarjetaMarca(m: Marca, elegida: Boolean, favorita: Boolean, modifier: Modifier, alTocar: () -> Unit) {
    val forma = RoundedCornerShape(16.dp)
    Box(
        modifier
            .clip(forma)
            .background(if (elegida) Tinta.Tarjeta else Tinta.Fondo)
            .border(if (elegida) 2.dp else 1.dp, if (elegida) m.degrade else androidx.compose.ui.graphics.SolidColor(Tinta.Borde), forma)
            .clickable(onClick = alTocar)
            .padding(horizontal = 12.dp, vertical = 14.dp),
    ) {
        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Box(Modifier.fillMaxWidth().height(56.dp), contentAlignment = Alignment.Center) {
                val logo = m.logo
                if (logo != null) {
                    val dibujo = painterResource(logo)
                    val proporcion = dibujo.intrinsicSize.let { if (it.height > 0f) it.width / it.height else 4f }
                    Image(
                        dibujo, m.nombre, contentScale = ContentScale.Fit,
                        modifier = if (proporcion > 2.6f) Modifier.fillMaxWidth(0.9f).aspectRatio(proporcion)
                        else Modifier.height(if (proporcion < 1.6f) 56.dp else 40.dp).aspectRatio(proporcion),
                    )
                } else {
                    Text(m.nombre, style = MaterialTheme.typography.titleLarge)
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                if (favorita) Icon(Icons.Filled.Star, contentDescription = "Favorita", tint = m.claro, modifier = Modifier.size(14.dp))
                Text(m.nombre, style = MaterialTheme.typography.labelLarge, color = if (elegida) Color.White else Tinta.TextoSuave)
            }
        }
    }
}
