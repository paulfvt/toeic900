package fr.toeic900.ui.components

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import fr.toeic900.ui.theme.ElanDimens
import fr.toeic900.ui.theme.ElanIcons
import fr.toeic900.ui.theme.ElanShapes
import fr.toeic900.ui.theme.ElanTheme

/**
 * Surface « à tranche » : une face posée sur une tranche pleine de 4 dp. Quand on appuie,
 * la face descend et recouvre la tranche, comme un bouton physique. C'est la signature
 * visuelle de la direction Élan (box-shadow 0 4px 0 dans styles.css).
 */
@Composable
fun EdgeSurface(
    faceColor: Color,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    enabled: Boolean = true,
    shape: Shape = ElanShapes.button,
    edgeColor: Color? = null,
    borderColor: Color = Color.Transparent,
    minHeight: Dp = ElanDimens.controlHeight,
    contentPadding: PaddingValues = PaddingValues(horizontal = 20.dp),
    contentAlignment: Alignment = Alignment.Center,
    content: @Composable BoxScope.() -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val edge = if (edgeColor != null) ElanDimens.edge else 0.dp
    val pressOffset by animateDpAsState(
        targetValue = if (pressed && enabled && onClick != null) edge else 0.dp,
        animationSpec = tween(120),
        label = "tranche",
    )
    Box(
        modifier = modifier
            .alpha(if (enabled) 1f else 0.4f)
            .then(
                if (onClick != null) {
                    Modifier.clickable(
                        interactionSource = interaction,
                        indication = null,
                        enabled = enabled,
                        role = Role.Button,
                        onClick = onClick,
                    )
                } else {
                    Modifier
                },
            ),
        propagateMinConstraints = true,
    ) {
        if (edgeColor != null) {
            Box(Modifier.matchParentSize().padding(top = edge).background(edgeColor, shape))
        }
        Box(
            modifier = Modifier
                .padding(bottom = edge)
                .offset { IntOffset(0, pressOffset.roundToPx()) }
                .defaultMinSize(minHeight = minHeight)
                .background(faceColor, shape)
                .border(ElanDimens.border, borderColor, shape)
                .padding(contentPadding),
            contentAlignment = contentAlignment,
            content = content,
        )
    }
}

enum class ButtonStyle { Filled, Tonal, Outlined, Text }

@Composable
fun ElanButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    style: ButtonStyle = ButtonStyle.Filled,
    enabled: Boolean = true,
) {
    val c = ElanTheme.colors
    val face: Color
    val ink: Color
    val edge: Color?
    val border: Color
    when (style) {
        ButtonStyle.Filled -> { face = c.primary; ink = c.onPrimary; edge = c.primaryStrong; border = Color.Transparent }
        ButtonStyle.Tonal -> { face = c.primaryContainer; ink = c.onPrimaryContainer; edge = null; border = Color.Transparent }
        ButtonStyle.Outlined -> { face = c.surface; ink = c.primaryInk; edge = c.outlineVariant; border = c.outline }
        ButtonStyle.Text -> { face = Color.Transparent; ink = c.primaryInk; edge = null; border = Color.Transparent }
    }
    EdgeSurface(
        faceColor = face,
        modifier = modifier,
        onClick = onClick,
        enabled = enabled,
        edgeColor = edge,
        borderColor = border,
        contentPadding = PaddingValues(horizontal = if (style == ButtonStyle.Text) 12.dp else 20.dp),
    ) {
        Text(text = text.uppercase(), style = ElanTheme.type.button, color = ink, textAlign = TextAlign.Center)
    }
}

enum class OptionState { Default, Selected, Correct, Wrong }

