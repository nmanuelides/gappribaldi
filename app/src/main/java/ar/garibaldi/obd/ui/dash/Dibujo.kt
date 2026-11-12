package ar.garibaldi.obd.ui.dash

import android.graphics.BlurMaskFilter
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asAndroidPath
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.sp
import ar.garibaldi.obd.R
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

// Tipografías de los tableros (todas con licencia OFL)
val FOrbitron = FontFamily(Font(R.font.orbitron))
val FRajdhani = FontFamily(Font(R.font.rajdhani_medium, FontWeight.Medium), Font(R.font.rajdhani_bold, FontWeight.Bold))
val FBarlow = FontFamily(
    Font(R.font.barlow_regular, FontWeight.Normal),
    Font(R.font.barlow_medium, FontWeight.Medium),
    Font(R.font.barlow_semibold, FontWeight.SemiBold),
)
val FBarlowCond = FontFamily(Font(R.font.barlow_cond_medium, FontWeight.Medium))
val FMichroma = FontFamily(Font(R.font.michroma))
/** Montserrat variable: ancha y simétrica, para el tablero híbrido. */
@OptIn(androidx.compose.ui.text.ExperimentalTextApi::class)
val FMont = FontFamily(
    Font(R.font.montserrat, FontWeight.Normal, variationSettings = androidx.compose.ui.text.font.FontVariation.Settings(androidx.compose.ui.text.font.FontVariation.weight(400))),
    Font(R.font.montserrat, FontWeight.Medium, variationSettings = androidx.compose.ui.text.font.FontVariation.Settings(androidx.compose.ui.text.font.FontVariation.weight(500))),
    Font(R.font.montserrat, FontWeight.SemiBold, variationSettings = androidx.compose.ui.text.font.FontVariation.Settings(androidx.compose.ui.text.font.FontVariation.weight(600))),
    Font(R.font.montserrat, FontWeight.Bold, variationSettings = androidx.compose.ui.text.font.FontVariation.Settings(androidx.compose.ui.text.font.FontVariation.weight(700))),
)
val FExo = FontFamily(Font(R.font.exo2))
val FSiete = FontFamily(Font(R.font.dseg7))
val FCatorce = FontFamily(Font(R.font.dseg14))

/** Lo que necesita cada tablero para dibujar texto. */
class Pincel(val tm: TextMeasurer)

/**
 * Lienzo de un tablero: se dibuja en las coordenadas de la imagen de referencia ([ancho] de ancho)
 * y se escala a la pantalla. Así cada posición sale directo de la foto original.
 */
@Composable
fun Lienzo(ancho: Float, modifier: Modifier, dibujo: DrawScope.(Pincel) -> Unit) {
    val p = Pincel(rememberTextMeasurer(cacheSize = 128))
    Canvas(modifier.clipToBounds()) {
        val s = size.width / ancho
        scale(s, s, pivot = Offset.Zero) { dibujo(p) }
    }
}

/** Punto sobre una elipse: [grados] como en el lienzo (0 = derecha, 90 = abajo). [k] estira en vertical. */
fun polar(c: Offset, r: Float, grados: Float, k: Float = 1f): Offset {
    val a = grados * PI.toFloat() / 180f
    return Offset(c.x + r * cos(a), c.y + r * sin(a) * k)
}

fun lerp(a: Float, b: Float, t: Float) = a + (b - a) * t

fun fraccion(v: Float, min: Float, max: Float) = ((v - min) / (max - min)).coerceIn(0f, 1f)

/** Arco de elipse como Path (para bandas, rellenos y trazos con brillo). */
fun arco(c: Offset, r: Float, desde: Float, hasta: Float, k: Float = 1f, pasos: Int = 90): Path = Path().apply {
    for (i in 0..pasos) {
        val p = polar(c, r, lerp(desde, hasta, i / pasos.toFloat()), k)
        if (i == 0) moveTo(p.x, p.y) else lineTo(p.x, p.y)
    }
}

