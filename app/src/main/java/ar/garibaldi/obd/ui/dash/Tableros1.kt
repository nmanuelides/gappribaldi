package ar.garibaldi.obd.ui.dash

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import ar.garibaldi.obd.ui.dash.DatosTablero.Companion.num
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin

/** Foto del auto y logo de la marca, para los tableros que los muestran. */
class Recursos(val foto: ImageBitmap?, val logo: ImageBitmap?)

/** Ángulo real (en grados) de la dirección hacia un punto de la elipse, para que la aguja apunte a la raya. */
fun anguloElipse(g: Float, k: Float): Float {
    val a = Math.toRadians(g.toDouble())
    return Math.toDegrees(atan2(sin(a) * k, cos(a))).toFloat()
}

/** Dibuja una imagen ajustada dentro de un cuadro, centrada en [c]. */
fun DrawScope.foto(img: ImageBitmap, c: Offset, anchoMax: Float, altoMax: Float, filtro: ColorFilter? = null, alfa: Float = 1f) {
    val esc = minOf(anchoMax / img.width, altoMax / img.height)
    val w = img.width * esc; val h = img.height * esc
    drawImage(
        img, IntOffset.Zero, IntSize(img.width, img.height),
        IntOffset((c.x - w / 2).toInt(), (c.y - h / 2).toInt()), IntSize(w.toInt(), h.toInt()),
        alpha = alfa, colorFilter = filtro,
    )
}

/** Tiñe la foto como un holograma de un color (pasa a grises y la colorea). */
fun holograma(r: Float, g: Float, b: Float, suma: Float) = ColorFilter.colorMatrix(
    ColorMatrix(
        floatArrayOf(
            0.3f * r, 0.59f * r, 0.11f * r, 0f, suma * 0.2f,
            0.3f * g, 0.59f * g, 0.11f * g, 0f, suma * 0.5f,
            0.3f * b, 0.59f * b, 0.11f * b, 0f, suma,
            0f, 0f, 0f, 1f, 0f,
        )
    )
)

// ═════════════════════════════════════════════════════════════════════════════
// 1 · Neón de los 90 (Optitron): azul marino, rayas cian y agujas rojas
// ═════════════════════════════════════════════════════════════════════════════

@Composable
fun TableroNeon(d: DatosTablero, r: Recursos, modifier: Modifier) = Lienzo(2000f, modifier) { p ->
    drawRect(Color.Black, size = Size(2000f, 932f))
    // Colores medidos de la referencia: banda índigo oscuro, rayas cian verdoso, números blanco azulado
    val cian = Color(0xFF12EEE2)
    val borde = Color(0xFF00B4BE)
    val blanco = Color(0xFFE8F0FF)

    fun reloj(c: Offset, etiquetas: List<String>, sub: Int, frac: Float, titulo: String, tamEt: Float) {
        val k = 1f; val rIn = 222f; val rOut = 264f
        drawPath(banda(c, rIn, rOut, 180f, 360f, k), Brush.radialGradient(0f to Color.Transparent, rIn / rOut to Color(0xFF050026), 0.8f to Color(0xFF05043A), 1f to Color(0xFF071C4E), center = c, radius = rOut))
        val aro = arco(c, rOut, 178f, 362f, k)
        brillo(aro, borde.copy(alpha = 0.8f), 8f, 5f)
        drawPath(aro, borde, style = Stroke(3f))
        val n = (etiquetas.size - 1) * sub
        for (i in 0..n) {
            val g = 180f + 180f * i / n
            val mayor = i % sub == 0
            val a = polar(c, rOut - 3f, g, k); val b = polar(c, rOut - if (mayor) 44f else 24f, g, k)
            val ancho = if (mayor) 9f else 6f
            brillo(linea(a, b), cian.copy(alpha = 0.7f), 6f, ancho)
            drawLine(cian, a, b, ancho)
        }
        // Cada número apoya su borde interno sobre el mismo círculo, así no se pisan ni se deforman
        etiquetas.forEachIndexed { i, s ->
            val g = 180f + 180f * i / (etiquetas.size - 1)
            val rad = Math.toRadians(g.toDouble())
            val medio = anchoTexto(p, s, tamEt, FRajdhani, FontWeight.Medium) / 2f
            val r = 290f + medio * kotlin.math.abs(cos(rad)).toFloat() + tamEt * 0.38f * kotlin.math.abs(sin(rad)).toFloat()
            val pt = polar(c, r, g)
            texto(p, s, pt.x, pt.y, tamEt, blanco, FRajdhani, FontWeight.Medium, brilloR = 5f, colorBrillo = cian)
        }
        texto(p, titulo, c.x, c.y - 150f, 38f, blanco, FBarlow, FontWeight.Normal, brilloR = 4f, colorBrillo = cian, espaciado = 3f)
        val hub = c
        aguja(hub, 192f, anguloElipse(180f + 180f * frac, k), 13f, 5f, Color(0xFFFF2424), brilloColor = Color(0xFFFF0000), brilloRadio = 16f, colorBase = Color(0xFF7A0000))
        halo(hub, 34f, Color.White, 0.8f)
        drawCircle(Color.White, 11f, hub)
    }

    reloj(Offset(627f, 575f), (0..6).map { "$it" }, 2, d.rpmEn(6000f) / 6000f, "x 1000", 58f)
    reloj(Offset(1345f, 575f), (0..10).map { "${it * 20}" }, 2, d.velEn(200f) / 200f, d.uVel, 46f)

    // Relojes chicos: temperatura (izquierda) y nafta (derecha)
    fun chico(c: Offset, desde: Float, hasta: Float, frac: Float, etiquetas: List<Pair<String, Float>>) {
        drawPath(banda(c, 122f, 150f, desde, hasta), Brush.radialGradient(0f to Color.Transparent, 0.81f to Color(0xFF050026), 1f to Color(0xFF0A1C66), center = c, radius = 150f))
        val aro = arco(c, 150f, desde, hasta)
        brillo(aro, borde, 6f, 4f); drawPath(aro, borde, style = Stroke(3f))
        rayas(c, 128f, 150f, desde, hasta, 6, cian, 5f)
        for ((s, f) in etiquetas) {
            val pt = polar(c, 180f, lerp(desde, hasta, f))
            texto(p, s, pt.x, pt.y, 32f, cian, FRajdhani, FontWeight.Medium, brilloR = 4f)
        }
        val g = lerp(desde, hasta, frac)
        aguja(c, 128f, g, 9f, 4f, Color.White, desde = 0f, brilloColor = Color.White, brilloRadio = 14f)
        drawCircle(Color.White, 8f, c)
    }
    chico(Offset(240f, 430f), 180f, 270f, fraccion(d.deA(d.refrigerante, 130f, 40f), 40f, 130f), listOf("40" to 0f, "85" to 0.5f, "130" to 1f))
    chico(Offset(1760f, 430f), 360f, 270f, d.deA(d.nafta, 100f) / 100f, listOf("E" to 0f, "F" to 1f))

    // Testigos
    val enc = d.prueba
    testigo(Testigo.GIRO_IZQ, 812f, 82f, 84f, Color(0xFF3CF03C), enc)
    testigo(Testigo.GIRO_DER, 1132f, 82f, 84f, Color(0xFF3CF03C), enc)
    testigo(Testigo.ALTAS, 975f, 82f, 64f, Color(0xFF2E6BFF), enc)
    testigo(Testigo.ACEITE, 928f, 178f, 82f, Color(0xFFFFA020), enc || (!d.motorEnMarcha && d.tension != null))
    testigo(Testigo.BATERIA, 1032f, 178f, 66f, Color(0xFFFF2A2A), enc || (!d.motorEnMarcha && d.tension != null) || (d.motorEnMarcha && (d.tension ?: 14f) < 12.4f))
    testigo(Testigo.TEMPERATURA, 980f, 285f, 66f, Color(0xFFFF2A2A), enc || (d.refrigerante ?: 0f) > 112f)
    texto(p, "4WD", 978f, 368f, 40f, Color(0xFFFFA020), FOrbitron, brilloR = 8f, alfa = if (enc) 1f else 0.12f)
    testigo(Testigo.AIRBAG, 290f, 180f, 74f, Color(0xFFFF2A2A), enc)
    testigo(Testigo.FRENO, 1745f, 180f, 80f, Color(0xFFFF2A2A), enc)
    testigo(Testigo.NAFTA, 1722f, 345f, 56f, Color(0xFFFF2A2A), enc || (d.nafta ?: 100f) < 12f)
    testigo(Testigo.MOTOR, 975f, 455f, 60f, Color(0xFFFFA020), enc || d.testigoMotor)
    // Temperatura exterior junto al reloj de agua
    testigo(Testigo.TEMPERATURA, 268f, 472f, 46f, Color(0xFFFF2A2A), true, brilloR = 8f)
    texto(p, num(d.ambiente) + "°", 312f, 478f, 36f, cian, FRajdhani, FontWeight.Medium, ancla = 0f, brilloR = 4f)

    // Palanca y cuentakilómetros
    caja(488f, 650f, 70f, 70f, Brush.verticalGradient(listOf(Color(0xFF0E3C8A), Color(0xFF082660))), Color(0xFF2E8BFF), 10f, 3f)
    halo(Offset(523f, 685f), 70f, Color(0xFF1E6BFF), 0.6f)
    texto(p, d.palanca.toString(), 523f, 686f, 52f, Color(0xFF5FB4FF), FBarlow, FontWeight.SemiBold, brilloR = 10f)
    caja(715f, 672f, 230f, 58f, Brush.verticalGradient(listOf(Color(0xFF06363E), Color(0xFF042428))), radio = 4f)
    caja(1050f, 672f, 165f, 58f, Brush.verticalGradient(listOf(Color(0xFF06363E), Color(0xFF042428))), radio = 4f)
    texto(p, num(d.odometro, guion = "-----").padStart(6, '!'), 932f, 702f, 40f, Color(0xFF2FD2DA), FSiete, ancla = 1f, brilloR = 6f)
    texto(p, num(d.viajeKm, 1), 1200f, 702f, 40f, Color(0xFF2FD2DA), FSiete, ancla = 1f, brilloR = 6f)

    // Testigos de abajo
    testigo(Testigo.BAJAS, 88f, 808f, 66f, Color(0xFF2F8BFF), enc)
    testigo(Testigo.COPO, 198f, 808f, 62f, Color(0xFF3FA0FF), enc || (d.ambiente ?: 20f) < 3f)
    testigo(Testigo.PUERTA, 312f, 808f, 82f, Color(0xFFFFA020), enc)
    testigo(Testigo.LAVA, 1662f, 805f, 66f, Color(0xFF2F8BFF), enc)
    testigo(Testigo.LIMPIA, 1767f, 805f, 66f, Color(0xFF2F8BFF), enc)
    testigo(Testigo.NIEBLA, 1875f, 805f, 66f, Color(0xFF2F8BFF), enc)
}

