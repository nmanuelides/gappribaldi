package ar.garibaldi.obd.ui

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import ar.garibaldi.obd.dtc.TipoFalla

/**
 * Prueba de luces: lee un solo módulo cada pocos segundos y marca qué código cambia
 * mientras el usuario desenchufa o cambia lámparas. Así se sabe qué luz es cada código.
 */
@Composable
fun PantallaPruebaLuces(vm: AppViewModel) {
    val p = vm.prueba
    var nombrando by remember { mutableStateOf<String?>(null) } // código al que se le pone nombre
    var confirmarBorrado by remember { mutableStateOf(false) }
    if (p == null || !vm.conectado) {
        Lista { Suave("Primero conectate al adaptador.") }
        return
    }
    val marca = LocalMarca.current

    Lista {
        Tarjeta {
            Text(p.nombre, style = MaterialTheme.typography.titleMedium)
            Suave("1. Contacto puesto y la luz que querés probar prendida (giro, freno, posición, patente…).")
            Suave("2. Desenchufá esa luz si anda, o poné una lámpara buena donde había una quemada.")
            Suave("3. En unos segundos el código de esa luz cambia acá y queda marcado. Tocalo para ponerle nombre.")
            if (p.lecturas == 0) FilaCargando("Leyendo el módulo…")
            else Suave("Leído ${p.lecturas} veces · se vuelve a leer cada 1,5 segundos")
            p.error?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = Tinta.Amarillo) }
        }

        if (p.lecturas > 0 && p.codigos.isEmpty() && p.error == null) {
            Tarjeta {
                Text("El módulo no tiene códigos ahora.", style = MaterialTheme.typography.bodyMedium, color = Tinta.Verde)
                Suave("Si desenchufás una luz que anda, su código debería aparecer acá.")
            }
        }

        // El último código que cambió queda resaltado unos segundos
        val reciente = p.codigos.filter { it.cambio > 0 }.maxByOrNull { it.cambio }
            ?.takeIf { p.ultima - it.cambio < 10_000 }
        for (c in p.codigos) {
            val codigo = c.dtc.codigo
            val destacado = c == reciente
            val forma = RoundedCornerShape(12.dp)
            Row(
                Modifier.fillMaxWidth().clip(forma)
                    .clickable { nombrando = codigo }
                    .drawBehind { drawRect(Tinta.TarjetaAlta) }
                    .border(if (destacado) 2.dp else 0.dp, if (destacado) marca.claro else Color.Transparent, forma)
                    .padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Punto(if (c.presente) Tinta.Rojo else Tinta.Verde, 12)
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        androidx.compose.material3.Text(codigo, style = MaterialTheme.typography.titleLarge, color = Tinta.Texto)
                        when {
                            destacado && c.aparecio && c.presente -> Etiqueta("APARECIÓ", marca.claro)
                            destacado -> Etiqueta("CAMBIÓ", marca.claro)
                        }
                    }
                    val nombre = vm.nombreLuz(p.idPedido, codigo)
                    if (nombre != null) Text("Luz: $nombre", style = MaterialTheme.typography.bodyMedium, color = marca.claro)
                    else Suave("Tocá para ponerle nombre")
                    TipoFalla.texto(c.dtc.tipoFalla)?.let { Suave("Tipo de falla: $it") }
                    // Mismo comienzo, otra terminación: es la misma luz con otro tipo de falla
                    p.codigos.map { it.dtc.codigo }.filter { it != codigo && baseDeLuz(it) == baseDeLuz(codigo) }
                        .takeIf { it.isNotEmpty() }?.let { Text("Misma luz que ${it.joinToString()}", style = MaterialTheme.typography.bodySmall, color = marca.claro) }
                    Text(
                        if (c.presente) "Presente ahora: esa luz está fallando" else "No está fallando ahora",
                        style = MaterialTheme.typography.bodySmall,
                        color = if (c.presente) Tinta.Rojo else Tinta.Verde,
                    )
                }
            }
        }

        if (p.codigos.isNotEmpty()) {
            BotonOscuro("Borrar y ver cuáles vuelven", Modifier.fillMaxWidth(), colorTexto = Tinta.Rojo) { confirmarBorrado = true }
        }
        Suave(
            "Una luz que anda deja el código guardado pero no presente. Si después de borrar un código vuelve " +
                "como presente, esa luz sigue fallando."
        )
    }

    nombrando?.let { codigo ->
        var texto by remember(codigo) { mutableStateOf(vm.nombreLuz(p.idPedido, codigo) ?: "") }
        Dialogo(
            titulo = "¿Qué luz es $codigo?",
            texto = "Ponele el nombre de la luz que desenchufaste o cambiaste. Queda guardado y se muestra en cada escaneo.",
            confirmar = "Guardar",
            alConfirmar = { vm.nombrarLuz(p.idPedido, codigo, texto); nombrando = null },
            alCancelar = { nombrando = null },
        ) {
            Campo(texto, { texto = it }, "Nombre de la luz")
            Suave("Por ejemplo: giro trasero izquierdo, freno central, patente. Dejalo vacío para sacarle el nombre.")
        }
    }

    if (confirmarBorrado) {
        Dialogo(
            titulo = "¿Borrar los códigos de ${p.nombre}?",
            texto = "Se borran los códigos de este módulo y se sigue leyendo. Los que vuelvan solos son de luces que siguen fallando; " +
                "las que andan bien no vuelven.",
            confirmar = "Sí, borrar",
            alConfirmar = { confirmarBorrado = false; vm.borrarEnPrueba() },
            alCancelar = { confirmarBorrado = false },
        )
    }
}
