package fr.toeic900.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.unit.dp

/** Formes et mesures « Élan » : grands arrondis, bordure de 2 dp, tranche de 4 dp sous ce qui se presse. */
object ElanShapes {
    val xs = RoundedCornerShape(6.dp)
    val sm = RoundedCornerShape(10.dp)
    val md = RoundedCornerShape(14.dp)
    val lg = RoundedCornerShape(18.dp)
    val xl = RoundedCornerShape(22.dp)
    val full = RoundedCornerShape(percent = 50)

    val button = lg
    val option = lg
    val card = xl
    val chip = full
    val sheet = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
}

object ElanDimens {
    val border = 2.dp
    val edge = 4.dp
    val controlHeight = 52.dp
    val touchTarget = 48.dp
    val gutter = 20.dp
    val gapRelated = 8.dp
    val gapSection = 20.dp
    val gapMajor = 32.dp
    val progressHeight = 14.dp
    val appBarHeight = 64.dp
}

/** Accès au thème depuis n'importe quel composable : ElanTheme.colors, ElanTheme.type. */
object ElanTheme {
    val colors: ElanColors
        @Composable @ReadOnlyComposable get() = LocalElanColors.current
    val type: ElanTypography
        @Composable @ReadOnlyComposable get() = LocalElanTypography.current
}

@Composable
fun ElanTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val colors = if (darkTheme) DarkElanColors else LightElanColors
    val type = ElanTypography()
    CompositionLocalProvider(
        LocalElanColors provides colors,
        LocalElanTypography provides type,
    ) {
        MaterialTheme(
            colorScheme = colors.toMaterialScheme(),
            typography = type.toMaterialTypography(),
            shapes = Shapes(
                extraSmall = ElanShapes.xs,
                small = ElanShapes.sm,
                medium = ElanShapes.md,
                large = ElanShapes.lg,
                extraLarge = ElanShapes.xl,
            ),
            content = content,
        )
    }
}
