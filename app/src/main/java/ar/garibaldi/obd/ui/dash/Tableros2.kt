package ar.garibaldi.obd.ui.dash

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import ar.garibaldi.obd.ui.dash.DatosTablero.Companion.num

// ═════════════════════════════════════════════════════════════════════════════
// 6 · Gran turismo clásico: dos relojes blancos con agujas rojas y computadora al medio
// ═════════════════════════════════════════════════════════════════════════════

@Composable
fun TableroClasico(d: DatosTablero, r: Recursos, modifier: Modifier) = Lienzo(2000f, modifier) { p ->
    drawRect(Brush.radialGradient(listOf(Color(0xFF1C1C1E), Color(0xFF0E0E0F), Color(0xFF050505)), Offset(1000f, 390f), 1200f), size = Size(2000f, 782f))
    val blanco = Color(0xFFF0EDE6)

    fun reloj(c: Offset, etiquetas: List<String>, frac: Float, rojoDesde: Int?, unidad: String, unidadY: Float) {
        val desde = 140f; val sweep = 260f
        drawCircle(Brush.radialGradient(listOf(Color(0xFF151516), Color(0xFF0B0B0C)), c, 360f), 360f, c)
        biselMetal(c, 364f, 14f)
        val n = (etiquetas.size - 1) * 4
        for (i in 0..n) {
            val g = desde + sweep * i / n
            val rojo = rojoDesde != null && i >= rojoDesde * 4
            val largo = if (i % 4 == 0) 40f else if (i % 2 == 0) 22f else 14f
            drawLine(if (rojo) Color(0xFFFF2A1A) else blanco, polar(c, 338f, g), polar(c, 338f - largo, g), if (i % 4 == 0) 7f else 3f)
        }
        if (rojoDesde != null) {
            val arc = arco(c, 345f, desde + sweep * rojoDesde / (etiquetas.size - 1), desde + sweep)
            brillo(arc, Color(0xFFFF1A0A), 8f, 6f)
            drawPath(arc, Color(0xFFFF2A1A), style = Stroke(6f))
        }
        etiquetas.forEachIndexed { i, s ->
            val pt = adentro(p, s, c, 292f, desde + sweep * i / (etiquetas.size - 1), 58f, FBarlow, FontWeight.Normal)
            texto(p, s, pt.x, pt.y, 58f, if (rojoDesde != null && i >= rojoDesde) Color(0xFFFF3A2A) else blanco, FBarlow, FontWeight.Normal)
        }
        texto(p, unidad, c.x, unidadY, 34f, blanco, FBarlow, FontWeight.Medium)
        val g = desde + sweep * frac
        // Como en la referencia: la aguja termina en el centro (sin cola) y el centro es un círculo oscuro con un aro fino
        drawCircle(Brush.radialGradient(listOf(Color(0xFF2A2A2C), Color(0xFF0E0E0F)), c, 40f), 38f, c)
        drawCircle(Color(0xFF8A8A8A), 38f, c, style = Stroke(3f))
        aguja(c, 330f, g, 11f, 4f, Color(0xFFFF4636), desde = 10f, brilloColor = Color(0xFFFF0A00), brilloRadio = 8f, colorBase = Color(0xFFC0281A))
    }
    // Velocidad
    val cv = Offset(390f, 395f)
    reloj(cv, (0..8).map { "${it * 30}" }, d.velEn(240f) / 240f, null, d.uVel, 192f)
    caja(292f, 500f, 220f, 64f, Brush.verticalGradient(listOf(Color(0xFF151515), Color(0xFF0C0C0C))), Color(0xFF3A3A3A), 6f, 2f)
    texto(p, num(d.velOtra), 425f, 532f, 50f, blanco, FBarlow, FontWeight.Medium, ancla = 1f)
    texto(p, d.uOtra, 432f, 540f, 30f, blanco, FBarlow, ancla = 0f)
    // Nafta abajo
    medidorInferior(p, cv, d.deA(d.nafta, 100f) / 100f, "E", "F", Testigo.NAFTA)
    // Vueltas
    val cr = Offset(1615f, 395f)
    reloj(cr, (0..8).map { "$it" }, d.rpmEn(8000f) / 8000f, 7, "x1000rpm", 195f)
    medidorInferior(p, cr, fraccion(d.deA(d.refrigerante, 130f, 40f), 40f, 130f), "C", "H", Testigo.TEMPERATURA)

    // Computadora de viaje al medio
    val iconos = listOf(Testigo.NAFTA, Testigo.GIRO_DER, Testigo.LIMPIA, Testigo.COPO, Testigo.MOTOR, Testigo.ADVERTENCIA)
    iconos.forEachIndexed { i, t -> testigo(t, 820f + i * 78f, 82f, 48f, Color(0xFFCFCFCF), true, 0f) }
    // "Viaje" termina antes de la caja de la "A" (con cualquier idioma)
    texto(p, "Viaje", 1005f, 237f, 50f, blanco, FBarlow, FontWeight.Medium, ancla = 1f)
    caja(1017f, 214f, 40f, 44f, Brush.verticalGradient(listOf(Color.Transparent, Color.Transparent)), blanco, 4f, 3f)
    texto(p, "A", 1037f, 237f, 32f, blanco, FBarlow, FontWeight.SemiBold)
    texto(p, num(d.viajeKm, 1), 1020f, 330f, 80f, blanco, FBarlow, FontWeight.Medium, ancla = 0.5f)
    texto(p, "km", 1140f, 342f, 34f, blanco, FBarlow, ancla = 0f)
    texto(p, "Prom.", 855f, 432f, 44f, blanco, FBarlow, ancla = 0f)
    texto(p, num(d.velPromedio), 1110f, 430f, 60f, blanco, FBarlow, FontWeight.Medium, ancla = 1f)
    texto(p, d.uVel, 1115f, 440f, 30f, blanco, FBarlow, ancla = 0f)
    texto(p, "T/T", 855f, 495f, 44f, blanco, FBarlow, ancla = 0f)
    texto(p, DatosTablero.duracion(d.viajeSeg), 1170f, 495f, 60f, blanco, FBarlow, FontWeight.Medium, ancla = 1f)
    caja(905f, 552f, 52f, 36f, Brush.verticalGradient(listOf(Color.Transparent, Color.Transparent)), Color(0xFF9A9A9A), 4f, 2f)
    texto(p, "OK", 931f, 570f, 26f, blanco, FBarlow, FontWeight.SemiBold)
    texto(p, "Mant.: Reiniciar", 975f, 570f, 32f, blanco, FBarlow, ancla = 0f)
    for (i in 0..2) drawCircle(if (i == 1) Color(0xFFDDDDDD) else Color(0xFF555555), 5f, Offset(1235f, 357f + i * 38f))
    val chevron = Path().apply { moveTo(780f, 610f); lineTo(880f, 610f); lineTo(915f, 645f); lineTo(1085f, 645f); lineTo(1120f, 610f); lineTo(1220f, 610f) }
    drawPath(chevron, Color(0xFF3A3A3A), style = Stroke(3f))
    texto(p, num(d.ambiente) + " °C", 700f, 760f, 40f, blanco, FBarlow, FontWeight.Medium)
    texto(p, d.palanca.toString(), 1010f, 690f, 110f, blanco, FBarlow, FontWeight.Medium)
    texto(p, num(d.odometro ?: d.viajeKm) + " km", 1295f, 715f, 46f, blanco, FBarlow, FontWeight.Medium)
}

