package ar.garibaldi.obd

import ar.garibaldi.obd.adapter.Elm327
import ar.garibaldi.obd.auto.Auto
import ar.garibaldi.obd.modules.CatalogoModulos
import ar.garibaldi.obd.dtc.DtcDecoder
import ar.garibaldi.obd.dtc.DtcRepositorio
import ar.garibaldi.obd.dtc.FuenteInfo
import ar.garibaldi.obd.dtc.Gravedad
import ar.garibaldi.obd.dtc.OrigenDtc
import ar.garibaldi.obd.log.FrameLog
import ar.garibaldi.obd.modules.Categoria
import ar.garibaldi.obd.modules.Descubridor
import ar.garibaldi.obd.obd.Obd2
import ar.garibaldi.obd.obd.Pids
import ar.garibaldi.obd.protocol.Diagnostico
import ar.garibaldi.obd.protocol.IsoTp
import ar.garibaldi.obd.protocol.Protocolo
import ar.garibaldi.obd.protocol.SafetyGuard
import ar.garibaldi.obd.protocol.SolicitudBloqueada
import ar.garibaldi.obd.protocol.hex
import ar.garibaldi.obd.protocol.hexABytes
import ar.garibaldi.obd.scan.Escaner
import ar.garibaldi.obd.scan.Historial
import ar.garibaldi.obd.scan.Informe
import ar.garibaldi.obd.transport.SimulatedTransport
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class IsoTpTest {
    @Test fun tramaUnicaConYSinEspacios() {
        val a = IsoTp.parsearLinea("7E8 06 41 00 BE 3E A8 13")!!
        val b = IsoTp.parsearLinea("7E8064100BE3EA813")!!
        assertEquals(0x7E8, a.id)
        assertEquals(a.datos.hex(), b.datos.hex())
        val m = IsoTp.reensamblar(listOf(a)).single()
        assertEquals("4100BE3EA813", m.datos.hex())
        assertTrue(m.completo)
    }

    @Test fun lineasQueNoSonTramas() {
        assertNull(IsoTp.parsearLinea("NO DATA"))
        assertNull(IsoTp.parsearLinea("OK"))
        assertNull(IsoTp.parsearLinea("7E8064")) // largo par: falta medio byte
    }

    @Test fun mensajeDeVariasTramasIntercaladoEntreDosModulos() {
        val lineas = listOf(
            "7E8 10 14 49 02 01 57 44 44", "7E9 03 7F 09 11",
            "7E8 21 47 46 35 36 58 30 37", "7E8 22 46 31 32 33 34 35 36",
        )
        val mensajes = IsoTp.reensamblar(lineas.map { IsoTp.parsearLinea(it)!! })
        assertEquals(2, mensajes.size)
        val vin = mensajes.first { it.id == 0x7E8 }
        assertTrue(vin.completo)
        assertEquals(20, vin.datos.size)
        assertEquals("WDDGF56X07F123456", String(vin.datos.drop(3).toByteArray()))
        assertEquals("7F0911", mensajes.first { it.id == 0x7E9 }.datos.hex())
    }

    @Test fun tramaPerdidaMarcaIncompleto() {
        val lineas = listOf("7E8 10 14 49 02 01 57 44 44", "7E8 22 46 31 32 33 34 35 36")
        val m = IsoTp.reensamblar(lineas.map { IsoTp.parsearLinea(it)!! }).single()
        assertFalse(m.completo)
    }
}

class DtcDecoderTest {
    @Test fun formatoSae() {
        assertEquals("P0171", DtcDecoder.sae(0x01, 0x71))
        assertEquals("C1120", DtcDecoder.sae(0x51, 0x20))
        assertEquals("B1041", DtcDecoder.sae(0x90, 0x41))
        assertEquals("U0155", DtcDecoder.sae(0xC1, 0x55))
        assertEquals("P2A00", DtcDecoder.sae(0x2A, 0x00))
    }

    @Test fun rangosGenericos() {
        assertTrue(DtcDecoder.esRangoGenerico("P0171"))
        assertTrue(DtcDecoder.esRangoGenerico("P2006"))
        assertTrue(DtcDecoder.esRangoGenerico("U0100"))
        assertFalse(DtcDecoder.esRangoGenerico("P1200"))
        assertFalse(DtcDecoder.esRangoGenerico("B1041"))
        assertFalse(DtcDecoder.esRangoGenerico("P3100"))
    }

