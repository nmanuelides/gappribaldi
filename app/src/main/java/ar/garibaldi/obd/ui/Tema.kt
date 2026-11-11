package ar.garibaldi.obd.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ar.garibaldi.obd.R
import ar.garibaldi.obd.dtc.Gravedad

/** Colores de acento de una marca: [claro] es el principal y [oscuro] el final del degradé. */
class Marca(val nombre: String, val claro: Color, val oscuro: Color) {
    /** Degradé horizontal de los botones principales. */
    val degrade: Brush get() = Brush.horizontalGradient(listOf(claro, oscuro))

    /** Degradé vertical del texto gigante del modelo. */
    val degradeVertical: Brush get() = Brush.verticalGradient(listOf(claro, oscuro))
}

object Marcas {
    val lista = listOf(
        Marca("Mercedes-Benz", Color(0xFF00B1A9), Color(0xFF00302E)),
        Marca("BMW", Color(0xFF2E8BFF), Color(0xFF0A3A8C)),
        Marca("Audi", Color(0xFFF50537), Color(0xFF8A0320)),
        Marca("Volkswagen", Color(0xFF00B0F0), Color(0xFF00437A)),
        Marca("McLaren", Color(0xFFFF9A1F), Color(0xFFE85D04)),
        Marca("Ferrari", Color(0xFFFF2800), Color(0xFF8F1500)),
        Marca("Porsche", Color(0xFFD5A021), Color(0xFF7A5A0C)),
    )

    fun porNombre(nombre: String?): Marca = lista.firstOrNull { it.nombre == nombre } ?: lista.first()
}

val LocalMarca = staticCompositionLocalOf { Marcas.lista.first() }

object Tinta {
    val Fondo = Color(0xFF000000)
    val Tarjeta = Color(0xFF151517)
    val TarjetaAlta = Color(0xFF1F1F22)
    val Borde = Color(0xFF2A2A2E)
    val Texto = Color(0xFFFFFFFF)
    /** El diseño usa solo blanco para los textos; se conserva el nombre para los textos secundarios. */
    val TextoSuave = Color(0xFFFFFFFF)
    val Apagado = Color(0xFF55555C)
    val Verde = Color(0xFF3DDC84)
    val Amarillo = Color(0xFFFFC53D)
    val Rojo = Color(0xFFFF5A4F)
}

fun Gravedad.color(): Color = when (this) {
    Gravedad.VERDE -> Tinta.Verde
    Gravedad.AMARILLO -> Tinta.Amarillo
    Gravedad.ROJO -> Tinta.Rojo
}

/** Tipografía condensada y pesada para títulos (Anton, licencia OFL). */
val Titular = FontFamily(Font(R.font.anton))
/** Tipografía de los textos chicos: Jost, la misma que usa Einkauf (licencia OFL). */
val Jost = FontFamily(
    Font(R.font.jost, FontWeight.Light),
    Font(R.font.jost, FontWeight.Normal),
    Font(R.font.jost, FontWeight.Medium),
    Font(R.font.jost, FontWeight.SemiBold),
)
private val Mono = Jost

private val Tipos = Typography().let { t ->
    Typography(
        displayLarge = t.displayLarge.copy(fontFamily = Titular),
        displayMedium = t.displayMedium.copy(fontFamily = Titular),
        displaySmall = t.displaySmall.copy(fontFamily = Titular),
        headlineLarge = t.headlineLarge.copy(fontFamily = Titular),
        headlineMedium = t.headlineMedium.copy(fontFamily = Titular, fontSize = 30.sp),
        headlineSmall = t.headlineSmall.copy(fontFamily = Titular, fontSize = 24.sp),
        titleLarge = t.titleLarge.copy(fontFamily = Titular, fontSize = 22.sp),
        titleMedium = t.titleMedium.copy(fontFamily = Titular, fontSize = 18.sp, letterSpacing = 0.3.sp),
        titleSmall = t.titleSmall.copy(fontFamily = Mono),
        bodyLarge = t.bodyLarge.copy(fontFamily = Mono, fontSize = 16.sp, lineHeight = 23.sp),
        bodyMedium = t.bodyMedium.copy(fontFamily = Mono, fontSize = 15.sp, lineHeight = 21.sp),
        bodySmall = t.bodySmall.copy(fontFamily = Mono, fontSize = 13.sp, lineHeight = 18.sp),
        labelLarge = t.labelLarge.copy(fontFamily = Mono, fontSize = 15.sp, fontWeight = FontWeight.Medium),
        labelMedium = t.labelMedium.copy(fontFamily = Mono),
        labelSmall = t.labelSmall.copy(fontFamily = Mono),
    )
}

