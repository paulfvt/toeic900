package fr.toeic900.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.unit.dp

/**
 * Icônes au trait de la direction « Élan » (trait 2,4, bouts ronds), reprises du sprite SVG
 * de design-system/concept.html. Elles se teintent avec le paramètre `tint` de Icon().
 */
object ElanIcons {
    private fun strokeIcon(name: String, vararg paths: String): ImageVector =
        ImageVector.Builder(name = name, defaultWidth = 24.dp, defaultHeight = 24.dp, viewportWidth = 24f, viewportHeight = 24f)
            .apply {
                paths.forEach { data ->
                    addPath(
                        pathData = addPathNodes(data),
                        stroke = SolidColor(Color.Black),
                        strokeLineWidth = 2.4f,
                        strokeLineCap = StrokeCap.Round,
                        strokeLineJoin = StrokeJoin.Round,
                    )
                }
            }
            .build()

    // Un cercle SVG de centre (12,12) et de rayon r, écrit comme deux arcs.
    private fun circle(r: Int) = "M${12 - r} 12a$r $r 0 1 0 ${2 * r} 0a$r $r 0 1 0 ${-2 * r} 0z"

    val Close by lazy { strokeIcon("close", "M18 6L6 18M6 6l12 12") }
    val Back by lazy { strokeIcon("back", "M19 12H5M12 19l-7-7 7-7") }
    val Check by lazy { strokeIcon("check", "M20 6L9 17l-5-5") }
    val XCircle by lazy { strokeIcon("x-circle", circle(10), "M15 9l-6 6M9 9l6 6") }
    val CheckCircle by lazy { strokeIcon("check-circle", circle(10), "M16.5 9.5l-5.5 5.5-3-3") }
    val Settings by lazy { strokeIcon("settings", "M4 21v-7M4 10V3M12 21v-9M12 8V3M20 21v-5M20 12V3M1 14h6M9 8h6M17 16h6") }
    val Bulb by lazy {
        strokeIcon(
            "bulb",
            "M9 18h6M10 22h4",
            "M15.09 14c.18-.98.65-1.74 1.41-2.5A4.65 4.65 0 0 0 18 8 6 6 0 0 0 6 8c0 1 .23 2.23 1.5 3.5A4.61 4.61 0 0 1 8.91 14",
        )
    }
    val Refresh by lazy { strokeIcon("refresh", "M23 4v6h-6", "M20.49 15a9 9 0 1 1-2.12-9.36L23 10") }
    val Clock by lazy { strokeIcon("clock", circle(10), "M12 6v6l4 2") }
    val Target by lazy { strokeIcon("target", circle(10), circle(6), circle(2)) }
    val Bolt by lazy { strokeIcon("bolt", "M13 2L3 14h9l-1 8 10-12h-9l1-8z") }
    val ChevronRight by lazy { strokeIcon("chevron-right", "M9 6l6 6-6 6") }
    val ChevronUp by lazy { strokeIcon("chevron-up", "M6 15l6-6 6 6") }
    val ChevronDown by lazy { strokeIcon("chevron-down", "M6 9l6 6 6-6") }
    val Home by lazy { strokeIcon("home", "M3 9l9-7 9 7v11a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2z", "M9 22V12h6v10") }
    val Map by lazy { strokeIcon("map", "M1 6v16l7-4 8 4 7-4V2l-7 4-8-4-7 4z", "M8 2v16M16 6v16") }
    val FileText by lazy {
        strokeIcon("file-text", "M14 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V8z", "M14 2v6h6M16 13H8M16 17H8M10 9H8")
    }
    val Lock by lazy { strokeIcon("lock", "M5 11h14a2 2 0 0 1 2 2v7a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2v-7a2 2 0 0 1 2-2z", "M7 11V7a5 5 0 0 1 10 0v4") }
    val Play by lazy { strokeIcon("play", "M6 4l13 8-13 8z") }
    val Award by lazy { strokeIcon("award", "M5 8a7 7 0 1 0 14 0a7 7 0 1 0 -14 0z", "M8.21 13.89L7 23l5-3 5 3-1.21-9.12") }
}