    @Test fun obdSaltaRellenos() {
        val dtcs = DtcDecoder.obd(hexABytes("43 02 01 71 00 16 00 00"), OrigenDtc.OBD_CONFIRMADO)
        assertEquals(listOf("P0171", "P0016"), dtcs.map { it.codigo })
    }

    @Test fun kwpEstadoYCodigosPropios() {
        val datos = hexABytes("58 03 01 71 E0 00 16 20 90 41 00")
        val motor = DtcDecoder.kwp(datos, trenMotriz = true)
        assertEquals(listOf("P0171", "P0016"), motor.map { it.codigo }) // el de estado 00 no es una falla
        assertTrue(motor[0].estado.actual && motor[0].estado.luzTestigo)
        assertTrue(motor[1].estado.almacenado && !motor[1].estado.actual)
        // En un módulo de carrocería no se adivina: se muestra el hex y no hay descripción genérica
        val sam = DtcDecoder.kwp(hexABytes("58 01 90 41 20"), trenMotriz = false).single()
        assertEquals("9041", sam.codigo)
        assertNull(sam.claveGenerica)
    }

    @Test fun udsFiltraLosQueNoSonFalla() {
        val dtcs = DtcDecoder.uds(hexABytes("59 02 FF C1 55 00 08 9F 4A 13 50"))
        assertEquals("U015500", dtcs.single().codigo)
        assertEquals("U0155", dtcs.single().claveGenerica)
    }
}

class SafetyGuardTest {
    @Test fun lecturasPermitidas() {
        for (h in listOf("0100", "03", "07", "0A", "0902", "1A87", "1A86", "22F111", "180 2FF00".replace(" ", ""), "19020D", "3E00", "1092", "1003")) {
            SafetyGuard.verificar(hexABytes(h))
        }
    }

    @Test fun escriturasBloqueadas() {
        val prohibidos = listOf(
            "2701", "2E F1 90 00", "3B 10 00", "31 01 FF 00", "2F 10 00 03", "30 10 07", "34 00", "36 01", "37",
            "10 02", "10 85", "11 01", "28 00", "85 02", "22 01 00", "3D 00",
        )
        for (h in prohibidos) {
            assertThrows(h, SolicitudBloqueada::class.java) { SafetyGuard.verificar(hexABytes(h), borradoConfirmado = true) }
        }
    }

    @Test fun borrarExigeConfirmacion() {
        assertFalse(SafetyGuard.permitido(hexABytes("04")))
        assertFalse(SafetyGuard.permitido(hexABytes("14FF00")))
        assertTrue(SafetyGuard.permitido(hexABytes("04"), borradoConfirmado = true))
        assertTrue(SafetyGuard.permitido(hexABytes("14FFFFFF"), borradoConfirmado = true))
    }
}

class BaseDeCodigosTest {
    private val repo = DtcRepositorio { nombre -> File("src/main/assets/dtc", nombre).takeIf { it.exists() }?.inputStream() }

    private fun dtc(hex: String) = DtcDecoder.kwp(hexABytes("58 01 $hex 20"), trenMotriz = true).single()

    @Test fun fichaCuradaConNotaDelMotor() {
        val info = repo.buscar(dtc("00 16"), Categoria.MOTOR)
        assertEquals(FuenteInfo.CURADA, info.fuente)
        assertEquals(Gravedad.ROJO, info.gravedad)
        assertFalse(info.enIngles)
        assertNotNull(info.notaMotor)
        assertTrue(info.causas.isNotEmpty() && info.queRevisar.isNotEmpty())
    }

    @Test fun genericoDeObdex() {
        val info = repo.buscar(dtc("04 01"), Categoria.MOTOR) // P0401, sin ficha curada
        assertEquals(FuenteInfo.OBDEX, info.fuente)
        assertTrue(info.enIngles && info.gravedadEstimada)
        assertTrue(info.titulo.isNotEmpty() && info.causas.isNotEmpty())
    }

