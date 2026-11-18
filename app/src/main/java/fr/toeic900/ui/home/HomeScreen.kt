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
import fr.toeic900.data.ProgressOverview
import fr.toeic900.data.ProgressRepository
import fr.toeic900.domain.GoalType
import fr.toeic900.ui.progress.StreakLine
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

class HomeViewModel(repository: TrainingRepository, progress: ProgressRepository) : ViewModel() {
    /** null tant que les chiffres de progression ne sont pas calculés. */
    val progress: StateFlow<ProgressOverview?> = progress.observe()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

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
    onProgress: () -> Unit,
    onBadges: () -> Unit,
) {
    val container = appContainer()
    val viewModel = viewModel { HomeViewModel(container.repository, container.progress) }
    val stats by viewModel.stats.collectAsStateWithLifecycle()
    val progress by viewModel.progress.collectAsStateWithLifecycle()
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
            progress?.let { o ->
                if (o.unseenBadges.isNotEmpty()) NewBadgesCard(o, onBadges)
                GoalCard(o)
            }
            path?.let { PathCard(it, onOpenStep, onPath) }

            ElanCard {
                LabelSmall("Révision du jour")
                Spacer(Modifier.height(4.dp))
                HomeLine(ElanIcons.Refresh, "Compétences à réviser", stats.dueSkills.toString(), c.onSurface)
                HomeLine(ElanIcons.Target, "Compétences à découvrir", stats.newSkills.toString(), c.onSurface)
                Spacer(Modifier.height(8.dp))
                ElanButton("Réviser · 15 min", onQuickStart, Modifier.fillMaxWidth(), style = ButtonStyle.Outlined)
            }

            progress?.let { o -> ScoreMiniCard(o, stats, onProgress) }

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

/** Objectif du jour, série hebdomadaire et niveau : le résumé de la motivation. */
@Composable
private fun GoalCard(o: ProgressOverview) {
    val c = ElanTheme.colors
    val type = ElanTheme.type
    val unit = if (o.goal.type == GoalType.MINUTES) "min" else "questions"
    ElanCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            LabelSmall("Objectif du jour", Modifier.weight(1f))
            Text("Niveau ${o.level.number} · ${o.level.totalXp} XP", style = type.labelL, color = c.muted)
        }
        Spacer(Modifier.height(4.dp))
        Row(verticalAlignment = Alignment.Bottom) {
            Text("${minOf(o.todayProgress, 999)}", style = type.headline, color = c.onSurface)
            Text(" / ${o.goal.target} $unit", style = type.titleM, color = c.muted, modifier = Modifier.padding(bottom = 3.dp))
            Spacer(Modifier.weight(1f))
            if (o.goalMetToday) {
                Icon(ElanIcons.CheckCircle, contentDescription = null, tint = c.successInk, modifier = Modifier.size(20.dp))
                Text(" Atteint", style = type.labelL, color = c.successInk)
            }
        }
        Spacer(Modifier.height(8.dp))
        ElanProgress(o.todayProgress.toFloat() / o.goal.target, Modifier.fillMaxWidth(), description = "Objectif du jour")
        Spacer(Modifier.height(16.dp))
        StreakLine(o)
    }
}

@Composable
private fun NewBadgesCard(o: ProgressOverview, onBadges: () -> Unit) {
    val c = ElanTheme.colors
    ElanCard(style = CardStyle.Tonal, onClick = onBadges) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Icon(ElanIcons.Award, contentDescription = null, tint = c.accentInk, modifier = Modifier.size(28.dp))
            Column(Modifier.weight(1f)) {
                val n = o.unseenBadges.size
                Text(if (n == 1) "Nouveau badge !" else "$n nouveaux badges !", style = ElanTheme.type.titleM, color = c.onSurface)
                Text(o.unseenBadges.joinToString(" · ") { it.titleFr }, style = ElanTheme.type.bodyM, color = c.muted)
            }
            Icon(ElanIcons.ChevronRight, contentDescription = null, tint = c.muted, modifier = Modifier.size(20.dp))
        }
    }
}

/** Le score estimé en une ligne, et le total des réponses ; mène à l'onglet Progrès. */
@Composable
private fun ScoreMiniCard(o: ProgressOverview, stats: HomeStats, onProgress: () -> Unit) {
    val c = ElanTheme.colors
    val type = ElanTheme.type
    ElanCard(onClick = onProgress) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Icon(ElanIcons.Chart, contentDescription = null, tint = c.primaryInk, modifier = Modifier.size(24.dp))
            Column(Modifier.weight(1f)) {
                LabelSmall("Score Reading estimé")
                val r = o.reading
                Text(
                    if (r == null) "Pas encore d'estimation" else "≈ ${r.score} (${r.low} à ${r.high})${if (r.provisional) ", provisoire" else ""}",
                    style = type.titleM,
                    color = c.onSurface,
                )
                val detail = if (stats.totalAnswered == 0) {
                    "Aucune réponse pour l'instant"
                } else {
                    "${stats.totalAnswered} réponses · ${(100f * stats.totalCorrect / stats.totalAnswered).roundToInt()} % de réussite"
                }
                Text(detail, style = type.bodyM, color = c.muted)
            }
            Icon(ElanIcons.ChevronRight, contentDescription = null, tint = c.muted, modifier = Modifier.size(20.dp))
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
