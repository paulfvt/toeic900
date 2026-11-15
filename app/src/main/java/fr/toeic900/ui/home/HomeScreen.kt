package fr.toeic900.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import fr.toeic900.data.HomeStats
import fr.toeic900.data.PathOverview
import fr.toeic900.data.TrainingRepository
import fr.toeic900.domain.StepKind
import fr.toeic900.ui.appContainer
import fr.toeic900.ui.components.ButtonStyle
import fr.toeic900.ui.components.CardStyle
import fr.toeic900.ui.components.ElanButton
import fr.toeic900.ui.components.ElanCard
import fr.toeic900.ui.components.ElanIconButton
import fr.toeic900.ui.components.ElanProgress
import fr.toeic900.ui.components.ElanTopBar
import fr.toeic900.ui.components.LabelSmall
import fr.toeic900.ui.theme.ElanDimens
import fr.toeic900.ui.theme.ElanIcons
import fr.toeic900.ui.theme.ElanTheme
import kotlin.math.roundToInt
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn

class HomeViewModel(repository: TrainingRepository) : ViewModel() {
    val stats: StateFlow<HomeStats> = repository.observeHome()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeStats())

    /** null tant que le chemin n'est pas chargé. */
    val path: StateFlow<PathOverview?> = repository.observePath()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
}

@Composable
fun HomeScreen(
    onQuickStart: () -> Unit,
    onOpenStep: (stepId: String) -> Unit,
    onPath: () -> Unit,
    onLessons: () -> Unit,
    onSettings: () -> Unit,
) {
    val container = appContainer()
    val viewModel = viewModel { HomeViewModel(container.repository) }
    val stats by viewModel.stats.collectAsStateWithLifecycle()
    val path by viewModel.path.collectAsStateWithLifecycle()
    val c = ElanTheme.colors
    val type = ElanTheme.type

    Column(Modifier.fillMaxSize()) {
        ElanTopBar(title = "TOEIC 900") {
            ElanIconButton(ElanIcons.Settings, "Réglages", onSettings)
        }
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = ElanDimens.gutter)
                .padding(bottom = ElanDimens.gapMajor),
            verticalArrangement = Arrangement.spacedBy(ElanDimens.gapSection),
        ) {
            path?.let { PathCard(it, onOpenStep, onPath) }

            ElanCard {
                LabelSmall("Révision du jour")
                Spacer(Modifier.height(4.dp))
                HomeLine(ElanIcons.Refresh, "Compétences à réviser", stats.dueSkills.toString(), c.onSurface)
                HomeLine(ElanIcons.Target, "Compétences à découvrir", stats.newSkills.toString(), c.onSurface)
                Spacer(Modifier.height(8.dp))
                ElanButton("Réviser · 15 min", onQuickStart, Modifier.fillMaxWidth(), style = ButtonStyle.Outlined)
            }

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                ElanCard(Modifier.weight(1f)) {
                    LabelSmall("Aujourd'hui")
                    Spacer(Modifier.height(4.dp))
                    Text(stats.answeredToday.toString(), style = type.headline, color = c.onSurface)
                    Text(plural(stats.answeredToday, "question", "questions"), style = type.bodyM, color = c.muted)
                }
                ElanCard(Modifier.weight(1f)) {
                    LabelSmall("Depuis le début")
                    Spacer(Modifier.height(4.dp))
                    Text(stats.totalAnswered.toString(), style = type.headline, color = c.onSurface)
                    val detail = if (stats.totalAnswered == 0) {
                        "questions"
                    } else {
                        "${(100f * stats.totalCorrect / stats.totalAnswered).roundToInt()} % de réussite"
                    }
                    Text(detail, style = type.bodyM, color = c.muted)
                }
            }

            ElanCard(onClick = onLessons) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Icon(ElanIcons.FileText, contentDescription = null, tint = c.primaryInk, modifier = Modifier.size(24.dp))
                    Column(Modifier.weight(1f)) {
                        Text("Fiches de cours", style = type.titleM, color = c.onSurface)
                        Text("Règles, exemples et pièges du TOEIC", style = type.bodyM, color = c.muted)
                    }
                    Icon(ElanIcons.ChevronRight, contentDescription = null, tint = c.muted, modifier = Modifier.size(20.dp))
                }
            }
        }
    }
}

/** La prochaine étape du chemin de leçons, avec l'avancement global. */
@Composable
private fun PathCard(path: PathOverview, onOpenStep: (String) -> Unit, onPath: () -> Unit) {
    val c = ElanTheme.colors
    val type = ElanTheme.type
    if (path.totalCount == 0) return
    val next = path.next
    ElanCard(style = CardStyle.Filled) {
        val unit = next?.let { path.unitOf(it.step.id) }
        LabelSmall(if (unit != null) "Chemin · ${unit.titleFr}" else "Chemin de leçons", color = c.onPrimaryContainer)
        Spacer(Modifier.height(8.dp))
        Text(
            when {
                next == null -> "Chemin terminé"
                next.step.kind == StepKind.REVIEW -> next.titleFr
                else -> next.titleFr
            },
            style = type.headline,
            color = c.onPrimaryContainer,
        )
        Spacer(Modifier.height(12.dp))
        ElanProgress(path.doneCount.toFloat() / path.totalCount, Modifier.fillMaxWidth(), description = "Avancement du chemin")
        Spacer(Modifier.height(8.dp))
        Text("${path.doneCount} ${plural(path.doneCount, "étape terminée", "étapes terminées")} sur ${path.totalCount}", style = type.bodyM, color = c.onPrimaryContainer)
        Spacer(Modifier.height(12.dp))
        if (next != null) {
            ElanButton(if (path.doneCount == 0) "Commencer le chemin" else "Continuer le chemin", { onOpenStep(next.step.id) }, Modifier.fillMaxWidth())
        } else {
            ElanButton("Revoir le chemin", onPath, Modifier.fillMaxWidth())
        }
    }
}

@Composable
private fun HomeLine(icon: ImageVector, label: String, value: String, ink: Color) {
    Row(
        modifier = Modifier.fillMaxWidth().height(40.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Icon(icon, contentDescription = null, tint = ink, modifier = Modifier.size(18.dp))
        Text(label, style = ElanTheme.type.bodyL, color = ink, modifier = Modifier.weight(1f))
        Text(value, style = ElanTheme.type.labelL, color = ink)
    }
}

fun plural(count: Int, singular: String, plural: String) = if (count < 2) singular else plural