/** Banda entre dos radios (un "anillo" parcial). */
fun banda(c: Offset, rIn: Float, rOut: Float, desde: Float, hasta: Float, k: Float = 1f, pasos: Int = 90): Path = Path().apply {
    for (i in 0..pasos) {
        val p = polar(c, rOut, lerp(desde, hasta, i / pasos.toFloat()), k)
        if (i == 0) moveTo(p.x, p.y) else lineTo(p.x, p.y)
    }
    for (i in pasos downTo 0) {
        val p = polar(c, rIn, lerp(desde, hasta, i / pasos.toFloat()), k)
        lineTo(p.x, p.y)
    }
    close()
}

/** Rayitas de una escala, de [desde] a [hasta] grados en [n] divisiones. */
fun DrawScope.rayas(
    c: Offset, rIn: Float, rOut: Float, desde: Float, hasta: Float, n: Int, color: Color, ancho: Float,
    k: Float = 1f, saltear: (Int) -> Boolean = { false }, colorDe: ((Int) -> Color)? = null,
) {
    for (i in 0..n) {
        if (saltear(i)) continue
        val g = lerp(desde, hasta, i / n.toFloat())
        drawLine(colorDe?.invoke(i) ?: color, polar(c, rIn, g, k), polar(c, rOut, g, k), ancho, StrokeCap.Butt)
    }
}

private val pinturaBrillo = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG)
private val filtros = HashMap<Int, BlurMaskFilter>()

/** Trazo o relleno difuminado: el "neón" de los tableros. */
fun DrawScope.brillo(path: Path, color: Color, radio: Float, ancho: Float = 0f) {
    drawIntoCanvas { c ->
        pinturaBrillo.reset()
        pinturaBrillo.isAntiAlias = true
        pinturaBrillo.style = if (ancho > 0f) android.graphics.Paint.Style.STROKE else android.graphics.Paint.Style.FILL
        pinturaBrillo.strokeWidth = ancho
        pinturaBrillo.strokeCap = android.graphics.Paint.Cap.ROUND
        pinturaBrillo.color = color.toArgb()
        pinturaBrillo.maskFilter = filtros.getOrPut(radio.toInt().coerceAtLeast(1)) {
            BlurMaskFilter(radio.coerceAtLeast(1f), BlurMaskFilter.Blur.NORMAL)
        }
        c.nativeCanvas.drawPath(path.asAndroidPath(), pinturaBrillo)
    }
}

fun linea(a: Offset, b: Offset) = Path().apply { moveTo(a.x, a.y); lineTo(b.x, b.y) }

/** Resplandor redondo barato (degradé radial). */
fun DrawScope.halo(c: Offset, r: Float, color: Color, alfa: Float = 1f) {
    if (alfa <= 0f) return
    drawCircle(Brush.radialGradient(listOf(color.copy(alpha = color.alpha * alfa), Color.Transparent), c, r), r, c)
}

/** Aguja afinada desde [c] hacia [grados], con brillo opcional. */
fun DrawScope.aguja(
    c: Offset, largo: Float, grados: Float, anchoBase: Float, anchoPunta: Float, color: Color,
    desde: Float = 0f, brilloColor: Color? = null, brilloRadio: Float = 0f, colorBase: Color? = null, k: Float = 1f,
) {
    val p0 = polar(c, desde, grados, k)
    val p1 = polar(c, largo, grados, k)
    val n = grados + 90f
    val b1 = polar(p0, anchoBase / 2, n); val b2 = polar(p0, -anchoBase / 2, n)
    val t1 = polar(p1, anchoPunta / 2, n); val t2 = polar(p1, -anchoPunta / 2, n)
    val path = Path().apply { moveTo(b1.x, b1.y); lineTo(t1.x, t1.y); lineTo(t2.x, t2.y); lineTo(b2.x, b2.y); close() }
    if (brilloColor != null && brilloRadio > 0f) brillo(path, brilloColor, brilloRadio)
    drawPath(path, if (colorBase != null) Brush.linearGradient(listOf(colorBase, color), p0, p1) else Brush.linearGradient(listOf(color, color)))
}

/**
 * Texto centrado en (x, y): [ancla] 0 = izquierda, 0.5 = centro, 1 = derecha; la altura se centra en las mayúsculas.
 * [brilloR] agrega un halo del mismo color (neón).
 */