/** Medidor en arco abajo de un reloj (nafta o temperatura) con su barra blanca. */
private fun androidx.compose.ui.graphics.drawscope.DrawScope.medidorInferior(
    p: Pincel, c: Offset, frac: Float, izq: String, der: String, t: Testigo,
) {
    val desde = 118f; val hasta = 62f
    drawPath(banda(c, 312f, 330f, hasta, desde), Color(0xFF2A2A2A))
    drawPath(banda(c, 312f, 330f, desde - (desde - hasta) * frac, desde), Color(0xFFE8E8E8))
    rayas(c, 300f, 312f, hasta, desde, 4, Color(0xFFAAAAAA), 3f)
    val a = polar(c, 360f, desde + 4f); val b = polar(c, 360f, hasta - 4f)
    texto(p, izq, a.x, a.y, 38f, Color(0xFFE8E8E8), FBarlow, FontWeight.Medium)
    texto(p, der, b.x, b.y, 38f, Color(0xFFE8E8E8), FBarlow, FontWeight.Medium)
    testigo(t, c.x, c.y + 270f, 40f, Color(0xFFE8E8E8), true, 0f)
}

// ═════════════════════════════════════════════════════════════════════════════
// 7 · Superdeportivo: anillo blanco de vueltas hasta 10.000, marcha gigante al medio
// ═════════════════════════════════════════════════════════════════════════════