// ═════════════════════════════════════════════════════════════════════════════
// 2 · Gran turismo naranja: velocímetro central, mapa a la izquierda y menú a la derecha
// ═════════════════════════════════════════════════════════════════════════════

/** Calles inventadas para el mapa decorativo (se repiten cada 900 px y corren con los km del viaje). */
private val CALLES = listOf(
    floatArrayOf(-50f, 120f, 700f, 40f), floatArrayOf(-50f, 330f, 750f, 260f), floatArrayOf(-50f, 560f, 700f, 520f),
    floatArrayOf(120f, -50f, 60f, 950f), floatArrayOf(380f, -50f, 300f, 950f), floatArrayOf(560f, -50f, 640f, 950f),
    floatArrayOf(-50f, 760f, 650f, 700f), floatArrayOf(200f, 200f, 520f, 640f), floatArrayOf(40f, 880f, 600f, 820f),
)
private val NOMBRES_CALLES = listOf("Av. Cabildo", "Juramento", "Echeverría", "Olazábal", "Av. Libertador")

@Composable
fun TableroGranTurismo(d: DatosTablero, r: Recursos, modifier: Modifier) = Lienzo(2000f, modifier) { p ->
    val naranja = Color(0xFFFF6A1F)
    val gris = Color(0xFF6E6A68)
    // Fondo negro neutro (medido: #030303 a #0C0C0C)
    drawRect(Brush.radialGradient(listOf(Color(0xFF0E0E0D), Color(0xFF080808), Color(0xFF040404)), Offset(1000f, 420f), 1300f), size = Size(2000f, 853f))

    // Mapa: calles que corren hacia abajo con los km del viaje
    val corrimiento = (d.viajeKm * 2200f) % 900f
    clipRect(0f, 0f, 640f, 853f) {
        for (rep in -1..1) translate(0f, corrimiento + rep * 900f) {
            for (c in CALLES) drawLine(Color(0xFF4A2410).copy(alpha = 0.55f), Offset(c[0], c[1]), Offset(c[2], c[3]), 6f, StrokeCap.Round)
        }
        val ruta = Path().apply { moveTo(250f, 900f); lineTo(250f, 640f); quadraticTo(255f, 420f, 420f, 300f); lineTo(700f, 60f) }
        brillo(ruta, naranja, 18f, 26f)
        drawPath(ruta, naranja, style = Stroke(22f, cap = StrokeCap.Round))
        NOMBRES_CALLES.forEachIndexed { i, n ->
            val pos = listOf(Offset(118f, 352f), Offset(355f, 334f), Offset(190f, 446f), Offset(390f, 658f), Offset(255f, 786f))[i]
            val w = anchoTexto(p, n, 30f, FBarlow) + 24f
            caja(pos.x - (if (i == 4) w / 2 else 12f), pos.y - 24f, w, 48f, Brush.verticalGradient(listOf(Color(0xFF3A3634), Color(0xFF2A2725))).let { it }, radio = 4f)
            texto(p, n, pos.x + (if (i == 4) 0f else w / 2 - 12f), pos.y, 30f, Color(0xFFE6E2DE), FBarlow, FontWeight.Medium)
        }
        // Marcador del auto
        drawCircle(Color(0xFFE0B080), 64f, Offset(253f, 688f))
        drawCircle(Color(0xFFB8541C), 56f, Offset(253f, 688f))
        caja(237f, 652f, 32f, 72f, Brush.verticalGradient(listOf(Color(0xFF1A1A1A), Color(0xFF3A3A3A))), radio = 10f)
    }
    drawRect(Brush.horizontalGradient(listOf(Color.Transparent, Color(0xFF080808)), 420f, 640f), Offset(420f, 0f), Size(220f, 853f))

    // Velocímetro
    val c = Offset(975f, 425f)
    val v = d.velEn(300f)
    val gv = 135f + 270f * (v / 300f).coerceIn(0f, 1f)
    drawArc(Color(0xFFF2F2F2), 118f, 304f, false, Offset(c.x - 392f, c.y - 392f), Size(784f, 784f), style = Stroke(7f))
    // Como en la referencia: una raya cada 5 km/h, la de cada número más larga
    for (i in 0..60) {
        val g = 135f + 270f * i / 60f
        val activo = g <= gv
        val largo = if (i % 4 == 0) 24f else 12f
        drawLine(if (activo) naranja else gris.copy(alpha = 0.7f), polar(c, 352f, g), polar(c, 352f - largo, g), if (i % 4 == 0) 4f else 2.5f)
    }
    for (i in 0..15) {
        val g = 135f + 270f * i / 15f
        val pt = polar(c, 300f, g)
        texto(p, "${i * 20}", pt.x, pt.y, 44f, if (g <= gv + 1f) naranja else Color(0xFF4A4644), FBarlow, FontWeight.Medium)
    }
    // Anillo interior encendido
    val anillo = arco(c, 225f, 0f, 360f, pasos = 120)
    brillo(anillo, naranja, 30f, 14f)
    drawCircle(naranja, 225f, c, style = Stroke(4f))
    drawCircle(Brush.radialGradient(listOf(Color(0xFF1A0E08), Color(0xFF0E0806)), c, 222f), 221f, c)
    // Aguja: línea fina naranja
    // La aguja nace cerca del centro (al lado del círculo de la marcha) y cruza el anillo encendido
    val a1 = polar(c, 58f, gv); val a2 = polar(c, 345f, gv)
    brillo(linea(a1, a2), naranja, 10f, 6f)
    drawLine(Color(0xFFFF9A50), a1, a2, 4f)
    texto(p, num(d.vel), c.x, 296f, 132f, naranja, FExo, FontWeight.SemiBold, brilloR = 18f)
    texto(p, d.uVel, 1112f, 330f, 26f, Color(0xFF8A6A58), FBarlow)
    // Palanca
    texto(p, "P R N", 858f, 425f, 40f, Color(0xFF7A7470), FBarlow, FontWeight.Medium, espaciado = 6f)
    drawCircle(Color.White, 48f, Offset(975f, 425f))
    texto(p, d.palanca.toString(), 975f, 426f, 62f, Color.Black, FBarlow, FontWeight.SemiBold)
    texto(p, d.modo, 975f, 545f, 30f, naranja, FBarlow, FontWeight.SemiBold, espaciado = 2f)
    val ms = ((d.viajeSeg * 1000).toLong() % 1000).toInt()
    val s = d.viajeSeg.toLong()
    texto(p, String.format(java.util.Locale.US, "%02d:%02d:%03d", s / 60 % 100, s % 60, ms), 975f, 590f, 30f, Color.White, FBarlow, FontWeight.Medium, espaciado = 2f)
    texto(p, num(d.autonomia), 975f, 712f, 56f, Color.White, FBarlow, FontWeight.Medium)
    texto(p, "km", 1150f, 712f, 26f, Color(0xFFBBBBBB), FBarlow)
    testigo(Testigo.BATERIA, 785f, 712f, 40f, Color(0xFFDDDDDD), true, 0f)

    // Menú derecho: lo que muestra cambia cada 6 segundos
    val opciones = listOf("Viaje", "Motor", "Consumo", "Eléctrico")
    val sel = ((System.currentTimeMillis() / 6000) % opciones.size).toInt()
    opciones.forEachIndexed { i, o ->
        val y = 198f + i * 48f
        if (i == sel) {
            val tri = Path().apply { moveTo(1510f, y - 12f); lineTo(1528f, y); lineTo(1510f, y + 12f); close() }
            drawPath(tri, naranja)
        }
        texto(p, o, 1546f, y, 36f, naranja, FBarlow, FontWeight.Medium, ancla = 0f, alfa = if (i == sel) 1f else 0.45f, brilloR = if (i == sel) 6f else 0f)
    }
    // "Tapa del disco": la foto del auto
    caja(1546f, 408f, 242f, 242f, Brush.linearGradient(listOf(Color(0xFFD03A7A), Color(0xFF5A8AB0)), Offset(1546f, 408f), Offset(1788f, 650f)), radio = 2f)
    r.foto?.let { foto(it, Offset(1667f, 540f), 230f, 150f) }
    texto(p, d.marca.uppercase(), 1667f, 440f, 22f, Color.White, FBarlow, FontWeight.SemiBold, espaciado = 4f)
    val (valor, unidad) = when (sel) {
        0 -> num(d.viajeKm, 1) to "km"
        1 -> num(d.refrigerante) to "°C"
        2 -> num(d.consumo ?: d.litrosHora, 1) to if (d.consumo != null) "l/100" else "l/h"
        else -> num(d.tension, 1) to "V"
    }
    texto(p, valor, 1830f, 530f, 72f, naranja, FExo, FontWeight.Medium, ancla = 0f, brilloR = 8f)
    texto(p, unidad, 1834f, 585f, 26f, Color(0xFFB07050), FBarlow, ancla = 0f)
    texto(p, d.marca, 1546f, 698f, 38f, naranja, FBarlow, FontWeight.Medium, ancla = 0f)
    texto(p, d.modelo + " · " + DatosTablero.hora(), 1546f, 738f, 30f, naranja, FBarlow, ancla = 0f, alfa = 0.8f)
    val tri = Path().apply { moveTo(1548f, 772f); lineTo(1580f, 792f); lineTo(1548f, 812f); close() }
    drawPath(tri, naranja, style = Stroke(3f))
    drawLine(Color(0xFF3A2A22), Offset(1612f, 792f), Offset(2000f, 792f), 4f)
    val fn = d.deA(d.nafta, 100f) / 100f
    drawLine(naranja, Offset(1612f, 792f), Offset(lerp(1612f, 2000f, fn), 792f), 4f)
    drawCircle(naranja, 9f, Offset(lerp(1612f, 2000f, fn), 792f))
    texto(p, num(d.nafta) + "%", 1720f, 815f, 24f, naranja, FBarlow)
}

