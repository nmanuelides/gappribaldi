package ar.garibaldi.obd.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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

/**
 * Una marca: [claro] es el color principal y [oscuro] el final del degradé. [modelo] es el texto
 * gigante de la portada por defecto; [logo] y [foto] son opcionales (sin logo va el nombre en letras,
 * sin foto queda solo el modelo).
 */
class Marca(
    val nombre: String,
    val claro: Color,
    val oscuro: Color,
    val modelo: String,
    val logo: Int? = null,
    val foto: Int? = null,
    /** Modelos que se pueden elegir para la portada (OBD-II, los que más se ven en la calle). */
    val modelos: List<String> = listOf(modelo),
) {
    /** Degradé horizontal de los botones principales. */
    val degrade: Brush get() = Brush.horizontalGradient(listOf(claro, oscuro))

    /** Degradé vertical del texto gigante del modelo. */
    val degradeVertical: Brush get() = Brush.verticalGradient(listOf(claro, oscuro))
}

object Marcas {
    val lista = listOf(
        Marca(
            "Mercedes-Benz", Color(0xFF00B1A9), Color(0xFF00302E), "C350",
            logo = R.drawable.logo_mercedes, foto = R.drawable.auto_c350,
            modelos = listOf("C180", "C200", "C220", "C230", "C250", "C280", "C300", "C320", "C350", "C63 AMG", "A200", "B200", "CLA200", "CLK350", "E250", "E350", "GLA200", "GLC300", "ML350", "SPRINTER"),
        ),
        // Colores de cada marca: el claro es el de su identidad, el oscuro la versión profunda del mismo tono
        Marca("Ford", Color(0xFF1C5DD6), Color(0xFF00095B), "FOCUS", logo = R.drawable.logo_ford,
            modelos = listOf("KA", "FIESTA", "FOCUS", "MONDEO", "FUSION", "ECOSPORT", "KUGA", "TERRITORY", "EXPLORER", "BRONCO", "RANGER", "MAVERICK", "F-150", "MUSTANG", "S-MAX")), // azul Ford del óvalo
        Marca("Volkswagen", Color(0xFF00B0F0), Color(0xFF001E50), "POLO", logo = R.drawable.logo_vw, foto = R.drawable.auto_polo,
            modelos = listOf("UP!", "GOL", "GOL TREND", "FOX", "SURAN", "VOYAGE", "POLO", "VIRTUS", "VENTO", "BORA", "GOLF", "PASSAT", "SCIROCCO", "NIVUS", "T-CROSS", "TAOS", "TIGUAN", "TOUAREG", "SAVEIRO", "AMAROK")), // celeste y azul VW
        Marca("Toyota", Color(0xFFEB0A1E), Color(0xFF4A0006), "COROLLA", logo = R.drawable.logo_toyota,
            modelos = listOf("ETIOS", "YARIS", "COROLLA", "COROLLA CROSS", "CAMRY", "PRIUS", "C-HR", "RAV4", "SW4", "HILUX", "PRADO", "LAND CRUISER", "86")), // rojo Toyota
        Marca("BMW", Color(0xFF3FA9F5), Color(0xFF0A3E73), "320I", logo = R.drawable.logo_bmw,
            modelos = listOf("118I", "120I", "130I", "320I", "325I", "328I", "330I", "335I", "340I", "520I", "530I", "540I", "X1", "X2", "X3", "X4", "X5", "X6", "Z4", "M2", "M3", "M4", "M5")), // celeste del logo BMW
        Marca("Mini", Color(0xFFA7A9AC), Color(0xFF2E3033), "COOPER", logo = R.drawable.logo_mini,
            modelos = listOf("ONE", "COOPER", "COOPER S", "JCW", "CLUBMAN", "COUNTRYMAN", "PACEMAN", "COUPE", "ROADSTER")), // gris plata Mini
        Marca("Chevrolet", Color(0xFFD1A23A), Color(0xFF4A3300), "CRUZE", logo = R.drawable.logo_chevrolet,
            modelos = listOf("CORSA", "CLASSIC", "CELTA", "AGILE", "ONIX", "PRISMA", "JOY", "AVEO", "SONIC", "COBALT", "ASTRA", "VECTRA", "CRUZE", "MERIVA", "SPIN", "TRACKER", "EQUINOX", "CAPTIVA", "TRAILBLAZER", "MONTANA", "S10", "CAMARO")), // dorado del moño Chevrolet
        Marca("Renault", Color(0xFFFFCC00), Color(0xFF3D3000), "SANDERO", logo = R.drawable.logo_renault,
            modelos = listOf("CLIO", "KWID", "SANDERO", "STEPWAY", "LOGAN", "SYMBOL", "MEGANE", "FLUENCE", "SCENIC", "LAGUNA", "KANGOO", "DUSTER", "CAPTUR", "KOLEOS", "OROCH", "ALASKAN")), // amarillo Renault
        Marca("Honda", Color(0xFFE40521), Color(0xFF3D0008), "CIVIC", logo = R.drawable.logo_honda,
            modelos = listOf("FIT", "CITY", "CIVIC", "ACCORD", "HR-V", "WR-V", "CR-V", "PILOT", "ODYSSEY")), // rojo Honda
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

/** Botón secundario: negro con borde en el degradé de la marca. */
@Composable
fun BotonOscuro(
    texto: String,
    modifier: Modifier = Modifier,
    habilitado: Boolean = true,
    colorTexto: Color = Tinta.Texto,
    alHacerClic: () -> Unit,
) {
    val marca = LocalMarca.current
    Box(
        modifier
            .heightIn(min = 52.dp)
            .clip(FormaBoton)
            .background(Tinta.Fondo)
            .border(1.5.dp, marca.degrade, FormaBoton)
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