/** Une proposition de réponse : pastille A/B/C/D, texte, et coche ou croix une fois corrigée. */
@Composable
fun OptionRow(
    letter: String,
    text: String,
    state: OptionState,
    modifier: Modifier = Modifier,
    compact: Boolean = false,
    onClick: (() -> Unit)? = null,
) {
    val c = ElanTheme.colors
    val face: Color
    val ink: Color
    val line: Color
    val edge: Color
    when (state) {
        OptionState.Default -> { face = c.surface; ink = c.onSurface; line = c.outline; edge = c.outlineVariant }
        OptionState.Selected -> { face = c.primaryContainer; ink = c.onPrimaryContainer; line = c.primary; edge = c.primary }
        OptionState.Correct -> { face = c.successContainer; ink = c.onSuccessContainer; line = c.success; edge = c.success }
        OptionState.Wrong -> { face = c.errorContainer; ink = c.onErrorContainer; line = c.error; edge = c.error }
    }
    val plain = state == OptionState.Default
    EdgeSurface(
        faceColor = face,
        modifier = modifier.fillMaxWidth(),
        onClick = onClick,
        shape = ElanShapes.option,
        edgeColor = edge,
        borderColor = line,
        minHeight = if (compact) 44.dp else ElanDimens.controlHeight,
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = if (compact) 8.dp else 10.dp),
        contentAlignment = Alignment.CenterStart,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .border(ElanDimens.border, if (plain) c.outlineVariant else ink, ElanShapes.full),
                contentAlignment = Alignment.Center,
            ) {
                Text(letter, style = ElanTheme.type.chip, color = if (plain) c.muted else ink)
            }
            Text(text, style = if (compact) ElanTheme.type.optionCompact else ElanTheme.type.option, color = ink, modifier = Modifier.weight(1f))
            when (state) {
                OptionState.Correct -> Icon(ElanIcons.Check, contentDescription = null, tint = ink, modifier = Modifier.size(18.dp))
                OptionState.Wrong -> Icon(ElanIcons.Close, contentDescription = null, tint = ink, modifier = Modifier.size(18.dp))
                else -> Unit
            }
        }
    }
}

@Composable
fun ElanChip(
    text: String,
    modifier: Modifier = Modifier,
    selected: Boolean = false,
    tonal: Boolean = false,
    onClick: (() -> Unit)? = null,
    leading: (@Composable () -> Unit)? = null,
) {
    val c = ElanTheme.colors
    val bg = when {
        selected -> c.primaryContainer
        tonal -> c.surfaceContainerHigh
        else -> c.surface
    }
    val ink = if (selected) c.onPrimaryContainer else c.onSurface
    val border = if (selected || tonal) Color.Transparent else c.outlineVariant
    Row(
        modifier = modifier
            .defaultMinSize(minHeight = 32.dp)
            .clip(ElanShapes.chip)
            .background(bg)
            .border(ElanDimens.border, border, ElanShapes.chip)
            .then(if (onClick != null) Modifier.clickable(role = Role.Button, onClick = onClick) else Modifier)
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        leading?.invoke()
        Text(text, style = ElanTheme.type.chip, color = ink)
    }
}

enum class CardStyle { Outlined, Filled, Tonal }

@Composable
fun ElanCard(
    modifier: Modifier = Modifier,
    style: CardStyle = CardStyle.Outlined,
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val c = ElanTheme.colors
    val face = when (style) {
        CardStyle.Outlined -> c.surface
        CardStyle.Filled -> c.primaryContainer
        CardStyle.Tonal -> c.surfaceContainer
    }
    EdgeSurface(
        faceColor = face,
        modifier = modifier.fillMaxWidth(),
        onClick = onClick,
        shape = ElanShapes.card,
        edgeColor = if (style == CardStyle.Outlined) c.outlineVariant else null,
        borderColor = if (style == CardStyle.Outlined) c.outlineVariant else Color.Transparent,
        minHeight = 0.dp,
        contentPadding = PaddingValues(16.dp),
        contentAlignment = Alignment.TopStart,
    ) {
        Column(modifier = Modifier.fillMaxWidth(), content = content)
    }
}

@Composable
fun ElanProgress(fraction: Float, modifier: Modifier = Modifier, description: String? = null) {
    val c = ElanTheme.colors
    val animated by animateFloatAsState(fraction.coerceIn(0f, 1f), tween(240), label = "progression")
    Box(
        modifier = modifier
            .height(ElanDimens.progressHeight)
            .clip(ElanShapes.full)
            .background(c.surfaceContainerHigh)
            .then(if (description != null) Modifier.semantics { contentDescription = description } else Modifier),
    ) {
        Box(Modifier.fillMaxHeight().fillMaxWidth(animated).clip(ElanShapes.full).background(c.primary))
    }
}