fun DrawScope.texto(
    p: Pincel, s: String, x: Float, y: Float, tam: Float, color: Color, fuente: FontFamily,
    peso: FontWeight = FontWeight.Normal, ancla: Float = 0.5f, brilloR: Float = 0f, espaciado: Float = 0f,
    alfa: Float = 1f, brush: Brush? = null, colorBrillo: Color = color,
) {
    if (s.isEmpty() || alfa <= 0f) return
    val s = traducir(s)
    val f = density * fontScale
    val estilo = TextStyle(
        fontFamily = fuente, fontWeight = peso, fontSize = (tam / f).sp, letterSpacing = (espaciado / f).sp, color = color,
        shadow = if (brilloR > 0f) androidx.compose.ui.graphics.Shadow(colorBrillo, Offset.Zero, brilloR) else null,
    )
    val r = p.tm.measure(s, estilo, overflow = TextOverflow.Visible, softWrap = false, maxLines = 1)
    val tope = y - r.firstBaseline + tam * 0.36f
    val pos = Offset(x - r.size.width * ancla, tope)
    if (brush != null) drawText(r, brush = brush, topLeft = pos, alpha = alfa) else drawText(r, topLeft = pos, alpha = alfa)
}

private val traducidos = HashMap<String, String>()

/** Las etiquetas se traducen en inglés; se guardan para no buscarlas en cada cuadro. */
private fun traducir(s: String): String {
    if (!ar.garibaldi.obd.i18n.Idioma.ingles || s.none { it.isLetter() }) return s
    if (traducidos.size > 400) traducidos.clear()
    return traducidos.getOrPut(s) { ar.garibaldi.obd.i18n.Idioma.L(s) }
}

/** Ancho que ocupa un texto (para armar renglones con partes de distinto tamaño). */
fun DrawScope.anchoTexto(p: Pincel, s: String, tam: Float, fuente: FontFamily, peso: FontWeight = FontWeight.Normal): Float {
    val f = density * fontScale
    return p.tm.measure(s, TextStyle(fontFamily = fuente, fontWeight = peso, fontSize = (tam / f).sp), softWrap = false, maxLines = 1)
        .size.width.toFloat()
}

/**
 * Texto que sigue un arco centrado en [centro] grados. Arriba se lee en sentido horario; con [abajo] se lee al revés
 * (para la parte de abajo de un reloj o un costado que se lee hacia abajo).
 */
fun DrawScope.textoCurvo(
    p: Pincel, s: String, c: Offset, r: Float, centro: Float, tam: Float, color: Color, fuente: FontFamily,
    peso: FontWeight = FontWeight.Normal, espaciado: Float = 0f, abajo: Boolean = false,
) {
    val anchos = s.map { anchoTexto(p, it.toString(), tam, fuente, peso) + espaciado }
    val total = anchos.sum() / r * 180f / PI.toFloat()
    var g = if (abajo) centro + total / 2 else centro - total / 2
    s.forEachIndexed { i, ch ->
        val dg = anchos[i] / r * 180f / PI.toFloat()
        val medio = if (abajo) g - dg / 2 else g + dg / 2
        val pt = polar(c, r, medio)
        rotate(if (abajo) medio - 90f else medio + 90f, pt) { texto(p, ch.toString(), pt.x, pt.y, tam, color, fuente, peso) }
        g = if (abajo) g - dg else g + dg
    }
}

/** Centro de un texto para que su borde exterior quede justo en el radio [r] (no pisa las rayas de afuera). */
fun DrawScope.adentro(p: Pincel, s: String, c: Offset, r: Float, g: Float, tam: Float, fuente: FontFamily, peso: FontWeight): Offset {
    val rad = Math.toRadians(g.toDouble())
    val medio = anchoTexto(p, s, tam, fuente, peso) / 2f
    val rr = r - medio * kotlin.math.abs(kotlin.math.cos(rad)).toFloat() - tam * 0.38f * kotlin.math.abs(kotlin.math.sin(rad)).toFloat()
    return polar(c, rr, g)
}