    @Test fun codigoPropioNoSeInventa() {
        val propio = DtcDecoder.kwp(hexABytes("58 01 90 41 20"), trenMotriz = false).single()
        val info = repo.buscar(propio, Categoria.CARROCERIA)
        assertEquals(FuenteInfo.NINGUNA, info.fuente)
        assertNull(info.explicacion)
        assertEquals(FuenteInfo.NINGUNA, repo.buscar(dtc("12 00"), Categoria.MOTOR).fuente) // P1200: de fabricante
    }

    @Test fun todasLasFichasCuradasSonCoherentes() {
        val codigos = repo.codigosCurados()
        assertTrue(codigos.size > 300)
        for (c in codigos) {
            assertTrue("$c fuera de rango genérico", DtcDecoder.esRangoGenerico(c))
            val d = DtcDecoder.obd(hexABytes("43 01 " + saeAHex(c)), OrigenDtc.OBD_CONFIRMADO).single()
            assertEquals(c, d.codigo)
            val info = repo.buscar(d, Categoria.MOTOR)
            assertEquals(c, FuenteInfo.CURADA, info.fuente)
            assertTrue(c, info.titulo.isNotBlank() && info.causas.isNotEmpty() && info.queRevisar.isNotEmpty())
        }
    }

    private fun saeAHex(c: String): String {
        val alto = ("PCBU".indexOf(c[0]) shl 6) or (c[1].digitToInt() shl 4) or c[2].digitToInt(16)
        return "%02X %s".format(alto, c.substring(3))
    }
}

/** Recorre el flujo completo contra el auto simulado: inicializar, descubrir, leer, borrar y releer. */
class FlujoCompletoTest {
    private val repo = DtcRepositorio { nombre -> File("src/main/assets/dtc", nombre).takeIf { it.exists() }?.inputStream() }

    @Test fun descubreLeeYBorra() = runBlocking {
        val log = FrameLog()
        val transporte = SimulatedTransport(demoraMs = 0)
        transporte.conectar()
        val elm = Elm327(transporte, log)
        val info = elm.inicializar()
        assertTrue(info.busOk)
        assertEquals("12.4V", info.voltaje)
        val diag = Diagnostico(elm)
        val obd = Obd2(diag)

        val modulos = Descubridor(diag, log).buscar(completo = false, conocidos = emptyList()) { _, _, _ -> }
        assertEquals(listOf(0x612, 0x632, 0x642, 0x662, 0x6A2, 0x7E0, 0x7E1), modulos.map { it.idPedido }.sorted())
        val porId = modulos.associateBy { it.idPedido }
        assertEquals(0x482, porId[0x612]!!.idRespuesta)
        assertEquals(Protocolo.UDS, porId[0x612]!!.protocolo)
        assertEquals(Protocolo.KWP, porId[0x7E0]!!.protocolo)
        assertTrue(porId[0x7E0]!!.identificacion.values.any { "2729004400" in it })

        val escaner = Escaner(diag, obd)
        val r = escaner.escanear(modulos, info.voltaje, { _, _, _ -> }, { _, _ -> })
        assertEquals("WDDGF56X07F123456", r.vin)
        assertEquals(true, r.testigoMotor)
        val codigos = r.modulos.associate { it.clave to it.dtcs.map { d -> d.codigo } }
        assertEquals(listOf("P0171", "P0016", "P2006"), codigos["obd-7E8"])
        assertEquals(listOf("P0171", "P0016", "P2006"), codigos["mod-7E0"])
        assertEquals(listOf("P0717"), codigos["mod-7E1"])
        assertEquals(listOf("5120"), codigos["mod-632"])
        assertEquals(listOf("9041"), codigos["mod-642"])
        assertEquals(listOf("U015500"), codigos["mod-662"])
        assertEquals(emptyList<String>(), codigos["mod-612"])
        // Este módulo exige abrir sesión extendida antes de dejar leer
        assertNull(r.modulos.first { it.clave == "mod-6A2" }.error)
        assertTrue(r.modulos.none { it.error != null })

        // El historial ida y vuelta conserva todo
        val copia = Historial.deJson(Historial.aJson(r))
        assertEquals(r.clavesDeCodigos(), copia.clavesDeCodigos())
        val informe = Informe.texto(r, repo)
        assertTrue("P0171" in informe && "Mezcla pobre" in informe && "sin descripción local" in informe)

        // Datos en vivo y cuadro congelado
        val soportados = obd.pidsSoportados()
        assertTrue(0x0C in soportados && 0x42 in soportados)
        assertEquals(90.0, obd.leerPid(Pids.porId(0x05)!!)!!, 0.01)
        assertEquals("P0171", obd.cuadroCongelado(soportados)!!.codigo)

        // Borrado por módulo y relectura
        val esp = porId[0x632]!!
        assertNull(diag.borrarDtc(esp.idPedido, esp.idRespuesta, esp.protocolo!!))
        assertTrue(obd.borrar())
        val r2 = escaner.escanear(modulos, null, { _, _, _ -> }, { _, _ -> })
        assertTrue(r2.modulos.first { it.clave == "mod-632" }.dtcs.isEmpty())
        assertTrue(r2.modulos.first { it.clave == "obd-7E8" }.dtcs.isEmpty())
        assertEquals(listOf("9041"), r2.modulos.first { it.clave == "mod-642" }.dtcs.map { it.codigo })
        assertTrue(r2.monitoresPendientes.isNotEmpty())

        // Nada de lo que salió hacia el auto es un servicio prohibido
        val enviados = log.copia().filter { " TX " in it }.map { it.substringAfter(" TX ") }
            .filter { !it.startsWith("AT") && !it.startsWith("ST") }
        assertTrue(enviados.isNotEmpty())
        for (h in enviados) assertTrue(h, SafetyGuard.permitido(hexABytes(h), borradoConfirmado = true))
    }
}