@Composable
fun TableroSuper(d: DatosTablero, r: Recursos, modifier: Modifier) = Lienzo(2000f, modifier) { p ->
    drawRect(Color.Black, size = Size(2000f, 1408f))
    val c = Offset(965f, 740f)
    val blanco = Color(0xFFF4F3EF)
    // 0 a 5 mil comprimido abajo a la izquierda, 5 a 10 mil más abierto arriba
    // Ángulos medidos de la referencia: 0 a 4 mil a 15° por mil, de 4 mil en adelante a ~30° por mil
    fun gRpm(v: Float): Float = if (v <= 4000f) 90f + v / 4000f * 60f else 150f + (v - 4000f) / 6000f * 181f
    // Marcas blancas por fuera del anillo
    for (i in 0..20) {
        val g = gRpm(i * 500f)
        val mayor = i % 2 == 0
        drawLine(if (i >= 18) Color(0xFFFF2A1A) else blanco, polar(c, 588f, g), polar(c, if (mayor) 650f else 620f, g), if (mayor) 9f else 5f)
    }
    // Anillo blanco
    // Anillo blanco con una sombra suave hacia el borde interno, como en la referencia
    drawCircle(Brush.radialGradient(0f to Color(0xFF6A6A68), 0.64f to Color(0xFF7A7A78), 0.7f to Color(0xFFD8D8D4), 0.8f to blanco, 1f to Color.White, center = c, radius = 569f), 569f, c)
    // Zona roja 9 a 10 mil (y un poco más)
    drawPath(banda(c, 375f, 569f, gRpm(9000f), gRpm(10000f)), Brush.radialGradient(listOf(Color(0xFFC0000A), Color(0xFFFF1A10)), c, 580f))
    // Rayitas finas en el borde interno del anillo
    for (i in 0..100) {
        val g = gRpm(i * 100f)
        drawLine(Color(0xFF9A9A98), polar(c, 548f, g), polar(c, if (i % 5 == 0) 520f else 535f, g), 2f)
    }
    for (i in 0..10) {
        if (i == 1 || i == 3) {
            val g = gRpm(i * 1000f)
            drawLine(Color.Black, polar(c, 569f, g), polar(c, 522f, g), 9f)
            continue
        }
        val g = gRpm(i * 1000f)
        drawLine(Color.Black, polar(c, 569f, g), polar(c, 522f, g), 9f)
        val pt = polar(c, 438f, when (i) { 9 -> g - 6f; 10 -> g + 7f; else -> g })
        texto(p, "$i", pt.x, pt.y, 84f, Color(0xFF0A0A0A), FBarlow, FontWeight.Medium)
    }
    texto(p, "x1000", 1072f, 1140f, 30f, Color(0xFF0A0A0A), FBarlow, FontWeight.Medium, ancla = 0f)
    texto(p, "RPM", 1080f, 1172f, 30f, Color(0xFF0A0A0A), FBarlow, FontWeight.Medium, ancla = 0f)
    // Centro negro
    drawCircle(Color.Black, 375f, c)
    // Aguja azul
    val g = gRpm(d.rpmEn(10000f))
    val a = polar(c, 120f, g); val b = polar(c, 590f, g)
    brillo(linea(a, b), Color(0xFF1A4AFF), 10f, 12f)
    drawLine(Color(0xFF1E3CE0), a, b, 8f, StrokeCap.Round)
    texto(p, num(d.vel), c.x, 555f, 170f, blanco, FBarlow, FontWeight.Medium)
    texto(p, d.uVel, c.x, 638f, 46f, blanco, FBarlow, FontWeight.Medium)
    drawLine(Color(0xFF9A9A9A), Offset(780f, 672f), Offset(1150f, 672f), 3f)
    val marcha = d.marcha?.toString() ?: d.palanca.toString()
    texto(p, marcha, c.x, 755f, 190f, blanco, FBarlow, FontWeight.Medium)
    texto(p, "R N", 822f, 760f, 54f, Color(0xFF3A3A3A), FBarlow, FontWeight.SemiBold, espaciado = 8f)
    caja(878f, 818f, 176f, 44f, Brush.verticalGradient(listOf(Color(0xFFFF7A1A), Color(0xFFE85A0A))), radio = 2f)
    texto(p, d.modo, 966f, 840f, 38f, Color(0xFF2A0A00), FBarlow, FontWeight.SemiBold, espaciado = 1f)
    texto(p, "▲ Inicio", 965f, 910f, 36f, blanco, FBarlow)
    val s = d.viajeSeg
    val cent = ((s * 100).toLong() % 100).toInt()
    val tiempo = String.format(java.util.Locale.US, "%d:%02d:%02d.%02d", (s / 3600).toInt(), (s / 60 % 60).toInt(), (s % 60).toInt(), cent)
    texto(p, tiempo, 965f, 958f, 62f, blanco, FBarlow, FontWeight.Medium)
    texto(p, "▼ Reiniciar", 965f, 1002f, 36f, blanco, FBarlow)

    // Arcos laterales
    fun lateral(desdeY: Float, hastaY: Float, izquierda: Boolean, frac: Float, etiquetas: List<String>, t: Testigo, iconoY: Float) {
        val x0 = if (izquierda) 80f else 1920f
        val curva = if (izquierda) 70f else -70f
        fun punto(f: Float): Offset {
            val y = lerp(desdeY, hastaY, f)
            val u = (f - 0.5f) * 2f
            return Offset(x0 + curva * (1f - u * u) * -1f + (if (izquierda) 70f else -70f), y)
        }
        // Barra
        for (i in 0 until 40) {
            val f0 = i / 40f; val f1 = (i + 1) / 40f
            val lleno = f1 <= frac
            drawLine(if (lleno) Color(0xFFC8C8C8) else Color(0xFF2A2A2A), punto(f0), punto(f1), 22f)
        }
        val borde = Path().apply { for (i in 0..40) { val q = punto(i / 40f); val o = Offset(q.x + (if (izquierda) -20f else 20f), q.y); if (i == 0) moveTo(o.x, o.y) else lineTo(o.x, o.y) } }
        drawPath(borde, Color(0xFF8A8A8A), style = Stroke(3f))
        etiquetas.forEachIndexed { i, s ->
            val q = punto(i / (etiquetas.size - 1f))
            texto(p, s, q.x + (if (izquierda) 40f else -40f), q.y, 40f, blanco, FBarlow, ancla = if (izquierda) 0f else 1f)
            drawLine(blanco, Offset(q.x + (if (izquierda) 10f else -10f), q.y), Offset(q.x + (if (izquierda) 30f else -30f), q.y), 3f)
        }
        testigo(t, if (izquierda) 260f else 1680f, iconoY, 80f, blanco, true, 0f)
    }
    lateral(640f, 240f, true, fraccion(d.deA(d.refrigerante, 130f, 40f), 40f, 130f), listOf("40", "70", "100", "130"), Testigo.TEMPERATURA, 490f)
    lateral(1190f, 840f, true, d.deA(d.nafta, 100f) / 100f, listOf("0", "1/2", "1/1"), Testigo.NAFTA, 980f)
    lateral(640f, 240f, false, fraccion(d.deA(d.aceite ?: d.admision, 160f, 40f), 40f, 160f), listOf("40", "100", "160"), Testigo.ACEITE, 490f)
    lateral(1190f, 840f, false, fraccion(d.deA(d.multiple, 200f), 0f, 200f), listOf("0", "1", "2"), Testigo.MOTOR, 990f)
    texto(p, "x100 kPa", 1790f, 1192f, 30f, blanco, FBarlow, ancla = 1f)
    texto(p, num(d.ambiente) + " °C", 150f, 740f, 66f, blanco, FBarlow, FontWeight.Medium)
    texto(p, DatosTablero.hora(), 1815f, 740f, 76f, blanco, FBarlow, FontWeight.Medium)
}