/** Petite étiquette en capitales (classe .t-label-s). */
@Composable
fun LabelSmall(text: String, modifier: Modifier = Modifier, color: Color = ElanTheme.colors.muted) {
    Text(text.uppercase(), style = ElanTheme.type.labelS, color = color, modifier = modifier)
}

/** Encadré ambre « à retenir ». */
@Composable
fun Callout(label: String, text: String, modifier: Modifier = Modifier) {
    val c = ElanTheme.colors
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(ElanShapes.option)
            .background(c.accentContainer)
            .padding(12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Icon(ElanIcons.Bulb, contentDescription = null, tint = c.onAccentContainer, modifier = Modifier.size(18.dp))
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            LabelSmall(label, color = c.onAccentContainer)
            Text(text, style = ElanTheme.type.bodyM, color = c.onAccentContainer)
        }
    }
}

@Composable
fun ElanIconButton(icon: ImageVector, description: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .size(ElanDimens.touchTarget)
            .clip(ElanShapes.full)
            .clickable(role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = description, tint = ElanTheme.colors.onSurface, modifier = Modifier.size(24.dp))
    }
}

/** Barre du haut : bouton de navigation éventuel, titre, actions à droite. */
@Composable
fun ElanTopBar(
    title: String,
    modifier: Modifier = Modifier,
    navigationIcon: ImageVector? = null,
    navigationDescription: String = "",
    onNavigate: () -> Unit = {},
    actions: @Composable RowScope.() -> Unit = {},
) {
    Row(
        modifier = modifier.fillMaxWidth().height(ElanDimens.appBarHeight).padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (navigationIcon != null) {
            ElanIconButton(navigationIcon, navigationDescription, onNavigate)
            Spacer(Modifier.width(4.dp))
        } else {
            Spacer(Modifier.width(12.dp))
        }
        Text(title, style = ElanTheme.type.titleL, color = ElanTheme.colors.onSurface, modifier = Modifier.weight(1f))
        actions()
    }
}

/**
 * Texte avec le seul balisage des fiches de cours : **mot** met le mot en avant.
 * Un ** non refermé est affiché tel quel.
 */
fun markupText(text: String, emphasis: SpanStyle): AnnotatedString = buildAnnotatedString {
    val parts = text.split("**")
    if (parts.size % 2 == 0) {
        append(text)
        return@buildAnnotatedString
    }
    parts.forEachIndexed { index, part ->
        if (index % 2 == 1) withStyle(emphasis) { append(part) } else append(part)
    }
}

@Composable
fun MarkupText(text: String, style: TextStyle, color: Color, modifier: Modifier = Modifier, emphasisColor: Color = ElanTheme.colors.primaryInk) {
    Text(
        text = markupText(text, SpanStyle(color = emphasisColor, fontWeight = FontWeight.W800)),
        style = style,
        color = color,
        modifier = modifier,
    )
}

/** Un onglet de la barre du bas. */
data class BottomTab(val label: String, val icon: ImageVector)

/** Barre de navigation du bas (classe .navbar) : un indicateur en pastille sous l'onglet actif. */
@Composable
fun ElanBottomBar(tabs: List<BottomTab>, selectedIndex: Int, onSelect: (Int) -> Unit, modifier: Modifier = Modifier) {
    val c = ElanTheme.colors
    Column(modifier.fillMaxWidth().background(c.surface)) {
        Box(Modifier.fillMaxWidth().height(ElanDimens.border).background(c.outlineVariant))
        Row(Modifier.fillMaxWidth().height(ElanDimens.navBarHeight)) {
            tabs.forEachIndexed { index, tab ->
                val selected = index == selectedIndex
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .clickable(role = Role.Tab, onClick = { onSelect(index) })
                        .semantics { this.selected = selected },
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(4.dp, Alignment.CenterVertically),
                ) {
                    Box(
                        modifier = Modifier
                            .size(width = 56.dp, height = 32.dp)
                            .background(if (selected) c.primaryContainer else Color.Transparent, ElanShapes.full),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(tab.icon, contentDescription = null, tint = if (selected) c.onPrimaryContainer else c.muted, modifier = Modifier.size(24.dp))
                    }
                    Text(tab.label, style = ElanTheme.type.labelS, color = if (selected) c.onSurface else c.muted)
                }
            }
        }
    }
}