// ═════════════════════════════════════════════════════════════════════════════
// 3 · Cabina virtual: dos relojes redondos y mapa claro al medio
// ═════════════════════════════════════════════════════════════════════════════

private val CALLES_CLARAS = listOf(
    floatArrayOf(0f, 40f, 760f, 130f, 26f), floatArrayOf(150f, -20f, 260f, 720f, 10f), floatArrayOf(420f, -20f, 380f, 720f, 30f),
    floatArrayOf(0f, 260f, 760f, 300f, 10f), floatArrayOf(560f, -20f, 700f, 720f, 12f), floatArrayOf(0f, 470f, 760f, 430f, 10f),
    floatArrayOf(60f, 720f, 330f, -20f, 18f), floatArrayOf(480f, 160f, 740f, 520f, 8f),
)

@Composable
fun TableroCabinaVirtual(d: DatosTablero, r: Recursos, modifier: Modifier) = Lienzo(2000f, modifier) { p ->
    drawRect(Color(0xFF050505), size = Size(2000f, 698f))
    // Mapa al medio
    // El mapa ocupa todo el centro y sigue por detrás de los dos relojes, como en la referencia
    val mapa = Rect(420f, 70f, 1580f, 698f)
    clipRect(mapa.left, mapa.top, mapa.right, mapa.bottom) {
        drawRect(Color(0xFFEFE6DA), mapa.topLeft, mapa.size)
        val corr = (d.viajeKm * 2500f) % 720f
        for (rep in -1..1) translate(mapa.left, mapa.top + corr + rep * 720f) { scale(1.6f, 1f, pivot = Offset.Zero) {
            for (c in CALLES_CLARAS) {
                val ancho = c[4]
                drawLine(if (ancho >= 18f) Color(0xFFF7D24A) else Color.White, Offset(c[0], c[1]), Offset(c[2], c[3]), ancho, StrokeCap.Round)
                if (ancho >= 18f) drawLine(Color(0xFFFFE680), Offset(c[0], c[1]), Offset(c[2], c[3]), ancho * 0.5f, StrokeCap.Round)
            }
        } }
        val ruta = Path().apply { moveTo(990f, 720f); lineTo(990f, 560f); cubicTo(985f, 380f, 900f, 300f, 800f, 70f) }
        drawPath(ruta, Color(0xFF7EC8F0), style = Stroke(40f, cap = StrokeCap.Round))
        drawPath(ruta, Color(0xFFA8DCF8), style = Stroke(24f, cap = StrokeCap.Round))
        drawCircle(Color.White.copy(alpha = 0.8f), 60f, Offset(985f, 490f), style = Stroke(5f))
        val flecha = Path().apply { moveTo(985f, 455f); lineTo(1015f, 512f); lineTo(985f, 498f); lineTo(955f, 512f); close() }
        drawPath(flecha, Color(0xFF1A5ED8))
        caja(705f, 380f, 46f, 46f, Brush.verticalGradient(listOf(Color(0xFF2A7AD8), Color(0xFF1A5AB8))), radio = 6f)
        texto(p, "P", 728f, 404f, 34f, Color.White, FBarlow, FontWeight.SemiBold)
        drawRect(Brush.verticalGradient(listOf(Color.Transparent, Color(0x55000000)), 560f, 698f), Offset(mapa.left, 560f), Size(mapa.width, 138f))
    }
    // Barra de arriba del mapa
    drawRect(Color(0xFF050505), Offset(590f, 0f), Size(720f, 72f))
    testigo(Testigo.NAFTA, 640f, 52f, 40f, Color.White, true, 0f)
    texto(p, num(d.autonomia), 668f, 54f, 40f, Color.White, FBarlow, FontWeight.Medium, ancla = 0f)
    texto(p, "km", 742f, 60f, 26f, Color.White, FBarlow, ancla = 0f)
    drawLine(Color(0xFF555555), Offset(800f, 30f), Offset(800f, 76f), 2f)
    caja(830f, 38f, 46f, 30f, Brush.verticalGradient(listOf(Color(0xFFDDDDDD), Color(0xFFAAAAAA))), radio = 4f)
    texto(p, "°C", 853f, 54f, 18f, Color.Black, FBarlow, FontWeight.SemiBold)
    texto(p, num(d.ambiente, 1) + "°  " + DatosTablero.hora24(), 890f, 56f, 38f, Color.White, FBarlow, FontWeight.Medium, ancla = 0f)
    drawLine(Color(0xFF555555), Offset(1085f, 30f), Offset(1085f, 76f), 2f)
    caja(1112f, 34f, 22f, 38f, Brush.verticalGradient(listOf(Color.White, Color.White)), radio = 4f)
    val pest = Path().apply { moveTo(1198f, 76f); lineTo(1208f, 28f); lineTo(1290f, 28f); lineTo(1300f, 76f) }
    drawPath(pest, Color(0xFF39B4E8), style = Stroke(4f))
    caja(1228f, 36f, 40f, 36f, Brush.verticalGradient(listOf(Color.White, Color(0xFFCCCCCC))), radio = 3f)

    fun dial(c: Offset, etiquetas: List<String>, angulo: (Int) -> Float, rojoDesde: Int, aguja: Float) {
        biselMetal(c, 342f, 16f)
        drawCircle(Color(0xFF0A0A0A), 334f, c)
        for (i in 0 until etiquetas.size - 1) {
            val g0 = angulo(i); val g1 = angulo(i + 1)
            // Como en la referencia: la del número gruesa, la del medio un poco más larga y dos chicas
            for (j in 0..3) {
                val g = lerp(g0, g1, j / 4f)
                val rojo = i >= rojoDesde
                drawLine(if (rojo) Color(0xFFE02020) else Color.White, polar(c, 318f, g), polar(c, when (j) { 0 -> 280f; 2 -> 296f; else -> 304f }, g), if (j == 0) 8f else 3.5f)
            }
        }
        drawLine(Color.White, polar(c, 318f, angulo(etiquetas.lastIndex)), polar(c, 282f, angulo(etiquetas.lastIndex)), 7f)
        if (rojoDesde < etiquetas.lastIndex) {
            val rojo = arco(c, 290f, angulo(rojoDesde), angulo(etiquetas.lastIndex))
            brillo(rojo, Color(0xFFFF2010), 10f, 8f)
            drawPath(rojo, Color(0xFFE01818), style = Stroke(6f))
        }
        etiquetas.forEachIndexed { i, s ->
            val tam = if (s.length > 2) 42f else 58f
            val pt = adentro(p, s, c, 272f, angulo(i), tam, FMont, FontWeight.Bold)
            texto(p, s, pt.x, pt.y, tam, if (i >= rojoDesde && rojoDesde < etiquetas.lastIndex) Color(0xFFFF3020) else Color.White, FMont, FontWeight.Bold)
        }
        drawCircle(Color(0xFF111111), 206f, c)
        drawCircle(Color(0xFF3A3A3A), 206f, c, style = Stroke(3f))
        val a = polar(c, 206f, aguja); val b = polar(c, 330f, aguja)
        brillo(linea(a, b), Color(0xFFFF2A10), 12f, 12f)
        drawLine(Color(0xFFE02A1A), a, b, 9f, StrokeCap.Round)
    }
    // Vueltas
    val cr = Offset(352f, 352f)
    dial(cr, (0..8).map { if (it == 0) "" else "$it" }, { 150f + it * 30f }, 7, 150f + d.rpmEn(8000f) / 1000f * 30f)
    texto(p, "1/min x 1000", cr.x, 615f, 30f, Color.White, FBarlow, FontWeight.Medium)
    texto(p, "READY", 172f, 514f, 26f, Color.White, FMont, FontWeight.Bold, alfa = if (d.motorEnMarcha) 1f else 0.45f)
    texto(p, "OFF", 205f, 556f, 26f, Color.White, FMont, FontWeight.Bold, alfa = if (d.motorEnMarcha) 0.45f else 1f)
    texto(p, "Ø", 240f, 312f, 38f, Color.White, FBarlow)
    texto(p, num(d.consumoPromedio ?: d.consumo, 1), 330f, 312f, 52f, Color.White, FBarlow, FontWeight.Medium)
    texto(p, "l/100km", 400f, 316f, 30f, Color.White, FBarlow, ancla = 0f)
    drawLine(Color.White, Offset(240f, 352f), Offset(470f, 352f), 3f)
    for ((i, et) in listOf("0", "10", "20").withIndex()) {
        val x = 282f + i * 74f
        drawLine(Color.White, Offset(x, 352f), Offset(x, 372f), 3f)
        texto(p, et, x, 395f, 26f, Color.White, FBarlow)
    }
    val px = 282f + ((d.consumo ?: 0f) / 20f).coerceIn(0f, 1f) * 148f
    drawPath(Path().apply { moveTo(px - 9f, 333f); lineTo(px + 9f, 333f); lineTo(px, 348f); close() }, Color.White)
    testigo(Testigo.BATERIA, 243f, 392f, 30f, Color(0xFF3AD03A), true, 0f)
    // Velocidad: 0-100 más abierto que 100-300
    val cv = Offset(1610f, 355f)
    val etV = listOf("0", "20", "40", "60", "80", "100", "140", "180", "220", "260", "300")
    fun gVel(v: Float) = if (v <= 100f) 150f + v / 100f * 120f else 270f + (v - 100f) / 200f * 120f
    dial(cv, etV, { 150f + it * 24f }, 99, gVel(d.velEn(300f)))
    // Mini cruce de calles dentro del reloj derecho
    val cruce = Path().apply { moveTo(1610f, 440f); lineTo(1610f, 330f); quadraticTo(1610f, 280f, 1700f, 240f) }
    drawPath(cruce, Color(0xFF606870), style = Stroke(34f))
    val cruce2 = Path().apply { moveTo(1610f, 330f); quadraticTo(1610f, 280f, 1520f, 240f) }
    drawPath(cruce2, Color(0xFF606870), style = Stroke(34f))
    drawPath(cruce, Color(0xFF39B4E8), style = Stroke(20f))
    texto(p, num(d.vel), 1610f, 485f, 92f, Color.White, FBarlow, FontWeight.SemiBold)
    texto(p, d.uVel, 1610f, 530f, 26f, Color.White, FBarlow)
}