// ═════════════════════════════════════════════════════════════════════════════
// 8 · Todoterreno de lujo: relojes plateados y el auto de perfil al medio
// ═════════════════════════════════════════════════════════════════════════════

@Composable
fun TableroTodoterreno(d: DatosTablero, r: Recursos, modifier: Modifier) = Lienzo(1999f, modifier) { p ->
    drawRect(Brush.radialGradient(listOf(Color(0xFF15181C), Color(0xFF07080A), Color.Black), Offset(1000f, 450f), 1200f), size = Size(1999f, 890f))
    val gris = Color(0xFF9EA6B0)
    val blanco = Color(0xFFECEFF3)

    // [cercano]: el número más cerca de la aguja se ilumina en blanco, como en la referencia
    fun reloj(c: Offset, etiquetas: List<Pair<String, Boolean>>, angulo: (Int) -> Float, cercano: Int, gAguja: Float) {
        drawCircle(Brush.radialGradient(listOf(Color(0xFF101215), Color(0xFF07080A)), c, 345f), 345f, c)
        biselMetal(c, 350f, 18f, tinte = Color(0xFF7A8AA0))
        etiquetas.forEachIndexed { i, (s, grande) ->
            val g = angulo(i)
            drawLine(blanco, polar(c, 322f, g), polar(c, 300f, g), 6f)
            val pt = polar(c, if (grande) 236f else 262f, g)
            val luz = i == cercano
            texto(p, s, pt.x, pt.y, (if (grande) 56f else 34f) * (if (luz) 1.1f else 1f), if (luz) blanco else gris, FBarlow, if (luz) FontWeight.SemiBold else FontWeight.Medium)
        }
        for (i in 0 until (etiquetas.size - 1) * 4) {
            val g = lerp(angulo(i / 4), angulo(i / 4 + 1), (i % 4) / 4f)
            if (i % 4 != 0) drawLine(Color(0xFF8A929C), polar(c, 322f, g), polar(c, 310f, g), 3f)
        }
        // Aguja plateada larga
        aguja(c, 300f, gAguja, 18f, 8f, Color(0xFFF2F4F6), desde = 30f, colorBase = Color(0xFF9AA2AC), brilloColor = Color.White, brilloRadio = 6f)
        drawLine(Color(0xFF6A727C), polar(c, 40f, gAguja), polar(c, 290f, gAguja), 3f)
    }
    // Vueltas: 0 a 6 mil en 270°
    val cr = Offset(400f, 490f)
    val gR = 135f + 270f * d.rpmEn(6000f) / 6000f
    reloj(cr, (0..6).map { "$it" to true }, { 135f + it * 45f }, Math.round(d.rpmEn(6000f) / 1000f), gR)
    for (j in 0..4) {
        val g = 135f + 270f * (4.1f + j * 0.2f) / 6f
        drawLine(Color(0xFFFF2A1A), polar(cr, 322f, g), polar(cr, 300f, g), 6f)
    }
    drawPath(arco(cr, 306f, 135f + 270f * 5f / 6f, 135f + 270f * 6.3f / 6f), Color(0xFFFF1A10), style = Stroke(7f))
    texto(p, "r/min x 1000", 398f, 330f, 32f, blanco, FBarlow, FontWeight.Medium)
    testigo(Testigo.ADVERTENCIA, 402f, 382f, 62f, Color(0xFFFFA020), d.prueba || d.testigoMotor)
    testigo(Testigo.CINTURON, 512f, 440f, 50f, Color(0xFFFF2A1A), d.prueba)
    caja(342f, 545f, 120f, 62f, Brush.verticalGradient(listOf(Color(0xFF15181C), Color(0xFF0B0D10))), Color(0xFF3A4048), 4f, 2f)
    texto(p, d.palanca.let { if (it == 'P') "N" else it.toString() }, 402f, 576f, 62f, blanco, FBarlow, FontWeight.SemiBold)
    // Temperatura abajo del reloj
    val ct = Offset(405f, 790f)
    drawPath(arco(ct, 85f, 210f, 330f), Color(0xFFB8C0CA), style = Stroke(3f))
    drawLine(Color(0xFF2A8CFF), polar(ct, 85f, 212f), polar(ct, 70f, 212f), 7f)
    drawLine(Color(0xFFFF2A1A), polar(ct, 85f, 328f), polar(ct, 70f, 328f), 7f)
    aguja(ct, 92f, 210f + 120f * fraccion(d.deA(d.refrigerante, 130f, 40f), 40f, 130f), 14f, 5f, Color.White, desde = -6f, colorBase = Color(0xFFAAB2BC))
    testigo(Testigo.TEMPERATURA, 330f, 752f, 42f, blanco, true, 0f)

    // Velocidad: 1 grado por km/h desde 145°
    val cv = Offset(1600f, 490f)
    val etV = (1..12).map { "${it * 20}" to (it % 2 == 1) }
    reloj(cv, etV, { 165f + it * 20f }, Math.round(d.velEn(240f) / 20f) - 1, 145f + d.velEn(240f))
    testigo(Testigo.FRENO, 1595f, 368f, 60f, Color(0xFFFF2A1A), d.prueba)
    texto(p, d.uVel, 1595f, 322f, 32f, blanco, FBarlow, FontWeight.Medium)
    // Nafta abajo
    val cn = Offset(1600f, 790f)
    drawPath(arco(cn, 85f, 210f, 330f), Color(0xFFB8C0CA), style = Stroke(3f))
    texto(p, "0", 1518f, 690f + 0f, 34f, blanco, FBarlow)
    texto(p, "1/2", 1600f, 668f, 34f, blanco, FBarlow)
    texto(p, "1", 1680f, 690f, 34f, blanco, FBarlow)
    drawLine(Color(0xFFFF2A1A), polar(cn, 85f, 212f), polar(cn, 70f, 212f), 7f)
    aguja(cn, 92f, 210f + 120f * d.deA(d.nafta, 100f) / 100f, 14f, 5f, Color.White, desde = -6f, colorBase = Color(0xFFAAB2BC))
    texto(p, "Nafta", 1470f, 748f, 26f, blanco, FBarlow, FontWeight.Medium)
    testigo(Testigo.NAFTA, 1690f, 740f, 44f, blanco, true, 0f)

    // Centro: el auto de perfil sobre un piso iluminado
    drawRect(Brush.verticalGradient(listOf(Color.Transparent, Color(0x6694B4D8), Color(0x2294B4D8)), 520f, 620f), Offset(735f, 520f), Size(530f, 100f))
    drawRoundRect(Brush.verticalGradient(listOf(Color(0xFFAFC8E4), Color(0xFF5E7A9A)), 568f, 600f), Offset(735f, 568f), Size(530f, 30f), CornerRadius(14f))
    r.foto?.let { foto(it, Offset(1000f, 470f), 470f, 210f) }
    texto(p, DatosTablero.hora24(), 828f, 255f, 52f, blanco, FBarlow, FontWeight.Medium)
    texto(p, num(d.ambiente) + "°C", 1175f, 250f, 52f, blanco, FBarlow, FontWeight.Medium)
    testigo(Testigo.BAJAS, 840f, 175f, 56f, Color(0xFF3AE03A), d.prueba || d.motorEnMarcha)
    val terrenos = listOf(Testigo.PUERTA, Testigo.COPO, Testigo.ACEITE, Testigo.LIMPIA, Testigo.ADVERTENCIA)
    terrenos.forEachIndexed { i, t -> testigo(t, 808f + i * 98f, 733f, 62f, if (i == 0) Color(0xFFFF8A1A) else Color(0xFFDDE2E8), true, 0f) }
    drawLine(Color(0xFFFF8A1A), Offset(772f, 772f), Offset(845f, 772f), 4f)
    texto(p, DatosTablero.fecha(), 795f, 805f, 46f, blanco, FBarlow, FontWeight.Medium)
    texto(p, num(d.odometro ?: d.viajeKm).padStart(6, '0'), 1095f, 805f, 46f, blanco, FBarlow, FontWeight.Medium)
    texto(p, "km", 1230f, 808f, 32f, blanco, FBarlow, ancla = 0f)
}

