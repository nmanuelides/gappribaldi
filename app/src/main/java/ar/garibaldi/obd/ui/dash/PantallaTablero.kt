package ar.garibaldi.obd.ui.dash

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.pm.ActivityInfo
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import ar.garibaldi.obd.ui.AppViewModel
import ar.garibaldi.obd.ui.Pantalla
import ar.garibaldi.obd.ui.Suave
import ar.garibaldi.obd.ui.Text
import ar.garibaldi.obd.ui.Tinta
import kotlinx.coroutines.delay

/** Un estilo de tablero: [proporcion] es ancho / alto de la imagen de referencia. */
class EstiloTablero(
    val nombre: String,
    val descripcion: String,
    val proporcion: Float,
    val dibujo: @Composable (DatosTablero, Recursos, Modifier) -> Unit,
)

val TABLEROS = listOf(
    EstiloTablero("Neón 90", "Relojes azul marino con rayas cian, agujas rojas y testigos que se prenden al arrancar.", 2000f / 932f) { d, r, m -> TableroNeon(d, r, m) },
    EstiloTablero("Gran turismo", "Velocímetro naranja al centro, mapa a la izquierda y datos del viaje a la derecha.", 2000f / 853f) { d, r, m -> TableroGranTurismo(d, r, m) },
    EstiloTablero("Cabina virtual", "Dos relojes redondos con zona roja y un mapa claro al medio.", 2000f / 698f) { d, r, m -> TableroCabinaVirtual(d, r, m) },
    EstiloTablero("Híbrido azul", "Velocidad y vueltas en anillos con volumen, con el auto en holograma azul.", 1999f / 846f) { d, r, m -> TableroHibrido(d, r, m) },
    EstiloTablero("M Sport", "Aros cromados sobre fondo rojo anaranjado y la marcha gigante.", 2000f / 926f) { d, r, m -> TableroM(d, r, m) },
    EstiloTablero("Clásico", "Relojes blancos de agujas rojas y computadora de viaje al medio.", 2000f / 782f) { d, r, m -> TableroClasico(d, r, m) },
    EstiloTablero("Superdeportivo", "Anillo blanco de vueltas hasta 10.000, marcha y cronómetro al centro.", 2000f / 1408f) { d, r, m -> TableroSuper(d, r, m) },
    EstiloTablero("Todoterreno", "Relojes plateados y tu auto de perfil al medio.", 1999f / 890f) { d, r, m -> TableroTodoterreno(d, r, m) },
    EstiloTablero("Info activa", "Discos metálicos: potencia a la izquierda y velocidad a la derecha.", 2000f / 964f) { d, r, m -> TableroInfoActiva(d, r, m) },
    EstiloTablero("Roadster LCD", "Barra de vueltas curva hasta 9.000 y velocidad en siete segmentos.", 1999f / 478f) { d, r, m -> TableroRoadster(d, r, m) },
)

@Composable
private fun recursos(vm: AppViewModel): Recursos {
    val ctx = LocalContext.current
    val marca = vm.marca
    val modelo = vm.modelo
    val foto = remember(marca.nombre, modelo, vm.fotoAuto, vm.versionFoto) {
        val propia = vm.fotoAuto?.let { runCatching { android.graphics.BitmapFactory.decodeFile(it.path)?.asImageBitmap() }.getOrNull() }
        propia ?: marca.fotoDe(modelo)?.let { id -> runCatching { android.graphics.BitmapFactory.decodeResource(ctx.resources, id)?.asImageBitmap() }.getOrNull() }
    }
    val logo: ImageBitmap? = remember(marca.nombre) {
        marca.logo?.let { id -> runCatching { android.graphics.BitmapFactory.decodeResource(ctx.resources, id)?.asImageBitmap() }.getOrNull() }
    }
    return Recursos(foto, logo)
}

/** Lista de tableros, cada uno con una vista previa en vivo. */
@Composable
fun PantallaTableros(vm: AppViewModel) {
    val r = recursos(vm)
    // Las vistas previas se refrescan con cada dato nuevo, sin animación
    val d = vm.datosTablero.copy(millas = vm.millas)
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp).padding(bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Suave("Elegí un tablero: se abre a pantalla completa, en horizontal, con los datos del auto en vivo. Deslizá para cambiar de tablero y tocá la pantalla para ver los controles.")
        TABLEROS.forEachIndexed { i, t ->
            Column(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(Tinta.Tarjeta)
                    .border(1.dp, Tinta.Borde, RoundedCornerShape(16.dp)).clickable { vm.ir(Pantalla.TableroVivo(i)) },
            ) {
                Box(Modifier.fillMaxWidth().background(Color.Black)) {
                    t.dibujo(d, r, Modifier.fillMaxWidth().aspectRatio(t.proporcion))
                }
                Column(Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
                    Text("${i + 1} · ${t.nombre}", style = MaterialTheme.typography.titleMedium)
                    Suave(t.descripcion)
                }
            }
        }
    }
}

private fun Context.actividad(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.actividad()
    else -> null
}