class IdiomaTest {
    private val repo = DtcRepositorio { nombre -> File("src/main/assets/dtc", nombre).takeIf { it.exists() }?.inputStream() }

    @org.junit.After fun volverAlEspanol() {
        ar.garibaldi.obd.i18n.Idioma.ingles = false
    }

    @Test fun traduceFrasesCompuestas() {
        val idioma = ar.garibaldi.obd.i18n.Idioma
        assertEquals("Lo más grave: Puede esperar", idioma.L("Lo más grave: Puede esperar")) // en español no cambia
        idioma.ingles = true
        assertEquals("Most serious: Can wait", idioma.L("Lo más grave: Puede esperar"))
        assertEquals("Engine (ME 9.7) (probable)", idioma.L("Motor (ME 9.7) (probable)"))
        assertEquals("Present now · stored", idioma.L("Presente ahora · guardado"))
        assertEquals("P0171", idioma.L("P0171")) // lo desconocido queda igual
    }

    @Test fun fichaCuradaEnIngles() {
        val dtc = DtcDecoder.kwp(hexABytes("58 01 00 16 20"), trenMotriz = true).single()
        ar.garibaldi.obd.i18n.Idioma.ingles = true
        val info = repo.buscar(dtc, Categoria.MOTOR)
        assertEquals(FuenteInfo.CURADA, info.fuente)
        assertEquals(Gravedad.ROJO, info.gravedad) // la gravedad de la ficha curada se conserva
        assertTrue(info.titulo.startsWith("Crankshaft Position")) // título en inglés de OBDex
        assertTrue(info.notaMotor!! in info.ajenos) // la nota en español queda para traducir
        val informe = Informe.texto(Escaneo.unoSolo(dtc), repo)
        assertTrue("Check engine light" in informe || "Codes found: 1" in informe)
    }
}

private object Escaneo {
    fun unoSolo(dtc: ar.garibaldi.obd.dtc.Dtc) = ar.garibaldi.obd.scan.ResultadoEscaneo(
        0L, null, null, null, emptyList(),
        listOf(ar.garibaldi.obd.scan.ResultadoModulo("mod-7E0", "Motor (ME 9.7)", Categoria.MOTOR, "Dirección 7E0 → 7E8 · KWP", listOf(dtc), null)),
    )
}

class NovedadesTest {
    @Test fun leeElNewsDelRepo() {
        val mensajes = ar.garibaldi.obd.update.Novedades.parsear(File("../news.json").readText())!!
        assertTrue(mensajes.size >= 2)
        assertTrue(mensajes.all { it.id.isNotBlank() && it.titulo.isNotBlank() && it.cuerpo.isNotBlank() })
        assertTrue(mensajes.all { it.tituloEn != null && it.cuerpoEn != null }) // todos traen versión en inglés
        assertNull(mensajes.first().link)
    }

