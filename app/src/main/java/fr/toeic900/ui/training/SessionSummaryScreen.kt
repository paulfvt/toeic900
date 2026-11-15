package fr.toeic900.ui.training

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import fr.toeic900.data.LessonOutcome
import fr.toeic900.data.SessionSummary
import fr.toeic900.data.SkillResult
import fr.toeic900.data.TrainingRepository
import fr.toeic900.domain.LessonPath
import fr.toeic900.domain.StepKind
import fr.toeic900.ui.appContainer
import fr.toeic900.ui.components.ButtonStyle
import fr.toeic900.ui.components.CardStyle
import fr.toeic900.ui.components.ElanButton
import fr.toeic900.ui.components.ElanCard
import fr.toeic900.ui.components.ElanTopBar
import fr.toeic900.ui.components.LabelSmall
import fr.toeic900.ui.theme.ElanDimens
import fr.toeic900.ui.theme.ElanTheme
import kotlin.math.roundToInt
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class SummaryViewModel(repository: TrainingRepository, sessionId: Long) : ViewModel() {
    private val _summary = MutableStateFlow<SessionSummary?>(null)
    val summary: StateFlow<SessionSummary?> = _summary

    init {
        viewModelScope.launch { _summary.value = repository.summary(sessionId) }
    }
}

/**
 * Bilan d'une session. Après un entraînement libre : nouvelle session ou accueil. Après une
 * étape du chemin : étape suivante si elle est réussie, sinon recommencer.
 */
@Composable
fun SessionSummaryScreen(
    sessionId: Long,
    onNewSession: () -> Unit,
    onHome: () -> Unit,
    onPath: () -> Unit,
    onOpenStep: (stepId: String) -> Unit,
    onRetryStep: (stepId: String) -> Unit,
) {
    val container = appContainer()
    val viewModel = viewModel { SummaryViewModel(container.repository, sessionId) }
    val summary by viewModel.summary.collectAsStateWithLifecycle()
    val c = ElanTheme.colors
    val type = ElanTheme.type
    val lesson = summary?.lesson
    BackHandler(onBack = if (lesson != null) onPath else onHome)

    Column(Modifier.fillMaxSize()) {
        ElanTopBar(title = if (lesson != null) lessonHeadline(lesson) else "Session terminée")
        val s = summary
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = ElanDimens.gutter),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (s != null) {
                if (lesson != null) {
                    ElanCard(style = if (lesson.passed) CardStyle.Filled else CardStyle.Tonal) {
                        val ink = if (lesson.passed) c.onPrimaryContainer else c.onSurface
                        LabelSmall(if (lesson.kind == StepKind.REVIEW) "Bilan d'unité" else "Leçon", color = ink)
                        Spacer(Modifier.height(4.dp))
                        Text(lesson.titleFr, style = type.titleL, color = ink)
                        Spacer(Modifier.height(4.dp))
                        Text(lessonMessage(lesson, s), style = type.bodyL, color = ink)
                    }
                }
                val rate = if (s.questionCount == 0) 0 else (100f * s.correctCount / s.questionCount).roundToInt()
                LabelSmall("Bonnes réponses")
                Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("${s.correctCount}", style = type.display, color = c.onBg)
                    Text("sur ${s.questionCount}", style = type.headline, color = c.muted, modifier = Modifier.padding(bottom = 8.dp))
                }
                Text("$rate % de réussite · ${formatDuration(s.durationMs)}", style = type.titleM, color = c.onBg)
                Spacer(Modifier.height(8.dp))
                LabelSmall("Par compétence")
                s.skills.forEach { SkillResultCard(it) }
                Spacer(Modifier.height(8.dp))
            }
        }
        Column(
            modifier = Modifier.padding(horizontal = ElanDimens.gutter).padding(top = 8.dp, bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            when {
                lesson == null -> {
                    ElanButton("Nouvelle session", onNewSession, Modifier.fillMaxWidth())
                    ElanButton("Retour à l'accueil", onHome, Modifier.fillMaxWidth(), style = ButtonStyle.Outlined)
                }
                lesson.passed -> {
                    val next = lesson.nextStepId
                    if (next != null) ElanButton("Étape suivante", { onOpenStep(next) }, Modifier.fillMaxWidth())
                    ElanButton("Retour au chemin", onPath, Modifier.fillMaxWidth(), style = if (next != null) ButtonStyle.Outlined else ButtonStyle.Filled)
                }
                else -> {
                    ElanButton("Recommencer", { onRetryStep(lesson.stepId) }, Modifier.fillMaxWidth())
                    ElanButton("Retour au chemin", onPath, Modifier.fillMaxWidth(), style = ButtonStyle.Outlined)
                }
            }
        }
    }
}

fun lessonHeadline(lesson: LessonOutcome): String {
    val name = if (lesson.kind == StepKind.REVIEW) "Bilan" else "Leçon"
    return when {
        lesson.passed -> if (lesson.kind == StepKind.REVIEW) "Bilan réussi" else "Leçon réussie"
        !lesson.finished -> "$name à terminer"
        else -> "$name à refaire"
    }
}

fun lessonMessage(lesson: LessonOutcome, summary: SessionSummary): String = when {
    lesson.passed && lesson.nextStepId != null -> "Étape validée : la suivante est débloquée."
    lesson.passed -> "Étape validée. Tu as terminé tout le chemin disponible."
    !lesson.finished -> "Tu as quitté avant la fin : l'étape n'est validée que si tu fais tous ses exercices."
    else -> "Il faut ${lesson.requiredCorrect} bonnes réponses sur ${summary.questionCount} " +
        "(${LessonPath.PASS_PERCENT} %) pour valider l'étape. Relis la fiche, puis recommence."
}

@Composable
private fun SkillResultCard(result: SkillResult) {
    val c = ElanTheme.colors
    ElanCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(result.labelFr, style = ElanTheme.type.titleM, color = c.onSurface, modifier = Modifier.weight(1f))
            Text(
                "${result.correct} / ${result.asked}",
                style = ElanTheme.type.labelL,
                color = if (result.correct == result.asked) c.successInk else c.onSurface,
            )
        }
        Spacer(Modifier.height(4.dp))
        Text(boxMovement(result), style = ElanTheme.type.bodyM, color = c.muted)
    }
}

fun boxMovement(result: SkillResult): String {
    val before = result.boxBefore
    return when {
        before == null -> "Nouvelle compétence · boîte ${result.boxAfter}"
        before == result.boxAfter -> "Reste en boîte ${result.boxAfter}"
        else -> "Boîte $before → boîte ${result.boxAfter}"
    }
}

fun formatDuration(ms: Long): String {
    val seconds = (ms / 1000).toInt()
    return if (seconds < 60) "$seconds s" else "${seconds / 60} min ${"%02d".format(seconds % 60)} s"
}
