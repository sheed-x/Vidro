package app.vidro.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.vidro.R

// Google Sans Flex (SIL Open Font License), the variable typeface of Pixel apps. Arabic text falls back
// to the system Arabic font automatically. Headlines use the rounded (ROND) axis for a softer look.
@OptIn(ExperimentalTextApi::class)
private fun flexFont(weight: Int, round: Float) = Font(
    R.font.google_sans_flex,
    weight = FontWeight(weight),
    variationSettings = FontVariation.Settings(
        FontVariation.weight(weight),
        FontVariation.Setting("ROND", round),
    ),
)

private val TextFamily = FontFamily(listOf(400, 500, 600, 700).map { flexFont(it, round = 0f) })
private val DisplayFamily = FontFamily(listOf(400, 500, 600, 700).map { flexFont(it, round = 100f) })

private fun TextStyle.text() = copy(fontFamily = TextFamily)
private fun TextStyle.display(weight: FontWeight = FontWeight.SemiBold) =
    copy(fontFamily = DisplayFamily, fontWeight = weight)

private val VidroTypography = Typography().let { b ->
    Typography(
        displayLarge = b.displayLarge.display(),
        displayMedium = b.displayMedium.display(),
        displaySmall = b.displaySmall.display(),
        headlineLarge = b.headlineLarge.display(),
        headlineMedium = b.headlineMedium.display(),
        headlineSmall = b.headlineSmall.display(),
        titleLarge = b.titleLarge.display().copy(fontSize = 22.sp),
        titleMedium = b.titleMedium.text().copy(fontWeight = FontWeight.SemiBold),
        titleSmall = b.titleSmall.text().copy(fontWeight = FontWeight.SemiBold),
        bodyLarge = b.bodyLarge.text(),
        bodyMedium = b.bodyMedium.text(),
        bodySmall = b.bodySmall.text(),
        labelLarge = b.labelLarge.text().copy(fontWeight = FontWeight.SemiBold),
        labelMedium = b.labelMedium.text().copy(fontWeight = FontWeight.SemiBold),
        labelSmall = b.labelSmall.text().copy(fontWeight = FontWeight.SemiBold),
    )
}

private val VidroShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(14.dp),
    medium = RoundedCornerShape(20.dp),
    large = RoundedCornerShape(28.dp),
    extraLarge = RoundedCornerShape(36.dp),
)

val LocalIsDark = staticCompositionLocalOf { false }

/** Light or dark follows the system setting; colors are the fixed brand palette (no dynamic color). */
@Composable
fun VidroTheme(darkTheme: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalIsDark provides darkTheme) {
        MaterialTheme(
            colorScheme = if (darkTheme) DarkColors else LightColors,
            typography = VidroTypography,
            shapes = VidroShapes,
            content = content,
        )
    }
}