// ═════════════════════════════════════════════════════════════════════════════
// 4 · Deportivo híbrido azul: velocidad y potencia segmentadas, auto holográfico al medio
// ═════════════════════════════════════════════════════════════════════════════

@Composable
fun TableroHibrido(d: DatosTablero, r: Recursos, modifier: Modifier) = Lienzo(1999f, modifier) { p ->
    val blanco = Color(0xFFF2F4F8)
    val azul = Color(0xFF3F9CFF)
    // Cielo azul oscuro, horizonte que brilla y piso más oscuro
    drawRect(Brush.verticalGradient(listOf(Color(0xFF03060E), Color(0xFF06112A), Color(0xFF0C2C66), Color(0xFF0A1A38), Color(0xFF05080F), Color(0xFF020306)), 0f, 846f), size = Size(1999f, 846f))
    drawRect(Brush.verticalGradient(listOf(Color.Transparent, Color(0x663D8CFF), Color(0x223D8CFF), Color.Transparent), 360f, 500f), Offset(0f, 360f), Size(1999f, 140f))
    drawLine(Color(0x553D8CFF), Offset(0f, 456f), Offset(1999f, 456f), 2f)
    // Ruta en perspectiva bajo el auto
    val ruta = Path().apply { moveTo(935f, 456f); lineTo(1065f, 456f); lineTo(1460f, 846f); lineTo(540f, 846f); close() }
    drawPath(ruta, Brush.verticalGradient(listOf(Color(0xFF0A0F1A), Color(0xFF05070C)), 456f, 846f))
    drawLine(Color(0x33A0B8D8), Offset(935f, 456f), Offset(540f, 846f), 3f)
    drawLine(Color(0x33A0B8D8), Offset(1065f, 456f), Offset(1460f, 846f), 3f)

    /**
     * Banda con volumen medida de la referencia: una cara de adelante angosta, blanca azulada, y una cara que
     * se va hacia el fondo, azul grisácea. La luz entra de costado: del lado iluminado ([luz] + 90°) la cara
     * de atrás va de oscura adentro a clara junto al quiebre; del lado opuesto se invierte (clara adentro,
     * muy oscura junto al quiebre), y en la diagonal queda pareja. Se dibuja en tajadas de 2° para que cada
     * una tenga su propio degradé.
     */
    fun cara(c: Offset, rIn: Float, rQ: Float, rOut: Float, g0: Float, g1: Float, luz: Float, tenue: Boolean = false) {
        val medio = Color(0xFF5A7996)
        val n = (kotlin.math.abs(g1 - g0) / 2f).toInt().coerceAtLeast(1)
        for (i in 0 until n) {
            val a = lerp(g0, g1, i / n.toFloat()); val b = lerp(g0, g1, (i + 1) / n.toFloat())
            val sGrados = ((a + b) / 2f - luz).toDouble()
            val s = kotlin.math.sin(Math.toRadians(sGrados)).toFloat()
            val adentro = if (s >= 0f) androidx.compose.ui.graphics.lerp(medio, Color(0xFF1C2840), s) else androidx.compose.ui.graphics.lerp(medio, Color(0xFF527090), -s)
            val quiebre = if (s >= 0f) androidx.compose.ui.graphics.lerp(medio, Color(0xFF97AEC0), s) else androidx.compose.ui.graphics.lerp(medio, Color(0xFF1A2640), -s)
            val fIn = rIn / rQ
            val atras = if (tenue) arrayOf(0f to Color.Transparent, fIn to Color(0xFF3E5674), 1f to Color(0xFF5E7796))
            else arrayOf(0f to Color.Transparent, fIn to adentro, lerp(fIn, 1f, 0.5f) to androidx.compose.ui.graphics.lerp(adentro, quiebre, 0.5f), 1f to quiebre)
            drawPath(banda(c, rIn, rQ, a, b + 0.3f, pasos = 2), Brush.radialGradient(*atras, center = c, radius = rQ))
            val frente = if (tenue) Color(0xFF6E87A5) else androidx.compose.ui.graphics.lerp(Color(0xFFE6F1F8), Color(0xFFC8D4E0), s.coerceAtLeast(0f))
            val frenteFuera = if (tenue) Color(0xFF657C9C) else androidx.compose.ui.graphics.lerp(Color(0xFFF2F7FC), Color(0xFFCFD9E3), s.coerceAtLeast(0f))
            drawPath(
                banda(c, rQ, rOut, a, b + 0.3f, pasos = 2),
                Brush.radialGradient(0f to Color.Transparent, rQ / rOut to frente, 1f to frenteFuera, center = c, radius = rOut),
            )
        }
    }

    /**
     * Los dos aros de afuera, como en la referencia: uno blanco pegado a las secciones con una marquita en
     * cada división, y otro más afuera y transparente sobre el que van los números y la unidad (cortado
     * donde hay texto, para que nada se pise). Ángulos sin normalizar: [desde] < [hasta].
     */
    fun aros(
        c: Offset, rOut: Float, bandaDesde: Float, bandaHasta: Float, divisiones: List<Float>,
        desde: Float, hasta: Float, numeros: List<Pair<String, Float>>, unidad: String, gUnidad: Float,
    ) {
        val rB = rOut + 9f
        val blancoAro = Color(0xFFE6EEF5)
        // Marca en la cara de adelante de las secciones, alineada con la del aro
        for (g in divisiones) drawLine(Color(0xFF3A4556), polar(c, rOut - 26f, g), polar(c, rOut, g), 2.5f)
        // Aro y marquitas hacia adentro, con una sombra suave que los despega de las secciones
        val aro = arco(c, rB, bandaDesde, bandaHasta)
        brillo(aro, Color(0xCC000814), 6f, 7f)
        for (g in divisiones) brillo(linea(polar(c, rB, g), polar(c, rB - 15f, g)), Color(0xCC000814), 5f, 7f)
        drawPath(aro, blancoAro, style = Stroke(3f))
        for (g in divisiones) drawLine(blancoAro, polar(c, rB, g), polar(c, rB - 15f, g), 3f)
        val rT = rOut + 44f
        val tamNum = 40f; val tamUni = 26f
        val huecos = ArrayList<Pair<Float, Float>>()
        for ((s, g) in numeros) {
            val m = (anchoTexto(p, s, tamNum, FMont, FontWeight.Bold) / 2f + 16f) / rT * 180f / Math.PI.toFloat()
            huecos += (g - m) to (g + m)
        }
        val anchoU = unidad.sumOf { anchoTexto(p, it.toString(), tamUni, FMont, FontWeight.SemiBold).toDouble() + 2.0 }.toFloat()
        val mU = (anchoU / 2f + 12f) / rT * 180f / Math.PI.toFloat()
        huecos += (gUnidad - mU) to (gUnidad + mU)
        huecos.sortBy { it.first }
        var g0 = desde
        for ((a, b) in huecos) {
            if (a > g0) drawPath(arco(c, rT, g0, minOf(a, hasta)), Color(0x55B8C4D2), style = Stroke(2.5f))
            g0 = maxOf(g0, b)
        }
        if (g0 < hasta) drawPath(arco(c, rT, g0, hasta), Color(0x55B8C4D2), style = Stroke(2.5f))
        for ((s, g) in numeros) {
            val pt = polar(c, rT, g)
            texto(p, s, pt.x, pt.y, tamNum, Color.White, FMont, FontWeight.Bold)
        }
        textoCurvo(p, unidad, c, rT, gUnidad, tamUni, Color(0xFFC8D2DE), FMont, FontWeight.SemiBold, espaciado = 2f)
    }

    fun raya(c: Offset, r0: Float, r1: Float, g: Float, color: Color = Color(0xFF283040), ancho: Float = 2.5f) =
        drawLine(color, polar(c, r0, g), polar(c, r1, g), ancho)

    /** El valor actual: una barra blanca que cruza la banda (en cero coincide con la del original). */
    fun marcador(c: Offset, r0: Float, r1: Float, g: Float) {
        val a = polar(c, r0, g); val b = polar(c, r1, g)
        brillo(linea(a, b), Color.White, 10f, 10f)
        drawLine(Color.White, a, b, 7f, StrokeCap.Butt)
    }

    // ── Velocidad (izquierda): 5 secciones grandes, una por número ──────────
    val c = Offset(442f, 392f)
    val rIn = 184f; val rQ = 250f; val rOut = 278f
    val angulos = floatArrayOf(51f, 98f, 146f, 191f, 237f, 302f)
    val valores = if (d.millas) floatArrayOf(0f, 30f, 60f, 90f, 120f, 160f) else floatArrayOf(0f, 50f, 100f, 150f, 200f, 260f)
    fun gVel(v: Float): Float {
        val x = v.coerceIn(0f, valores.last())
        val i = (1 until valores.size).firstOrNull { x <= valores[it] } ?: valores.lastIndex
        return lerp(angulos[i - 1], angulos[i], (x - valores[i - 1]) / (valores[i] - valores[i - 1]))
    }
    cara(c, rIn, rQ, rOut, angulos.first(), angulos.last(), luz = 35f)
    for (i in 1 until angulos.lastIndex) raya(c, rIn, rQ, angulos[i])
    // Rayitas chicas sobre la cara de adelante
    for (s in 0 until angulos.lastIndex) for (j in 1..5) raya(c, rQ, rQ + 9f, lerp(angulos[s], angulos[s + 1], j / 6f), Color(0x99606A7A), 2f)
    raya(c, rIn, rOut, angulos.last(), Color(0xFFF4F6F8), 3f)
    aros(
        c, rOut, angulos.first(), angulos.last(), angulos.toList(), 40f, 312f,
        valores.mapIndexed { i, v -> num(v) to angulos[i] }, d.uVel, 270f,
    )
    marcador(c, rIn - 6f, rOut + 22f, gVel(d.velEn(valores.last())))
    texto(p, num(d.velOtra), 440f, 296f, 46f, blanco, FMont, FontWeight.Medium)
    texto(p, d.uOtra, 492f, 302f, 28f, blanco, FMont, FontWeight.Medium, ancla = 0f)
    texto(p, num(d.vel), 445f, 405f, 160f, blanco, FMont, FontWeight.Medium)
    texto(p, d.uVel, 625f, 462f, 40f, blanco, FMont, FontWeight.Medium, ancla = 0f)
    caja(410f, 508f, 80f, 76f, Brush.verticalGradient(listOf(Color(0xFFDDE1E6), Color(0xFFA8AEB6))), radio = 2f)
    texto(p, if (d.palanca == 'D') d.textoMarcha else "---", 450f, 546f, 36f, Color(0xFF2A2E34), FMont, FontWeight.SemiBold)

    // ── Vueltas (derecha): 6 secciones de 0 a 6.000 y 6 más hasta 8.000 ─────
    val cp = Offset(1568f, 395f)
    val pIn = 184f; val pQ = 250f; val pOut = 278f
    val cero = 126f; val cien = -42f; val tope = -120f
    cara(cp, pIn, pQ, pOut, tope, cero, luz = -45f)
    for (i in 1..5) raya(cp, pIn, pQ, lerp(cero, cien, i / 6f))
    for (s in 0 until 6) for (j in 1..4) raya(cp, pQ, pQ + 9f, lerp(lerp(cero, cien, s / 6f), lerp(cero, cien, (s + 1) / 6f), j / 5f), Color(0x99606A7A), 2f)
    // En la zona alta las divisiones cortan las dos caras
    for (i in 0..5) raya(cp, pIn, pOut, lerp(cien, tope, i / 6f), Color(0xFF283040), 3f)
    raya(cp, pIn, pOut, tope, Color(0xFFF4F6F8), 3f)
    // Ángulos de la derecha llevados a 176..486 para que el aro sea un solo tramo continuo
    fun n(g: Float) = if (g < 176f) g + 360f else g
    aros(
        cp, pOut, n(tope), n(cero),
        (0..6).map { n(lerp(cero, cien, it / 6f)) } + (1..6).map { n(lerp(cien, tope, it / 6f)) },
        176f, 486f, listOf("0" to n(cero), "3" to n(lerp(cero, cien, 0.5f)), "6" to n(cien)), "1/min x 1000", 214f,
    )
    // Carga: banda aparte abajo a la izquierda, más tenue, con sus escalones
    val carga = fraccion(d.deA(d.tension, 14.5f, 11.5f), 11.5f, 14.5f)
    cara(cp, pIn, pQ, pOut, 132f, 176f, luz = -45f, tenue = true)
    for (i in 1..4) raya(cp, pIn, pOut, lerp(132f, 176f, i / 5f))
    raya(cp, pIn, pOut, 176f, Color(0xFFDCE2EA), 3f)
    textoCurvo(p, "BATERÍA", cp, 305f, 155f, 28f, Color(0xFFB8C0CC), FMont, FontWeight.Medium, espaciado = 3f, abajo = true)
    val rpm = d.rpmEn(8000f)
    marcador(cp, pIn - 6f, pOut + 22f, if (rpm <= 6000f) lerp(cero, cien, rpm / 6000f) else lerp(cien, tope, ((rpm - 6000f) / 2000f).coerceAtMost(1f)))
    // Arcos azules
    val boost = arco(cp, 158f, 238f, 317f)
    brillo(boost, azul, 12f, 10f)
    drawPath(boost, Color(0xFF2E6FB0), style = Stroke(9f, cap = StrokeCap.Round))
    drawPath(boost, Color(0xFFA2DEF8), style = Stroke(3.5f, cap = StrokeCap.Round))
    textoCurvo(p, "SPORT", cp, 132f, 272f, 32f, Color(0xFF4FA8FF), FMont, FontWeight.Medium, espaciado = 2f)
    texto(p, d.modo, 1567f, 330f, 38f, blanco, FMont, FontWeight.Medium, espaciado = 1f)
    val edrive = arco(cp, 179f, 173f, 82f)
    brillo(edrive, azul, 12f, 10f)
    drawPath(edrive, Color(0xFF2E6FB0), style = Stroke(9f, cap = StrokeCap.Round))
    drawPath(edrive, Color(0xFF8CCCF0), style = Stroke(3.5f, cap = StrokeCap.Round))
    textoCurvo(p, "ECO", cp, 145f, 96f, 32f, Color(0xFF4FA8FF), FMont, FontWeight.Medium, espaciado = 2f, abajo = true)
    texto(p, if (d.motorEnMarcha) "READY" else "OFF", 1432f, 712f, 34f, azul, FMont, FontWeight.Medium, brilloR = 8f)

    // ── Centro: auto holográfico ────────────────────────────────────────────
    r.foto?.let {
        halo(Offset(1000f, 520f), 240f, Color(0xFF2A7BFF), 0.45f)
        foto(it, Offset(1000f, 470f), 360f, 190f, holograma(0.2f, 0.75f, 1.5f, 40f), 0.95f)
    }
    texto(p, (if ((d.ambiente ?: 0f) >= 0f) "+" else "") + num(d.ambiente) + "°C", 1020f, 98f, 44f, blanco, FMont, FontWeight.SemiBold)
    texto(p, DatosTablero.hora24(), 1020f, 155f, 44f, blanco, FMont, FontWeight.SemiBold)
    testigo(Testigo.ADVERTENCIA, 645f, 708f, 46f, Color(0xFFFFC21A), d.prueba || d.testigoMotor)

    // ── Barra de abajo: alta en los costados, baja al medio ─────────────────
    val gris = Color(0xAAC8D0DA)
    val base = Path().apply { moveTo(140f, 738f); lineTo(475f, 738f); lineTo(522f, 792f); lineTo(1478f, 792f); lineTo(1525f, 738f); lineTo(1999f, 738f) }
    drawPath(base, gris, style = Stroke(3f))
    // Calendario y fecha
    caja(165f, 748f, 30f, 30f, Brush.verticalGradient(listOf(Color.Transparent, Color.Transparent)), blanco, 3f, 3f)
    texto(p, "1", 180f, 765f, 20f, blanco, FMont, FontWeight.SemiBold)
    texto(p, DatosTablero.fechaCorta(), 228f, 764f, 38f, blanco, FMont, FontWeight.Medium, ancla = 0f)
    // Nafta: cada sección encendida con su degradé; las apagadas, un solo degradé de punta a punta
    testigo(Testigo.NAFTA, 572f, 758f, 50f, Color(0xFFFFC21A), true, 0f)
    val fn = d.deA(d.nafta, 100f) / 100f
    val x0 = 608f; val x1 = 930f
    drawRect(Brush.horizontalGradient(listOf(Color(0xFF6A4612), Color(0xFF4A2E0C), Color(0xFF5A3A10)), x0, x1), Offset(x0, 763f), Size(x1 - x0, 15f))
    val anchoSec = (x1 - x0) / 4f
    for (i in 0 until 4) {
        val xs = x0 + i * anchoSec
        val lleno = ((fn * 4f) - i).coerceIn(0f, 1f)
        if (lleno > 0f) drawRect(Brush.horizontalGradient(listOf(Color(0xFFFFD040), Color(0xFFF2A010)), xs, xs + anchoSec), Offset(xs, 763f), Size((anchoSec - 4f) * lleno, 15f))
        drawRect(Color(0xFF05070C), Offset(xs + anchoSec - 4f, 763f), Size(4f, 15f))
    }
    // Batería (tensión) en azul, igual idea
    val bat = Path().apply { addRect(androidx.compose.ui.geometry.Rect(965f, 745f, 1022f, 777f)) }
    drawPath(bat, azul, style = Stroke(3f))
    for (i in 0..4) drawLine(azul, Offset(975f + i * 9f, 752f), Offset(975f + i * 9f, 770f), 3f)
    val y0 = 1040f; val y1 = 1172f
    drawRect(Brush.horizontalGradient(listOf(Color(0xFF1A2A55), Color(0xFF0E1830), Color(0xFF142242)), y0, y1), Offset(y0, 763f), Size(y1 - y0, 15f))
    val anchoBat = (y1 - y0) / 4f
    for (i in 0 until 4) {
        val xs = y0 + i * anchoBat
        val lleno = ((carga * 4f) - i).coerceIn(0f, 1f)
        if (lleno > 0f) drawRect(Brush.horizontalGradient(listOf(Color(0xFF5AAEFF), Color(0xFF2A6CE8)), xs, xs + anchoBat), Offset(xs, 763f), Size((anchoBat - 4f) * lleno, 15f))
        drawRect(Color(0xFF05070C), Offset(xs + anchoBat - 4f, 763f), Size(4f, 15f))
    }
    val marcador = y0 + (y1 - y0) * carga
    drawPath(Path().apply { moveTo(marcador - 9f, 744f); lineTo(marcador + 9f, 744f); lineTo(marcador, 757f); close() }, Color(0xFFFFB31A))
    texto(p, num(d.autonomia) + " km", 1245f, 766f, 32f, azul, FMont, FontWeight.Medium)
    drawLine(gris, Offset(1310f, 745f), Offset(1310f, 785f), 2f)
    texto(p, num(d.viajeKm, 1) + " km", 1395f, 764f, 36f, blanco, FMont, FontWeight.Medium)
    drawLine(gris, Offset(1540f, 748f), Offset(1540f, 785f), 2f)
    texto(p, d.palanca.toString(), 1592f, 764f, 48f, blanco, FMont, FontWeight.Medium)
    drawLine(gris, Offset(1645f, 748f), Offset(1645f, 785f), 2f)
    texto(p, num(d.odometro ?: d.viajeKm, 1) + " km", 1765f, 764f, 36f, blanco, FMont, FontWeight.Medium)
}