// ═════════════════════════════════════════════════════════════════════════════
// 9 · Info activa: discos metálicos, potencia a la izquierda y velocidad a la derecha
// ═════════════════════════════════════════════════════════════════════════════

@Composable
fun TableroInfoActiva(d: DatosTablero, r: Recursos, modifier: Modifier) = Lienzo(2000f, modifier) { p ->
    drawRect(Brush.radialGradient(listOf(Color(0xFF26282C), Color(0xFF111215), Color(0xFF07080A)), Offset(1000f, 450f), 1200f), size = Size(2000f, 964f))
    val blanco = Color(0xFFF2F2F2)

    /**
     * Disco con las proporciones de la referencia: aro cromado fino, banda oscura con las rayas, un anillo
     * hundido con rayitas finas, un bisel metálico y un centro chico y abombado (~30 % del radio).
     */
    fun disco(c: Offset, radio: Float) {
        drawCircle(Brush.radialGradient(listOf(Color(0xFF2C2E33), Color(0xFF222428)), c, radio), radio, c)
        drawCircle(Brush.sweepGradient(listOf(Color(0xFF7A7E84), Color(0xFFF4F4F4), Color(0xFF6A6E74), Color(0xFFE0E0E0), Color(0xFF7A7E84)), c), radio, c, style = Stroke(9f))
        drawCircle(Brush.radialGradient(listOf(Color(0xFF15161A), Color(0xFF1E2024)), c, radio * 0.73f), radio * 0.73f, c)
        for (i in 0 until 120) {
            val g = i * 3f
            drawLine(Color(0x66B8BCC4), polar(c, radio * 0.56f, g), polar(c, radio * 0.6f, g), 1.5f)
        }
        drawCircle(
            Brush.radialGradient(0f to Color(0xFF3A3D42), 0.6f to Color(0xFF5A5E64), 0.85f to Color(0xFFA4A8AE), 1f to Color(0xFF4A4E54), center = c, radius = radio * 0.515f),
            radio * 0.515f, c,
        )
        drawCircle(Brush.sweepGradient(listOf(Color(0xFF8A8E94), Color(0xFFE4E4E4), Color(0xFF6A6E74), Color(0xFFC8C8C8), Color(0xFF8A8E94)), c), radio * 0.515f, c, style = Stroke(4f))
        drawCircle(
            Brush.radialGradient(listOf(Color(0xFF8C9096), Color(0xFF5C6066), Color(0xFF3A3D42)), Offset(c.x - radio * 0.06f, c.y - radio * 0.1f), radio * 0.36f),
            radio * 0.31f, c,
        )
        drawCircle(Color(0x55000000), radio * 0.31f, c, style = Stroke(3f))
    }
    // Potencia
    val cp = Offset(451f, 489f)
    disco(cp, 398f)
    val g0 = 194f; val g100 = 370f
    for (i in 0..100) {
        val g = lerp(g0, g100, i / 100f)
        val largo = if (i % 20 == 0) 34f else if (i % 10 == 0) 24f else 14f
        drawLine(blanco, polar(cp, 375f, g), polar(cp, 375f - largo, g), if (i % 10 == 0) 4f else 2f)
    }
    for (i in 0..5) {
        val pt = polar(cp, 300f, lerp(g0, g100, i / 5f))
        texto(p, "${i * 20}", pt.x, pt.y, 58f, blanco, FBarlow, FontWeight.Medium)
    }
    for (i in 0..8) drawLine(blanco, polar(cp, 375f, lerp(130f, 190f, i / 8f)), polar(cp, 360f, lerp(130f, 190f, i / 8f)), 3f)
    adentro(p, "PWR", cp, 350f, 160f, 34f, FBarlow, FontWeight.Medium).let { texto(p, "PWR", it.x, it.y, 34f, blanco, FBarlow, FontWeight.Medium) }
    adentro(p, "OFF", cp, 350f, 130f, 34f, FBarlow, FontWeight.Medium).let { texto(p, "OFF", it.x, it.y, 34f, blanco, FBarlow, FontWeight.Medium) }
    adentro(p, "BOOST", cp, 350f, 40f, 34f, FBarlow, FontWeight.Medium).let { texto(p, "BOOST", it.x, it.y, 34f, blanco, FBarlow, FontWeight.Medium) }
    for (i in 0..5) drawLine(blanco, polar(cp, 375f, lerp(14f, 45f, i / 5f)), polar(cp, 360f, lerp(14f, 45f, i / 5f)), 3f)
    val gp = lerp(g0, g100, d.deA(d.potencia, 100f) / 100f)
    val a = polar(cp, 208f, gp); val b = polar(cp, 378f, gp)
    brillo(linea(a, b), Color(0xFFFF1A0A), 8f, 8f)
    drawLine(Color(0xFFE81A10), a, b, 7f, StrokeCap.Round)
    texto(p, "%PWR", 451f, 430f, 30f, blanco, FBarlow, FontWeight.Medium)
    texto(p, num(d.potencia), 451f, 503f, 120f, blanco, FBarlow, FontWeight.Normal)
    // Batería (tensión) abajo
    val fv = fraccion(d.deA(d.tension, 14.5f, 11.5f), 11.5f, 14.5f)
    drawPath(arco(cp, 330f, 115f, 65f), Color(0xFF3A3C40), style = Stroke(12f))
    drawPath(arco(cp, 330f, 115f, 115f - 50f * fv), Color(0xFFE8E8E8), style = Stroke(12f))
    texto(p, "11,5V", 368f, 755f, 24f, blanco, FBarlow)
    texto(p, "14,5V", 548f, 755f, 24f, blanco, FBarlow)
    texto(p, "BATERÍA", 451f, 738f, 28f, blanco, FBarlow, FontWeight.SemiBold)
    texto(p, num(d.tension, 1) + " V", 451f, 772f, 30f, blanco, FBarlow, FontWeight.SemiBold)

    // Velocidad
    val cv = Offset(1565f, 375f)
    disco(cv, 330f)
    // Como en la referencia: 0-60 comprimido, 60-160 abierto (ahí están los números grandes), 160-260 comprimido
    fun gV(v: Float): Float = 195f + 205f * when {
        v <= 60f -> 0.22f * v / 60f
        v <= 160f -> 0.22f + 0.5f * (v - 60f) / 100f
        else -> 0.72f + 0.28f * ((v - 160f) / 100f).coerceAtMost(1f)
    }
    for (i in 0..130) {
        val g = gV(i * 2f)
        val largo = if (i % 10 == 0) 26f else if (i % 5 == 0) 18f else 10f
        drawLine(blanco, polar(cv, 300f, g), polar(cv, 300f - largo, g), if (i % 10 == 0) 4f else 1.5f)
    }
    for (i in 0..13) {
        val v = i * 20f
        val tam = if (v in 100f..140f) 44f else 30f
        val pt = adentro(p, "${v.toInt()}", cv, 270f, gV(v), tam, FBarlow, FontWeight.Medium)
        texto(p, "${v.toInt()}", pt.x, pt.y, tam, blanco, FBarlow, FontWeight.Medium)
    }
    val gv = gV(d.velEn(260f))
    val va = polar(cv, 172f, gv); val vb = polar(cv, 305f, gv)
    brillo(linea(va, vb), Color(0xFFFF1A0A), 8f, 8f)
    drawLine(Color(0xFFE81A10), va, vb, 7f, StrokeCap.Round)
    texto(p, d.uVel.uppercase().replace("/", "|"), 1565f, 335f, 26f, blanco, FBarlow, FontWeight.Medium)
    texto(p, num(d.vel), 1565f, 395f, 96f, blanco, FBarlow, FontWeight.Normal)
    val fn = d.deA(d.nafta, 100f) / 100f
    drawPath(arco(cv, 268f, 120f, 70f), Color(0xFF3A3C40), style = Stroke(10f))
    drawPath(arco(cv, 268f, 120f, 120f - 50f * fn), Color(0xFFE8E8E8), style = Stroke(10f))
    texto(p, "NAFTA", 1565f, 570f, 26f, blanco, FBarlow, FontWeight.SemiBold)
    texto(p, num(d.autonomia) + " KM", 1565f, 600f, 28f, blanco, FBarlow, FontWeight.SemiBold)
    texto(p, "0%", 1462f, 590f, 22f, blanco, FBarlow)
    texto(p, "100%", 1668f, 590f, 22f, blanco, FBarlow)

    // Pantalla del medio
    texto(p, num(d.ambiente) + "°C", 800f, 148f, 46f, blanco, FBarlow, FontWeight.Medium)
    drawCircle(blanco, 16f, Offset(880f, 140f), style = Stroke(4f))
    texto(p, DatosTablero.hora(), 1035f, 135f, 50f, blanco, FBarlow, FontWeight.SemiBold)
    drawCircle(blanco, 15f, Offset(1225f, 125f), style = Stroke(4f))
    texto(p, "N", 1262f, 125f, 38f, blanco, FBarlow, FontWeight.SemiBold)
    texto(p, d.marca, 1022f, 195f, 40f, blanco, FBarlow, FontWeight.Medium)
    texto(p, d.modelo + " · Gappribaldi", 1022f, 240f, 34f, blanco, FBarlow, alfa = 0.85f)
    drawCircle(Brush.radialGradient(listOf(Color(0xFF6A6E74), Color(0xFF2A2C30)), Offset(1045f, 380f), 110f), 105f, Offset(1045f, 380f))
    drawCircle(Color(0xFF9A9EA4), 105f, Offset(1045f, 380f), style = Stroke(4f))
    r.logo?.let { foto(it, Offset(1045f, 380f), 120f, 90f) }
    drawRoundRect(Color(0xFF3A3C40), Offset(930f, 535f), Size(270f, 8f), CornerRadius(4f))
    drawRoundRect(Color(0xFFD8D8D8), Offset(930f, 535f), Size(270f * fraccion(d.viajeKm, 0f, 50f), 8f), CornerRadius(4f))
    texto(p, "Viaje: " + num(d.viajeKm, 1) + " km", 1070f, 580f, 40f, blanco, FBarlow, FontWeight.Medium)
    texto(p, num(d.odometro ?: d.viajeKm) + " KM", 895f, 735f, 40f, blanco, FBarlow, FontWeight.SemiBold)
    texto(p, d.textoMarcha, 1080f, 680f, 60f, blanco, FBarlow, FontWeight.Medium)
    texto(p, d.modo, 1080f, 718f, 28f, blanco, FBarlow, FontWeight.Medium)
    texto(p, num(d.autonomia) + " KM", 1300f, 678f, 40f, blanco, FBarlow, FontWeight.SemiBold)
}

