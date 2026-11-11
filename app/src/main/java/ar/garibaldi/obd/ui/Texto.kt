package ar.garibaldi.obd.ui

import androidx.compose.material3.LocalTextStyle
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import ar.garibaldi.obd.i18n.Idioma

/**
 * Reemplazo de `material3.Text` para todas las pantallas: traduce el texto al idioma elegido
 * antes de dibujarlo. Como lee [Idioma.ingles], al cambiar de idioma se redibuja solo.
 * Para textos que no hay que traducir (registro crudo, códigos) usar `androidx.compose.material3.Text`.
 */
@Composable
fun Text(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = Color.Unspecified,
    textAlign: TextAlign? = null,
    overflow: TextOverflow = TextOverflow.Clip,
    softWrap: Boolean = true,
    maxLines: Int = Int.MAX_VALUE,
    style: TextStyle = LocalTextStyle.current,
) {
    val traducido = if (Idioma.ingles) Idioma.L(text) else text
    androidx.compose.material3.Text(
        traducido,
        modifier = modifier,
        color = color,
        textAlign = textAlign,
        overflow = overflow,
        softWrap = softWrap,
        maxLines = maxLines,
        style = style,
    )
}
