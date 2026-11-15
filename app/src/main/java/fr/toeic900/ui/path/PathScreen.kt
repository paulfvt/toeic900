package fr.toeic900.ui.path

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import fr.toeic900.data.PathOverview
import fr.toeic900.data.PathStepView
import fr.toeic900.data.PathUnitView
import fr.toeic900.data.TrainingRepository
import fr.toeic900.domain.StepKind
import fr.toeic900.domain.StepStatus
import fr.toeic900.ui.appContainer
import fr.toeic900.ui.components.CardStyle
import fr.toeic900.ui.components.ElanCard
import fr.toeic900.ui.components.ElanChip
import fr.toeic900.ui.components.ElanProgress
import fr.toeic900.ui.components.ElanTopBar
import fr.toeic900.ui.components.LabelSmall
import fr.toeic900.ui.home.plural
import fr.toeic900.ui.theme.ElanDimens
import fr.toeic900.ui.theme.ElanIcons
import fr.toeic900.ui.theme.ElanShapes
import fr.toeic900.ui.theme.ElanTheme
import fr.toeic900.ui.training.unitCountLabel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn

class PathViewModel(repository: TrainingRepository) : ViewModel() {
    val path: StateFlow<PathOverview> = repository.observePath()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), PathOverview())
}

/** Décalage horizontal des étapes : le chemin serpente (0, 36, 72, 36 dp), comme sur la maquette. */
private val ZIGZAG = listOf(0.dp, 36.dp, 72.dp, 36.dp)

/** Onglet « Chemin » : les unités et leurs étapes, à faire l'une après l'autre. */
@Composable
fun PathScreen(onOpenStep: (stepId: String) -> Unit, onLessons: () -> Unit) {
    val container = appContainer()
    val viewModel = viewModel { PathViewModel(container.repository) }
    val path by viewModel.path.collectAsStateWithLifecycle()
    val nextId = path.next?.step?.id

    Column(Modifier.fillMaxSize()) {
        ElanTopBar(title = "Chemin") {
            ElanChip(
                text = "Fiches",
                onClick = onLessons,
                leading = { Icon(ElanIcons.FileText, contentDescription = null, tint = ElanTheme.colors.primaryInk, modifier = Modifier.size(16.dp)) },
                modifier = Modifier.padding(end = 12.dp),
            )
        }
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = ElanDimens.gutter, end = ElanDimens.gutter, bottom = ElanDimens.gapMajor),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            path.units.forEachIndexed { unitIndex, unit ->
                item(key = unit.id) {
                    UnitHeader(unit, number = unitIndex + 1, current = unit.steps.any { it.step.id == nextId })
                }
                itemsIndexed(unit.steps, key = { _, step -> step.step.id }) { index, step ->
                    StepRow(
                        view = step,
                        number = index + 1,
                        modifier = Modifier.padding(start = ZIGZAG[index % ZIGZAG.size]),
                        onClick = if (step.status == StepStatus.LOCKED) null else ({ onOpenStep(step.step.id) }),
                    )
                }
                item(key = "${unit.id}-space") { Spacer(Modifier.height(4.dp)) }
            }
        }
    }
}

@Composable
private fun UnitHeader(unit: PathUnitView, number: Int, current: Boolean) {
    val c = ElanTheme.colors
    val ink = if (current) c.onPrimaryContainer else c.onSurface
    ElanCard(style = if (current) CardStyle.Filled else CardStyle.Tonal) {
        LabelSmall("Unité $number", color = ink)
        Spacer(Modifier.height(4.dp))
        Text(unit.titleFr, style = ElanTheme.type.titleL, color = ink)
        Spacer(Modifier.height(8.dp))
        ElanProgress(unit.doneCount.toFloat() / unit.steps.size.coerceAtLeast(1), Modifier.fillMaxWidth(), description = "Avancement de l'unité")
        Spacer(Modifier.height(8.dp))
        Text(
            "${unit.doneCount} ${plural(unit.doneCount, "étape terminée", "étapes terminées")} sur ${unit.steps.size}",
            style = ElanTheme.type.bodyM,
            color = ink,
        )
    }
}

@Composable
private fun StepRow(view: PathStepView, number: Int, modifier: Modifier = Modifier, onClick: (() -> Unit)?) {
    val c = ElanTheme.colors
    val locked = view.status == StepStatus.LOCKED
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(ElanShapes.lg)
            .then(if (onClick != null) Modifier.clickable(role = Role.Button, onClick = onClick) else Modifier)
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        StepNode(view)
        Column(Modifier.weight(1f)) {
            Text(view.titleFr, style = ElanTheme.type.titleM, color = if (locked) c.muted else c.onBg)
            Text(stepSubtitle(view, number), style = ElanTheme.type.bodyM, color = c.muted)
        }
    }
}

/** Le rond d'une étape, posé sur sa tranche : coche (faite), lecture (à faire), cadenas (verrouillée). */
@Composable
private fun StepNode(view: PathStepView) {
    val c = ElanTheme.colors
    val review = view.step.kind == StepKind.REVIEW
    val (face, edge, ink) = when (view.status) {
        StepStatus.DONE -> Triple(c.successContainer, c.success, c.onSuccessContainer)
        StepStatus.AVAILABLE -> Triple(c.primary, c.primaryStrong, c.onPrimary)
        StepStatus.LOCKED -> Triple(c.surfaceContainerHigh, c.outlineVariant, c.subtle)
    }
    val icon = when {
        view.status == StepStatus.DONE -> ElanIcons.Check
        review -> ElanIcons.Award
        view.status == StepStatus.AVAILABLE -> ElanIcons.Play
        else -> ElanIcons.Lock
    }
    Box(Modifier.size(width = 60.dp, height = 60.dp + ElanDimens.edge)) {
        Box(Modifier.padding(top = ElanDimens.edge).size(60.dp).background(edge, CircleShape))
        Box(Modifier.size(60.dp).background(face, CircleShape), contentAlignment = Alignment.Center) {
            Icon(icon, contentDescription = null, tint = ink, modifier = Modifier.size(24.dp))
        }
    }
}

fun stepSubtitle(view: PathStepView, number: Int): String {
    val name = if (view.step.kind == StepKind.REVIEW) "Bilan" else "Leçon $number"
    val best = view.progress?.takeIf { it.bestTotal > 0 }?.let { " · meilleur score ${it.bestCorrect}/${it.bestTotal}" }.orEmpty()
    return when (view.status) {
        StepStatus.DONE -> "$name · terminée$best"
        StepStatus.AVAILABLE ->
            if (view.progress != null) "$name · à refaire$best" else "$name · ${unitCountLabel(view.step.part, view.step.exerciseCount)}"
        StepStatus.LOCKED -> "$name · verrouillée"
    }
}