/** Un tablero a pantalla completa: horizontal, sin barras del sistema y con la pantalla siempre prendida. */
@Composable
fun PantallaTableroVivo(vm: AppViewModel, inicial: Int) {
    val ctx = LocalContext.current
    val vista = LocalView.current
    DisposableEffect(Unit) {
        val act = ctx.actividad()
        val antes = act?.requestedOrientation
        act?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        val control = act?.let { WindowCompat.getInsetsController(it.window, it.window.decorView) }
        control?.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        control?.hide(WindowInsetsCompat.Type.systemBars())
        vista.keepScreenOn = true
        onDispose {
            control?.show(WindowInsetsCompat.Type.systemBars())
            act?.requestedOrientation = antes ?: ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
            vista.keepScreenOn = false
        }
    }
    BackHandler { vm.volver() }
    var indice by remember { mutableIntStateOf(inicial.coerceIn(0, TABLEROS.lastIndex)) }
    var controles by remember { mutableStateOf(true) }
    LaunchedEffect(controles, indice) { if (controles) { delay(3500); controles = false } }

    // Encendido: agujas al máximo y de vuelta, con todos los testigos prendidos (se repite al cambiar de tablero)
    val barrido = remember { Animatable(0f) }
    LaunchedEffect(indice) {
        barrido.snapTo(0f)
        barrido.animateTo(1f, tween(1000, easing = FastOutSlowInEasing))
        delay(250)
        barrido.animateTo(0f, tween(1100, easing = FastOutSlowInEasing))
    }
    // Reloj para los tableros que muestran hora o cronómetro
    var cuadro by remember { mutableIntStateOf(0) }
    LaunchedEffect(Unit) { while (true) withFrameMillis { cuadro = (it / 33).toInt() } }

    val crudo = vm.datosTablero
    val suave = spring<Float>(dampingRatio = 0.9f, stiffness = 45f)
    val rpm by animateFloatAsState(crudo.rpm, suave, label = "rpm")
    val vel by animateFloatAsState(crudo.velocidad, suave, label = "vel")
    val pot by animateFloatAsState(crudo.carga ?: crudo.mariposa ?: 0f, suave, label = "pot")
    val agua by animateFloatAsState(crudo.refrigerante ?: 0f, suave, label = "agua")
    val nafta by animateFloatAsState(crudo.nafta ?: 0f, suave, label = "nafta")
    // El cronómetro avanza entre lecturas para que los milisegundos corran
    val segundos = remember(cuadro) { vm.segundosTablero() }
    val d = crudo.copy(
        rpm = rpm, velocidad = vel,
        carga = if (crudo.carga != null) pot else null,
        mariposa = if (crudo.carga == null && crudo.mariposa != null) pot else crudo.mariposa,
        refrigerante = crudo.refrigerante?.let { agua }, nafta = crudo.nafta?.let { nafta },
        barrido = barrido.value, viajeSeg = segundos, millas = vm.millas,
    )
    val r = recursos(vm)
    val estilo = TABLEROS[indice]

    BoxWithConstraints(
        Modifier.fillMaxSize().background(Color.Black)
            .pointerInput(Unit) {
                var acumulado = 0f
                detectHorizontalDragGestures(
                    onDragStart = { acumulado = 0f },
                    onDragEnd = {
                        if (acumulado < -120f) indice = (indice + 1) % TABLEROS.size
                        else if (acumulado > 120f) indice = (indice - 1 + TABLEROS.size) % TABLEROS.size
                        controles = true
                    },
                ) { _, dx -> acumulado += dx }
            }
            .clickable(indication = null, interactionSource = null) { controles = !controles },
    ) {
        val pantalla = maxWidth / maxHeight
        val m = Modifier.aspectRatio(estilo.proporcion, matchHeightConstraintsFirst = pantalla > estilo.proporcion)
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { estilo.dibujo(d, r, m) }
        AnimatedVisibility(controles, Modifier.align(Alignment.TopCenter), enter = fadeIn(), exit = fadeOut()) {
            Row(
                Modifier.padding(top = 12.dp).clip(RoundedCornerShape(50)).background(Color(0xCC000000))
                    .border(1.dp, Color(0x44FFFFFF), RoundedCornerShape(50)).padding(horizontal = 6.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                BotonRedondo("‹") { indice = (indice - 1 + TABLEROS.size) % TABLEROS.size }
                Text("${indice + 1}/${TABLEROS.size} · ${estilo.nombre}", style = MaterialTheme.typography.titleMedium, color = Color.White, textAlign = TextAlign.Center)
                BotonRedondo("›") { indice = (indice + 1) % TABLEROS.size }
                BotonRedondo("✕") { vm.volver() }
            }
        }
    }
}

@Composable
private fun BotonRedondo(texto: String, alTocar: () -> Unit) {
    Box(
        Modifier.size(40.dp).clip(RoundedCornerShape(50)).clickable(onClick = alTocar),
        contentAlignment = Alignment.Center,
    ) { Text(texto, style = MaterialTheme.typography.titleLarge, color = Color.White) }
}
