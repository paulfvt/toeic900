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
import fr.toeic900.data.SessionSummary
import fr.toeic900.data.SkillResult
import fr.toeic900.data.TrainingRepository
import fr.toeic900.ui.appContainer
import fr.toeic900.ui.components.ButtonStyle
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

@Composable
fun SessionSummaryScreen(sessionId: Long, onNewSession: () -> Unit, onHome: () -> Unit) {
    val container = appContainer()
    val viewModel = viewModel { SummaryViewModel(container.repository, sessionId) }
    val summary by viewModel.summary.collectAsStateWithLifecycle()
    val c = ElanTheme.colors
    val type = ElanTheme.type
    BackHandler(onBack = onHome)

    Column(Modifier.fillMaxSize()) {
        ElanTopBar(title = "Session terminée")
        val s = summary
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = ElanDimens.gutter),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (s != null) {
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
            ElanButton("Nouvelle session", onNewSession, Modifier.fillMaxWidth())
            ElanButton("Retour à l'accueil", onHome, Modifier.fillMaxWidth(), style = ButtonStyle.Outlined)
        }
    }
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
