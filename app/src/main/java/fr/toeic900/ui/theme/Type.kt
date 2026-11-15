package fr.toeic900.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import fr.toeic900.R

// Nunito et Nunito Sans sont des polices « variables » : un seul fichier contient toutes
// les graisses. On déclare ici celles dont le thème a besoin.
@OptIn(ExperimentalTextApi::class)
private fun variableFamily(resId: Int, vararg weights: Int) = FontFamily(
    weights.map { w ->
        Font(
            resId = resId,
            weight = FontWeight(w),
            variationSettings = FontVariation.Settings(FontVariation.weight(w)),
        )
    },
)

/** Interface : titres, boutons, étiquettes. */
val Nunito = variableFamily(R.font.nunito_variable, 500, 700, 800, 900)

/** Textes d'examen : énoncés et options, plus sobres pour la lecture. */
val NunitoSans = variableFamily(R.font.nunito_sans_variable, 400, 700)

/** Échelle typographique « Élan » (classes .t-* de styles.css). */
@Immutable
data class ElanTypography(
    val display: TextStyle = TextStyle(fontFamily = Nunito, fontWeight = FontWeight.W900, fontSize = 64.sp, lineHeight = 64.sp, letterSpacing = (-0.01).em),
    val headline: TextStyle = TextStyle(fontFamily = Nunito, fontWeight = FontWeight.W800, fontSize = 24.sp, lineHeight = 29.sp, letterSpacing = (-0.01).em),
    val titleL: TextStyle = TextStyle(fontFamily = Nunito, fontWeight = FontWeight.W800, fontSize = 20.sp, lineHeight = 25.sp),
    val titleM: TextStyle = TextStyle(fontFamily = Nunito, fontWeight = FontWeight.W700, fontSize = 16.sp, lineHeight = 21.sp),
    val bodyL: TextStyle = TextStyle(fontFamily = Nunito, fontWeight = FontWeight.W500, fontSize = 16.sp, lineHeight = 24.sp),
    val bodyM: TextStyle = TextStyle(fontFamily = Nunito, fontWeight = FontWeight.W500, fontSize = 14.sp, lineHeight = 20.sp),
    val labelL: TextStyle = TextStyle(fontFamily = Nunito, fontWeight = FontWeight.W800, fontSize = 14.sp, lineHeight = 18.sp),
    /** Toujours affichée en capitales (voir [fr.toeic900.ui.components.LabelSmall]). */
    val labelS: TextStyle = TextStyle(fontFamily = Nunito, fontWeight = FontWeight.W800, fontSize = 11.sp, lineHeight = 14.sp, letterSpacing = 0.06.em),
    /** Toujours affichée en capitales. */
    val button: TextStyle = TextStyle(fontFamily = Nunito, fontWeight = FontWeight.W800, fontSize = 15.sp, lineHeight = 15.sp, letterSpacing = 0.04.em),
    val chip: TextStyle = TextStyle(fontFamily = Nunito, fontWeight = FontWeight.W800, fontSize = 13.sp, lineHeight = 16.sp),
    val reading: TextStyle = TextStyle(fontFamily = NunitoSans, fontWeight = FontWeight.W400, fontSize = 18.sp, lineHeight = 27.sp),
    val option: TextStyle = TextStyle(fontFamily = NunitoSans, fontWeight = FontWeight.W400, fontSize = 18.sp, lineHeight = 23.sp),
    /** Documents longs des Parts 6 et 7 : un peu plus petit que [reading], pour voir plus de texte à la fois. */
    val document: TextStyle = TextStyle(fontFamily = NunitoSans, fontWeight = FontWeight.W400, fontSize = 16.sp, lineHeight = 25.sp),
    /** Options de réponse sous un document : plus compactes que [option]. */
    val optionCompact: TextStyle = TextStyle(fontFamily = NunitoSans, fontWeight = FontWeight.W400, fontSize = 16.sp, lineHeight = 21.sp),
)

val LocalElanTypography = staticCompositionLocalOf { ElanTypography() }

fun ElanTypography.toMaterialTypography() = Typography(
    displayLarge = display,
    headlineSmall = headline,
    headlineMedium = headline,
    titleLarge = titleL,
    titleMedium = titleM,
    bodyLarge = bodyL,
    bodyMedium = bodyM,
    labelLarge = labelL,
    labelSmall = labelS,
)