/**
 * Bisel de metal (realista): cuerpo de metal cepillado con varios brillos, luz de arriba a la izquierda y sombra
 * abajo a la derecha, un filo claro por fuera, uno oscuro por dentro y una sombra que lo despega del fondo.
 */
fun DrawScope.biselMetal(c: Offset, r: Float, ancho: Float, tinte: Color = Color(0xFF8A8D92)) {
    drawCircle(Color(0x88000000), r + ancho / 2f + 3f, c, style = Stroke(6f))
    val metal = listOf(
        0xFF6E7176, 0xFFD8DBDF, 0xFF8A8D92, 0xFF4A4D52, 0xFFB8BBC0, 0xFFF4F5F6, 0xFF7A7D82, 0xFF54575C, 0xFFC8CBD0, 0xFF6E7176,
    ).map { androidx.compose.ui.graphics.lerp(Color(it), tinte, 0.15f) }
    drawCircle(Brush.sweepGradient(metal, c), r, c, style = Stroke(ancho))
    drawCircle(
        Brush.linearGradient(listOf(Color(0x77FFFFFF), Color(0x10FFFFFF), Color(0x00000000), Color(0x77000000)), Offset(c.x - r, c.y - r), Offset(c.x + r, c.y + r)),
        r, c, style = Stroke(ancho),
    )
    drawCircle(Color(0xCCFFFFFF), r + ancho / 2f - 1f, c, style = Stroke(1.5f))
    drawCircle(Color(0xDD141416), r - ancho / 2f + 1f, c, style = Stroke(2.5f))
}

/** Texto girado alrededor de su centro. */
fun DrawScope.textoGirado(
    p: Pincel, s: String, x: Float, y: Float, tam: Float, color: Color, fuente: FontFamily, grados: Float,
    peso: FontWeight = FontWeight.Normal, brilloR: Float = 0f, espaciado: Float = 0f,
) {
    rotate(grados, Offset(x, y)) { texto(p, s, x, y, tam, color, fuente, peso, brilloR = brilloR, espaciado = espaciado) }
}

// ── Testigos del tablero, dibujados con trazos ───────────────────────────────

enum class Testigo { GIRO_IZQ, GIRO_DER, ALTAS, BAJAS, ACEITE, BATERIA, TEMPERATURA, NAFTA, FRENO, CINTURON, MOTOR, COPO, ADVERTENCIA, AIRBAG, NIEBLA, LIMPIA, LAVA, PUERTA }

/**
 * Dibuja un testigo en un cuadro de [tam] centrado en (x, y). Apagado se ve casi invisible, como en un tablero real.
 */