// ═════════════════════════════════════════════════════════════════════════════
// 10 · Roadster LCD: barra de vueltas curva hasta 9.000 y velocidad en siete segmentos
// ═════════════════════════════════════════════════════════════════════════════

@Composable
fun TableroRoadster(d: DatosTablero, r: Recursos, modifier: Modifier) = Lienzo(1999f, modifier) { p ->
    drawRect(Color(0xFF050505), size = Size(1999f, 478f))
    val naranja = Color(0xFFFF7A1E)
    val apagado = Color(0xFF3A2414)
    val blanco = Color(0xFFE8E8E8)
    // Escala de vueltas (medida de la referencia): un arco de círculo enorme, centro muy abajo, que sube desde
    // el 0 y llega a su punto más alto cerca del 7-8; del 8 al 9 sigue la misma curva, ya bajando.
    // La banda tiene grosor parejo y va por encima de la línea.
    // Vista de frente (la foto está sacada desde abajo): círculo más abierto que entra entero en el lienzo
    val cE = Offset(1080f, 1755f); val rE = 1660f
    val a0 = 235.9f; val a8 = 269.3f; val a9 = 274.5f
    fun gE(v: Float): Float = if (v <= 8000f) lerp(a0, a8, v / 8000f) else lerp(a8, a9, ((v - 8000f) / 1000f).coerceAtMost(1f))
    val rpm = d.rpmEn(9000f)
    val inicio = 234.6f
    drawPath(banda(cE, rE, rE + 60f, inicio, a8, pasos = 120), Brush.horizontalGradient(listOf(Color(0xFF3A2410), Color(0xFF4A2E12), Color(0xFF3E2610)), 80f, 1060f))
    val hastaLuz = gE(minOf(rpm, 8000f)).coerceAtLeast(inicio + 1.2f)
    val encendida = banda(cE, rE, rE + 60f, inicio, hastaLuz, pasos = 120)
    brillo(encendida, naranja, 14f)
    drawPath(encendida, Brush.verticalGradient(listOf(Color(0xFFFF9A3A), naranja, Color(0xFFE85A10)), 40f, 460f))
    // Del 8 al 9: barras inclinadas que siguen la misma curva, con su línea roja abajo
    val roja = rpm >= 8000f
    for (i in 0 until 12) {
        val g = lerp(a8 + 0.5f, a9 + 1.4f, i / 11f)
        // Mismo alto que la banda de vueltas
        val a = polar(cE, rE, g); val b = polar(cE, rE + 60f, g + 0.6f)
        val lit = roja && g <= gE(rpm) + 0.3f
        if (lit) brillo(linea(a, b), Color(0xFFFF3A1A), 8f, 6f)
        drawLine(if (lit) Color(0xFFFF4A1E) else Color(0xFFD2481C), a, b, 5f)
    }
    drawPath(arco(cE, rE, a8, a9 + 1.6f, pasos = 20), Color(0xFFE2501E), style = Stroke(4f))
    // Línea blanca con sus bloquecitos (cada 500) y los números debajo
    drawPath(arco(cE, rE, a0 - 0.5f, a8, pasos = 120), Color(0xFFE8E8E8), style = Stroke(3f))
    for (k in 0..16) {
        val g = lerp(a0, a8, k / 16f)
        drawLine(Color(0xFFE8E8E8), polar(cE, rE + 1f, g), polar(cE, rE - 15f, g), if (k % 2 == 0) 9f else 5f)
    }
    for (k in 0..9) {
        val g = if (k <= 8) lerp(a0, a8, k / 8f) else a9
        val q = polar(cE, rE - if (k == 0) 56f else 46f, g)
        texto(p, "$k", q.x + (if (k == 0) 14f else 0f), q.y, if (k == 0) 72f else 52f, blanco, FBarlow, FontWeight.Normal)
    }
    texto(p, "x1000r/min", 222f, 446f, 26f, blanco, FBarlow, FontWeight.Normal, ancla = 0f)

    // Velocidad en siete segmentos
    // Pantallas LCD debajo del arco, sin tocar los números de la escala
    caja(780f, 214f, 370f, 134f, Brush.verticalGradient(listOf(Color(0xFF2A0A06), Color(0xFF1A0604))), radio = 6f)
    texto(p, "888", 1140f, 281f, 132f, Color(0xFF3A0E08), FSiete, ancla = 1f)
    texto(p, num(d.vel).padStart(3, '!'), 1140f, 281f, 132f, Color(0xFFFF4A1A), FSiete, ancla = 1f, brilloR = 16f)
    texto(p, d.uVel, 1162f, 318f, 34f, Color(0xFFFF4A1A), FBarlow, FontWeight.Medium, ancla = 0f, brilloR = 6f)
    caja(735f, 358f, 470f, 96f, Brush.verticalGradient(listOf(Color(0xFF240804), Color(0xFF180503))), radio = 6f)
    val lcd = Color(0xFFFF5A2A)
    texto(p, num(d.ambiente) + "°C", 760f, 381f, 36f, lcd, FBarlow, FontWeight.SemiBold, ancla = 0f, brilloR = 6f)
    texto(p, DatosTablero.hora(), 1040f, 381f, 38f, lcd, FSiete, ancla = 0.5f, brilloR = 6f)
    drawLine(lcd, Offset(740f, 405f), Offset(1200f, 405f), 3f)
    texto(p, num(d.odometro ?: d.viajeKm).padStart(6, '0'), 750f, 431f, 34f, lcd, FSiete, ancla = 0f, brilloR = 6f)
    texto(p, "VIAJE", 1030f, 420f, 20f, lcd, FBarlow, FontWeight.SemiBold)
    texto(p, "A", 1030f, 442f, 20f, lcd, FBarlow, FontWeight.SemiBold)
    texto(p, num(d.viajeKm, 1), 1192f, 431f, 38f, lcd, FSiete, ancla = 1f, brilloR = 6f)

    // Temperatura y nafta: dos arcos separados (medidos de la referencia), cada uno con una línea blanca fina,
    // una banda segmentada por encima, una marca blanca/naranja al principio y una roja al final
    fun arcoSeg(c: Offset, rLinea: Float, frac: Float, izq: String, der: String, t: Testigo) {
        // Semicírculo de frente: empieza y termina horizontal
        val desde = 180f; val hasta = 360f
        drawPath(banda(c, rLinea + 6f, rLinea + 44f, desde, hasta), Color(0xFF2E1C0C))
        val segs = 28
        for (i in 0 until segs) {
            val g0 = lerp(desde, hasta, i / segs.toFloat()) + 0.5f; val g1 = lerp(desde, hasta, (i + 1) / segs.toFloat()) - 0.5f
            val on = (i + 0.5f) / segs <= frac
            val s = banda(c, rLinea + 6f, rLinea + 44f, g0, g1, pasos = 3)
            if (on) brillo(s, naranja, 6f)
            drawPath(s, if (on) naranja else apagado)
        }
        drawPath(arco(c, rLinea, desde + 2f, hasta - 2f), Color(0xFFE8E8E8), style = Stroke(3f))
        drawLine(Color(0xFFE8E8E8), polar(c, rLinea - 4f, desde), polar(c, rLinea + 44f, desde), 7f)
        drawLine(Color(0xFFFF3A1A), polar(c, rLinea - 4f, hasta), polar(c, rLinea + 44f, hasta), 7f)
        texto(p, izq, c.x - rLinea + 28f, c.y - 22f, 34f, blanco, FBarlow, FontWeight.Medium)
        texto(p, der, c.x + rLinea - 28f, c.y - 22f, 34f, blanco, FBarlow, FontWeight.Medium)
        testigo(t, c.x, c.y - rLinea * 0.42f, 40f, blanco, true, 0f)
    }
    arcoSeg(Offset(1440f, 236f), 112f, fraccion(d.deA(d.refrigerante, 130f, 40f), 40f, 130f), "C", "H", Testigo.TEMPERATURA)
    arcoSeg(Offset(1720f, 436f), 112f, d.deA(d.nafta, 100f) / 100f, "E", "F", Testigo.NAFTA)
}