@Composable
fun TemaGaribaldi(marca: Marca, contenido: @Composable () -> Unit) {
    val colores = darkColorScheme(
        primary = marca.claro,
        onPrimary = Color.White,
        secondary = marca.claro,
        background = Tinta.Fondo,
        onBackground = Tinta.Texto,
        surface = Tinta.Tarjeta,
        onSurface = Tinta.Texto,
        surfaceVariant = Tinta.TarjetaAlta,
        onSurfaceVariant = Tinta.TextoSuave,
        surfaceContainer = Tinta.Tarjeta,
        surfaceContainerHigh = Tinta.TarjetaAlta,
        surfaceContainerHighest = Tinta.TarjetaAlta,
        outline = Tinta.Borde,
        outlineVariant = Tinta.Borde,
        error = Tinta.Rojo,
    )
    CompositionLocalProvider(LocalMarca provides marca) {
        MaterialTheme(colorScheme = colores, typography = Tipos) {
            // Sin esto, los textos sin color explícito salen negros sobre el fondo oscuro
            CompositionLocalProvider(LocalContentColor provides Tinta.Texto, content = contenido)
        }
    }
}

/** Fondo negro con una bruma del color de la marca que baja desde arriba, como en el diseño de referencia. */
@Composable
fun FondoApp(intensidad: Float = 0.55f, contenido: @Composable BoxScope.() -> Unit) {
    val marca = LocalMarca.current
    Box(
        Modifier
            .fillMaxSize()
            .background(Tinta.Fondo)
            .drawBehind {
                drawRect(
                    Brush.radialGradient(
                        colors = listOf(marca.claro.copy(alpha = intensidad * 0.45f), Color.Transparent),
                        center = Offset(size.width / 2f, size.height * 0.12f),
                        radius = size.width * 1.05f,
                    )
                )
            },
        content = contenido,
    )
}

private val FormaBoton = RoundedCornerShape(12.dp)

/** Botón principal: degradé de la marca. */
@Composable
fun BotonAcento(
    texto: String,
    modifier: Modifier = Modifier,
    habilitado: Boolean = true,
    alHacerClic: () -> Unit,
) {
    val marca = LocalMarca.current
    Box(
        modifier
            .heightIn(min = 52.dp)
            .clip(FormaBoton)
            .background(marca.degrade)
            .alpha(if (habilitado) 1f else 0.4f)
            .clickable(enabled = habilitado, onClick = alHacerClic)
            .padding(horizontal = 18.dp, vertical = 14.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(texto, style = MaterialTheme.typography.labelLarge, color = Color.White, textAlign = TextAlign.Center)
    }
}

/** Botón secundario: gris oscuro. */
@Composable
fun BotonOscuro(
    texto: String,
    modifier: Modifier = Modifier,
    habilitado: Boolean = true,
    colorTexto: Color = Tinta.Texto,
    alHacerClic: () -> Unit,
) {
    Box(
        modifier
            .heightIn(min = 52.dp)
            .clip(FormaBoton)
            .background(Tinta.TarjetaAlta)
            .alpha(if (habilitado) 1f else 0.4f)
            .clickable(enabled = habilitado, onClick = alHacerClic)
            .padding(horizontal = 18.dp, vertical = 14.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(texto, style = MaterialTheme.typography.labelLarge, color = colorTexto, textAlign = TextAlign.Center)
    }
}

@Composable
fun Tarjeta(
    modifier: Modifier = Modifier,
    alHacerClic: (() -> Unit)? = null,
    relleno: PaddingValues = PaddingValues(16.dp),
    contenido: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(Tinta.Tarjeta)
            .then(if (alHacerClic != null) Modifier.clickable(onClick = alHacerClic) else Modifier)
            .padding(relleno),
        verticalArrangement = Arrangement.spacedBy(6.dp),
        content = contenido,
    )
}

@Composable
fun Punto(color: Color, tamano: Int = 10) {
    Box(Modifier.size(tamano.dp).clip(CircleShape).background(color))
}

@Composable
fun Etiqueta(texto: String, color: Color) {
    Text(
        texto,
        style = MaterialTheme.typography.labelSmall,
        color = color,
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(color.copy(alpha = 0.16f))
            .padding(horizontal = 8.dp, vertical = 3.dp),
    )
}

@Composable
fun Seccion(titulo: String) {
    Text(titulo, style = MaterialTheme.typography.titleMedium, color = Tinta.Texto, modifier = Modifier.padding(top = 10.dp))
}

@Composable
fun Suave(texto: String, modifier: Modifier = Modifier, estilo: TextStyle = MaterialTheme.typography.bodySmall) {
    Text(texto, style = estilo, color = Tinta.TextoSuave, modifier = modifier)
}

@Composable
fun FilaCargando(texto: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp, color = LocalMarca.current.claro)
        Suave(texto)
    }
}

@Composable
fun FilaBotones(contenido: @Composable RowScope.() -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp), content = contenido)
}