fun DrawScope.testigo(t: Testigo, x: Float, y: Float, tam: Float, color: Color, encendido: Boolean, brilloR: Float = tam * 0.35f) {
    val alfa = if (encendido) 1f else 0.12f
    val col = color.copy(alpha = alfa)
    val o = Offset(x - tam / 2, y - tam / 2)
    fun q(px: Float, py: Float) = Offset(o.x + px * tam, o.y + py * tam)
    val w = tam * 0.085f
    val trazo = Stroke(w, cap = StrokeCap.Round)
    val path = Path()
    when (t) {
        Testigo.GIRO_IZQ, Testigo.GIRO_DER -> {
            val pts = listOf(0.02f to 0.5f, 0.45f to 0.12f, 0.45f to 0.33f, 0.98f to 0.33f, 0.98f to 0.67f, 0.45f to 0.67f, 0.45f to 0.88f)
            pts.forEachIndexed { i, (a, b) ->
                val xx = if (t == Testigo.GIRO_DER) 1f - a else a
                val pt = q(xx, b); if (i == 0) path.moveTo(pt.x, pt.y) else path.lineTo(pt.x, pt.y)
            }
            path.close()
            if (encendido) brillo(path, color, brilloR)
            drawPath(path, col)
            return
        }
        Testigo.ALTAS, Testigo.BAJAS, Testigo.NIEBLA -> {
            path.moveTo(q(0.55f, 0.18f).x, q(0.55f, 0.18f).y)
            path.cubicTo(q(1.02f, 0.18f).x, q(1.02f, 0.18f).y, q(1.02f, 0.82f).x, q(1.02f, 0.82f).y, q(0.55f, 0.82f).x, q(0.55f, 0.82f).y)
            path.close()
            for (i in 0..3) {
                val yy = 0.25f + i * 0.17f
                val dy = when (t) { Testigo.BAJAS -> 0.08f; Testigo.NIEBLA -> if (i % 2 == 0) 0.05f else -0.05f; else -> 0f }
                path.moveTo(q(0.08f, yy + dy).x, q(0.08f, yy + dy).y); path.lineTo(q(0.42f, yy).x, q(0.42f, yy).y)
            }
        }
        Testigo.ACEITE -> {
            path.moveTo(q(0.1f, 0.45f).x, q(0.1f, 0.45f).y); path.lineTo(q(0.3f, 0.52f).x, q(0.3f, 0.52f).y)
            path.lineTo(q(0.3f, 0.78f).x, q(0.3f, 0.78f).y); path.lineTo(q(0.75f, 0.78f).x, q(0.75f, 0.78f).y)
            path.lineTo(q(0.95f, 0.5f).x, q(0.95f, 0.5f).y); path.lineTo(q(0.62f, 0.55f).x, q(0.62f, 0.55f).y)
            path.lineTo(q(0.5f, 0.45f).x, q(0.5f, 0.45f).y); path.lineTo(q(0.3f, 0.45f).x, q(0.3f, 0.45f).y)
            path.moveTo(q(0.42f, 0.45f).x, q(0.42f, 0.45f).y); path.lineTo(q(0.42f, 0.33f).x, q(0.42f, 0.33f).y)
            path.moveTo(q(0.33f, 0.33f).x, q(0.33f, 0.33f).y); path.lineTo(q(0.52f, 0.33f).x, q(0.52f, 0.33f).y)
            path.moveTo(q(0.98f, 0.62f).x, q(0.98f, 0.62f).y); path.lineTo(q(0.98f, 0.72f).x, q(0.98f, 0.72f).y)
        }
        Testigo.BATERIA -> {
            path.addRect(Rect(q(0.08f, 0.3f), q(0.92f, 0.82f)))
            path.moveTo(q(0.22f, 0.3f).x, q(0.22f, 0.3f).y); path.lineTo(q(0.22f, 0.2f).x, q(0.22f, 0.2f).y)
            path.moveTo(q(0.78f, 0.3f).x, q(0.78f, 0.3f).y); path.lineTo(q(0.78f, 0.2f).x, q(0.78f, 0.2f).y)
            path.moveTo(q(0.2f, 0.56f).x, q(0.2f, 0.56f).y); path.lineTo(q(0.36f, 0.56f).x, q(0.36f, 0.56f).y)
            path.moveTo(q(0.64f, 0.56f).x, q(0.64f, 0.56f).y); path.lineTo(q(0.8f, 0.56f).x, q(0.8f, 0.56f).y)
            path.moveTo(q(0.72f, 0.48f).x, q(0.72f, 0.48f).y); path.lineTo(q(0.72f, 0.64f).x, q(0.72f, 0.64f).y)
        }
        Testigo.TEMPERATURA -> {
            path.moveTo(q(0.5f, 0.08f).x, q(0.5f, 0.08f).y); path.lineTo(q(0.5f, 0.66f).x, q(0.5f, 0.66f).y)
            path.addOval(Rect(q(0.38f, 0.66f), q(0.62f, 0.9f)))
            for (i in 0..2) { val yy = 0.2f + i * 0.14f; path.moveTo(q(0.5f, yy).x, q(0.5f, yy).y); path.lineTo(q(0.72f, yy).x, q(0.72f, yy).y) }
            path.moveTo(q(0.12f, 0.92f).x, q(0.12f, 0.92f).y); path.lineTo(q(0.3f, 0.86f).x, q(0.3f, 0.86f).y)
            path.moveTo(q(0.7f, 0.86f).x, q(0.7f, 0.86f).y); path.lineTo(q(0.88f, 0.92f).x, q(0.88f, 0.92f).y)
        }
        Testigo.NAFTA -> {
            path.addRect(Rect(q(0.15f, 0.12f), q(0.6f, 0.9f)))
            path.moveTo(q(0.22f, 0.4f).x, q(0.22f, 0.4f).y); path.lineTo(q(0.53f, 0.4f).x, q(0.53f, 0.4f).y)
            path.moveTo(q(0.6f, 0.3f).x, q(0.6f, 0.3f).y); path.lineTo(q(0.78f, 0.4f).x, q(0.78f, 0.4f).y)
            path.lineTo(q(0.78f, 0.75f).x, q(0.78f, 0.75f).y); path.lineTo(q(0.88f, 0.75f).x, q(0.88f, 0.75f).y)
            path.lineTo(q(0.88f, 0.3f).x, q(0.88f, 0.3f).y)
        }
        Testigo.FRENO -> {
            path.addOval(Rect(q(0.22f, 0.15f), q(0.78f, 0.85f)))
            path.moveTo(q(0.12f, 0.2f).x, q(0.12f, 0.2f).y); path.quadraticTo(q(-0.05f, 0.5f).x, q(-0.05f, 0.5f).y, q(0.12f, 0.8f).x, q(0.12f, 0.8f).y)
            path.moveTo(q(0.88f, 0.2f).x, q(0.88f, 0.2f).y); path.quadraticTo(q(1.05f, 0.5f).x, q(1.05f, 0.5f).y, q(0.88f, 0.8f).x, q(0.88f, 0.8f).y)
            path.moveTo(q(0.5f, 0.3f).x, q(0.5f, 0.3f).y); path.lineTo(q(0.5f, 0.56f).x, q(0.5f, 0.56f).y)
            path.moveTo(q(0.5f, 0.68f).x, q(0.5f, 0.68f).y); path.lineTo(q(0.5f, 0.7f).x, q(0.5f, 0.7f).y)
        }
        Testigo.CINTURON, Testigo.AIRBAG -> {
            path.addOval(Rect(q(0.38f, 0.05f), q(0.62f, 0.27f)))
            path.moveTo(q(0.3f, 0.95f).x, q(0.3f, 0.95f).y); path.lineTo(q(0.3f, 0.45f).x, q(0.3f, 0.45f).y)
            path.quadraticTo(q(0.5f, 0.3f).x, q(0.5f, 0.3f).y, q(0.7f, 0.45f).x, q(0.7f, 0.45f).y); path.lineTo(q(0.7f, 0.95f).x, q(0.7f, 0.95f).y)
            if (t == Testigo.CINTURON) { path.moveTo(q(0.36f, 0.4f).x, q(0.36f, 0.4f).y); path.lineTo(q(0.66f, 0.85f).x, q(0.66f, 0.85f).y) }
            else path.addOval(Rect(q(0.55f, 0.35f), q(0.98f, 0.75f)))
        }
        Testigo.MOTOR -> {
            path.moveTo(q(0.15f, 0.35f).x, q(0.15f, 0.35f).y); path.lineTo(q(0.15f, 0.75f).x, q(0.15f, 0.75f).y)
            path.moveTo(q(0.15f, 0.55f).x, q(0.15f, 0.55f).y); path.lineTo(q(0.25f, 0.55f).x, q(0.25f, 0.55f).y)
            path.addRect(Rect(q(0.25f, 0.35f), q(0.78f, 0.78f)))
            path.moveTo(q(0.4f, 0.35f).x, q(0.4f, 0.35f).y); path.lineTo(q(0.4f, 0.22f).x, q(0.4f, 0.22f).y)
            path.moveTo(q(0.3f, 0.22f).x, q(0.3f, 0.22f).y); path.lineTo(q(0.6f, 0.22f).x, q(0.6f, 0.22f).y)
            path.moveTo(q(0.78f, 0.5f).x, q(0.78f, 0.5f).y); path.lineTo(q(0.92f, 0.42f).x, q(0.92f, 0.42f).y)
            path.lineTo(q(0.92f, 0.72f).x, q(0.92f, 0.72f).y); path.lineTo(q(0.78f, 0.65f).x, q(0.78f, 0.65f).y)
        }
        Testigo.COPO -> {
            val c = q(0.5f, 0.5f)
            for (i in 0 until 3) {
                val g = i * 60f + 90f
                val a = polar(c, tam * 0.45f, g); val b = polar(c, tam * 0.45f, g + 180f)
                path.moveTo(a.x, a.y); path.lineTo(b.x, b.y)
                for (s in listOf(g, g + 180f)) {
                    val m = polar(c, tam * 0.3f, s)
                    val r1 = polar(m, tam * 0.12f, s + 140f); val r2 = polar(m, tam * 0.12f, s - 140f)
                    path.moveTo(r1.x, r1.y); path.lineTo(m.x, m.y); path.lineTo(r2.x, r2.y)
                }
            }
        }
        Testigo.ADVERTENCIA -> {
            path.moveTo(q(0.5f, 0.08f).x, q(0.5f, 0.08f).y); path.lineTo(q(0.95f, 0.88f).x, q(0.95f, 0.88f).y)
            path.lineTo(q(0.05f, 0.88f).x, q(0.05f, 0.88f).y); path.close()
            path.moveTo(q(0.5f, 0.38f).x, q(0.5f, 0.38f).y); path.lineTo(q(0.5f, 0.62f).x, q(0.5f, 0.62f).y)
            path.moveTo(q(0.5f, 0.74f).x, q(0.5f, 0.74f).y); path.lineTo(q(0.5f, 0.76f).x, q(0.5f, 0.76f).y)
        }
        Testigo.LIMPIA, Testigo.LAVA -> {
            path.moveTo(q(0.05f, 0.75f).x, q(0.05f, 0.75f).y)
            path.quadraticTo(q(0.5f, 0.05f).x, q(0.5f, 0.05f).y, q(0.95f, 0.75f).x, q(0.95f, 0.75f).y)
            path.quadraticTo(q(0.5f, 0.6f).x, q(0.5f, 0.6f).y, q(0.05f, 0.75f).x, q(0.05f, 0.75f).y)
            if (t == Testigo.LIMPIA) { path.moveTo(q(0.5f, 0.78f).x, q(0.5f, 0.78f).y); path.lineTo(q(0.7f, 0.25f).x, q(0.7f, 0.25f).y) }
            else for (i in 0..2) { val xx = 0.35f + i * 0.15f; path.moveTo(q(xx, 0.45f).x, q(xx, 0.45f).y); path.lineTo(q(xx, 0.3f).x, q(xx, 0.3f).y) }
        }
        Testigo.PUERTA -> {
            path.moveTo(q(0.05f, 0.7f).x, q(0.05f, 0.7f).y); path.lineTo(q(0.2f, 0.45f).x, q(0.2f, 0.45f).y)
            path.lineTo(q(0.65f, 0.42f).x, q(0.65f, 0.42f).y); path.lineTo(q(0.95f, 0.55f).x, q(0.95f, 0.55f).y)
            path.lineTo(q(0.95f, 0.72f).x, q(0.95f, 0.72f).y); path.close()
            path.addOval(Rect(q(0.18f, 0.66f), q(0.34f, 0.82f))); path.addOval(Rect(q(0.68f, 0.66f), q(0.84f, 0.82f)))
            path.moveTo(q(0.6f, 0.43f).x, q(0.6f, 0.43f).y); path.lineTo(q(0.78f, 0.22f).x, q(0.78f, 0.22f).y)
        }
    }
    if (encendido) brillo(path, color, brilloR, w * 1.6f)
    drawPath(path, col, style = trazo)
}

/** Cuadrado redondeado con borde (cajas de datos de algunos tableros). */
fun DrawScope.caja(x: Float, y: Float, w: Float, h: Float, fondo: Brush, borde: Color? = null, radio: Float = 10f, anchoBorde: Float = 2f) {
    drawRoundRect(fondo, Offset(x, y), Size(w, h), androidx.compose.ui.geometry.CornerRadius(radio))
    if (borde != null) drawRoundRect(borde, Offset(x, y), Size(w, h), androidx.compose.ui.geometry.CornerRadius(radio), style = Stroke(anchoBorde))
}
