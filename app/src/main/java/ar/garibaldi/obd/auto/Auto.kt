package ar.garibaldi.obd.auto

/**
 * El auto elegido en Ajustes (marca y modelo). Lo leen las partes que no son de la interfaz:
 * el catálogo de módulos, la base de códigos, el informe y la explicación con IA.
 *
 * Solo el Mercedes-Benz C350 W204 tiene mapa de módulos, notas de motor y foto propios; el resto
 * de las marcas usa lo que es común a todos los autos (OBD-II) y la búsqueda de módulos genérica.
 */
object Auto {
    const val MERCEDES = "Mercedes-Benz"

    @Volatile var marca: String = MERCEDES
    @Volatile var modelo: String = "C350"

    val esMercedes: Boolean get() = marca == MERCEDES

    /** Cómo se nombra a la marca en un texto corrido ("código propio de Mercedes"). */
    val marcaCorta: String get() = if (esMercedes) "Mercedes" else marca

    /** El auto completo, para el informe y la IA. */
    val descripcion: String
        get() = if (esMercedes && modelo.equals("C350", ignoreCase = true)) "Mercedes-Benz C350 (W204)" else "$marca $modelo"
}