    @Test fun changelogSoloParaVersionesInstaladas() {
        val n = ar.garibaldi.obd.update.Novedades
        assertTrue(n.esParaLaVersionInstalada("v0.01.0", "0.01.0"))
        assertTrue(n.esParaLaVersionInstalada("v0.01.0", "0.02.0"))
        assertFalse(n.esParaLaVersionInstalada("v0.03.0", "0.02.0"))
        assertTrue(n.esParaLaVersionInstalada("2026-11-11-aviso", "0.01.0")) // los avisos siempre
    }
}

class MarcasTest {
    @Test
    fun cadaMarcaTraeSuModeloDeFabricaEnLaLista() {
        for (m in ar.garibaldi.obd.ui.Marcas.lista) {
            assertTrue("${m.nombre}: ${m.modelo}", m.modelo in m.modelos)
            assertTrue(m.nombre, m.modelos.size > 3 && m.modelos.size == m.modelos.distinct().size)
            assertTrue(m.nombre, m.modelos.all { it.length <= 14 })
        }
    }

    @Test
    fun otraMarcaNoUsaElMapaDeMercedes() {
        try {
            Auto.marca = "Ford"; Auto.modelo = "FOCUS"
            val rapidos = CatalogoModulos.candidatosRapidos()
            assertTrue(0x7E0 in rapidos)
            assertFalse(0x612 in rapidos) // esquema de Daimler, solo para el Mercedes
            assertEquals("Motor (probable)", CatalogoModulos.nuevo(0x7E0, 0x7E8).nombre)
            assertEquals("Ford FOCUS", Auto.descripcion)
            assertEquals("Código propio de Ford", Auto.let { "Código propio de ${it.marcaCorta}" })
        } finally {
            Auto.marca = Auto.MERCEDES; Auto.modelo = "C350"
        }
        assertTrue(0x612 in CatalogoModulos.candidatosRapidos())
        assertEquals("Mercedes-Benz C350 (W204)", Auto.descripcion)
    }
}

class ListasMercedesTest {
    private val repo = DtcRepositorio { nombre -> File("src/main/assets/dtc", nombre).takeIf { it.exists() }?.inputStream() }

    private fun carroceria(hex: String) = DtcDecoder.kwp(hexABytes("58 01 $hex 20"), trenMotriz = false).single()

    @Test
    fun codigoDeEspConModuloConocido() {
        val info = repo.buscar(carroceria("50 01"), Categoria.FRENOS, "Frenos / ESP")
        assertEquals(FuenteInfo.MERCEDES, info.fuente)
        assertEquals(1, info.mercedes.size)
        assertTrue(info.titulo.contains("N47-5"))
    }

    @Test
    fun moduloDesconocidoMuestraVariosSignificados() {
        val info = repo.buscar(carroceria("90 01"), Categoria.DESCONOCIDO, "Módulo 6A2")
        assertTrue(info.mercedes.size > 1) // 9001 existe en airbag, techo y otros
        assertTrue(info.mercedes.any { it.modulo == "Airbag (SRS)" })
    }

    @Test
    fun motorSumaElTextoDeMercedesALaFichaGenerica() {
        val p0014 = DtcDecoder.kwp(hexABytes("58 01 00 14 20"), trenMotriz = true).single()
        val info = repo.buscar(p0014, Categoria.MOTOR, "Motor (ME 9.7)")
        assertTrue(info.fuente == FuenteInfo.CURADA || info.fuente == FuenteInfo.OBDEX)
        assertTrue(info.mercedes.single().texto.contains("camshaft", ignoreCase = true))
    }

    @Test
    fun otrasMarcasNoUsanLasListasDeMercedes() {
        try {
            Auto.marca = "Toyota"
            val info = repo.buscar(carroceria("50 01"), Categoria.FRENOS, "Frenos / ABS / ESP")
            assertEquals(FuenteInfo.NINGUNA, info.fuente)
            assertTrue(info.mercedes.isEmpty())
            assertEquals("Código propio de Toyota", info.titulo)
        } finally {
            Auto.marca = Auto.MERCEDES
        }
    }
}