// ═════════════════════════════════════════════════════════════════════════════
// 5 · M Sport: dos relojes iguales de secciones con volumen naranja, aro cromado y fondo casi negro
// ═════════════════════════════════════════════════════════════════════════════

@Composable
fun TableroM(d: DatosTablero, r: Recursos, modifier: Modifier) = Lienzo(2000f, modifier) { p ->
    // La foto de referencia está sacada de costado: acá va de frente. Fondo casi negro, dos relojes iguales.
    drawRect(Brush.verticalGradient(listOf(Color(0xFF141416), Color(0xFF0B0B0C), Color(0xFF070708)), 0f, 926f), size = Size(2000f, 926f))
    drawRect(Brush.verticalGradient(listOf(Color(0xFF050506), Color(0xFF0C0C0D)), 760f, 926f), Offset(0f, 760f), Size(2000f, 166f))
    val naranja = Color(0xFFFF7A3A)

    /**
     * Secciones con volumen, como el Híbrido azul pero en naranja: cara de adelante angosta y clara afuera, y
     * una pared que se va hacia el fondo cuyo degradé cambia con el ángulo (luz de costado). Tajadas de 2°.
     */
    fun cara(c: Offset, rIn: Float, rQ: Float, rOut: Float, g0: Float, g1: Float, luz: Float) {
        val medio = Color(0xFFB4481C)
        val n = (kotlin.math.abs(g1 - g0) / 2f).toInt().coerceAtLeast(1)
        for (i in 0 until n) {
            val a = lerp(g0, g1, i / n.toFloat()); val b = lerp(g0, g1, (i + 1) / n.toFloat())
            val s = kotlin.math.sin(Math.toRadians(((a + b) / 2f - luz).toDouble())).toFloat()
            val adentro = if (s >= 0f) androidx.compose.ui.graphics.lerp(medio, Color(0xFF2E0C04), s) else androidx.compose.ui.graphics.lerp(medio, Color(0xFFC85A28), -s)
            val quiebre = if (s >= 0f) androidx.compose.ui.graphics.lerp(medio, Color(0xFFF2874A), s) else androidx.compose.ui.graphics.lerp(medio, Color(0xFF4A1406), -s)
            val fIn = rIn / rQ
            drawPath(
                banda(c, rIn, rQ, a, b + 0.3f, pasos = 2),
                Brush.radialGradient(
                    0f to Color.Transparent, fIn to adentro, lerp(fIn, 1f, 0.5f) to androidx.compose.ui.graphics.lerp(adentro, quiebre, 0.5f),
                    1f to quiebre, center = c, radius = rQ,
                ),
            )
            val frente = androidx.compose.ui.graphics.lerp(Color(0xFFFFB48A), Color(0xFFE88A5E), s.coerceAtLeast(0f))
            drawPath(
                banda(c, rQ, rOut, a, b + 0.3f, pasos = 2),
                Brush.radialGradient(
                    0f to Color.Transparent, rQ / rOut to frente, 1f to androidx.compose.ui.graphics.lerp(frente, Color(0xFFFFD2B8), 0.4f),
                    center = c, radius = rOut,
                ),
            )
        }
    }

    fun reloj(c: Offset, etiquetas: List<String>, desde: Float, sweep: Float, frac: Float, rojoDesde: Float?) {
        val rIn = 186f; val rQ = 276f; val rOut = 298f
        drawCircle(Color(0xFF0A0605), rOut, c)
        cara(c, rIn, rQ, rOut, 0f, 360f, luz = 35f)
        // Divisiones de la escala: rayas finas en la pared y marca en la cara de adelante
        val n = (etiquetas.size - 1) * 4
        for (i in 0..n) {
            val g = desde + sweep * i / n
            drawLine(Color(0x88200802), polar(c, rIn, g), polar(c, rQ, g), if (i % 4 == 0) 2.5f else 1.4f)
            if (i % 4 == 0) drawLine(Color(0x99401004), polar(c, rQ, g), polar(c, rOut, g), 2.5f)
        }
        rojoDesde?.let {
            drawPath(banda(c, rIn, rQ, desde + sweep * it, desde + sweep), Color(0xBBFF2A0A))
            for (j in 0..10) {
                val g = lerp(desde + sweep * it, desde + sweep, j / 10f)
                drawLine(Color(0xFF6A0A00), polar(c, rIn, g), polar(c, rQ, g), 2f)
            }
        }
        // Centro casi negro, con una sombra suave sobre el borde de la pared
        drawCircle(Brush.radialGradient(listOf(Color(0xFF1A0E0B), Color(0xFF0C0605), Color(0xFF060303)), c, rIn), rIn, c)
        drawCircle(Color(0x88000000), rIn + 2f, c, style = Stroke(6f))
        etiquetas.forEachIndexed { i, s ->
            val pt = polar(c, (rIn + rQ) / 2f, desde + sweep * i / (etiquetas.size - 1))
            texto(p, s, pt.x, pt.y, 50f, Color.White, FBarlow, FontWeight.SemiBold)
        }
        // Aguja: línea blanca que cruza la pared
        val g = desde + sweep * frac
        val a = polar(c, rIn - 4f, g); val b = polar(c, rOut, g)
        brillo(linea(a, b), Color.White, 8f, 6f)
        drawLine(Color.White, a, b, 4.5f)
        // Aro cromado partido, como en el original
        val aro = Brush.sweepGradient(listOf(Color(0xFF8A8A8A), Color.White, Color(0xFF6A6A6A), Color(0xFFE0E0E0), Color(0xFF8A8A8A)), c)
        drawArc(aro, -60f, 300f, false, Offset(c.x - rOut - 14f, c.y - rOut - 14f), Size((rOut + 14f) * 2, (rOut + 14f) * 2), style = Stroke(14f))
        drawArc(aro, 242f, 50f, false, Offset(c.x - rOut - 30f, c.y - rOut - 30f), Size((rOut + 30f) * 2, (rOut + 30f) * 2), style = Stroke(9f))
    }

    // Velocidad (izquierda) y vueltas (derecha), del mismo tamaño
    val cv = Offset(560f, 420f)
    reloj(cv, listOf("0", "", "", "", "", "", "260"), 140f, 270f, d.velEn(260f) / 260f, null)
    texto(p, d.uVel, cv.x, cv.y - 110f, 28f, naranja, FBarlow)
    texto(p, num(d.vel), cv.x, cv.y + 5f, 130f, Color.White, FBarlow, FontWeight.Medium)

    val cr = Offset(1440f, 420f)
    reloj(cr, (0..7).map { "$it" }, 150f, 240f, d.rpmEn(7000f) / 7000f, 6.5f / 7f)
    texto(p, "1/min x 1000", cr.x, cr.y - 120f, 28f, naranja, FBarlow, FontWeight.Medium)
    texto(p, d.textoMarcha.let { if (it.startsWith("D")) "M" + it.drop(1) else it }, cr.x, cr.y + 2f, 120f, Color.White, FMont, FontWeight.Bold)
    texto(p, d.modo.replace("+", " +"), cr.x, cr.y + 112f, 32f, naranja, FBarlow, FontWeight.SemiBold, espaciado = 2f)
    val trap = Path().apply {
        moveTo(cr.x - 80f, cr.y + 140f); lineTo(cr.x + 80f, cr.y + 140f); lineTo(cr.x + 60f, cr.y + 186f); lineTo(cr.x - 60f, cr.y + 186f); close()
    }
    drawPath(trap, Color(0xFF0E0E0E)); drawPath(trap, Color(0xFF8A8A8A), style = Stroke(2f))
    texto(p, d.textoMarcha, cr.x, cr.y + 163f, 32f, Color.White, FBarlow, FontWeight.SemiBold)

    // Relojes chicos a los costados (nafta y temperatura del aceite), iguales entre sí
    fun chico(c: Offset, radio: Float, frac: Float, t: Testigo) {
        drawCircle(Color(0xFF0C0C0D), radio, c)
        drawCircle(Brush.sweepGradient(listOf(Color(0xFF8A8A8A), Color.White, Color(0xFF6A6A6A), Color(0xFF8A8A8A)), c), radio, c, style = Stroke(8f))
        rayas(c, radio * 0.62f, radio * 0.82f, 150f, 390f, 12, naranja, 3f, colorDe = { i -> if (i >= 10) Color(0xFFFF2A0A) else if (i <= 1) Color(0xFF2A8CFF) else naranja })
        val g = 150f + 240f * frac
        drawLine(Color(0xFFFF8A4A), c, polar(c, radio * 0.75f, g), 5f, StrokeCap.Round)
        testigo(t, c.x, c.y + radio * 0.42f, radio * 0.32f, naranja, true, 4f)
    }
    chico(Offset(140f, 640f), 100f, d.deA(d.nafta, 100f) / 100f, Testigo.NAFTA)
    chico(Offset(1860f, 640f), 100f, fraccion(d.deA(d.aceite ?: d.refrigerante, 150f, 50f), 50f, 150f), Testigo.ACEITE)

    // Renglón de datos al medio, abajo
    texto(p, "Hora", 840f, 790f, 24f, Color(0xFFAAAAAA), FBarlow, ancla = 0f)
    texto(p, DatosTablero.hora24(), 840f, 826f, 36f, Color.White, FBarlow, FontWeight.Medium, ancla = 0f)
    texto(p, "Viaje", 960f, 790f, 24f, Color(0xFFAAAAAA), FBarlow, ancla = 0f)
    texto(p, num(d.viajeKm, 1) + " km", 960f, 826f, 36f, Color.White, FBarlow, FontWeight.Medium, ancla = 0f)
    texto(p, "Temp. ext.", 1090f, 790f, 24f, Color(0xFFAAAAAA), FBarlow, ancla = 0f)
    texto(p, (if ((d.ambiente ?: 0f) >= 0f) "+" else "") + num(d.ambiente) + "°C", 1090f, 826f, 36f, Color.White, FBarlow, FontWeight.Medium, ancla = 0f)
}
