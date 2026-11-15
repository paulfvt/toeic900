package fr.toeic900.ui.training

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import fr.toeic900.data.TrainingRepository
import fr.toeic900.domain.Clock
import fr.toeic900.domain.Leitner
import fr.toeic900.domain.Skill
import fr.toeic900.domain.SystemClock
import fr.toeic900.ui.appContainer
import fr.toeic900.ui.components.EdgeSurface
import fr.toeic900.ui.components.ElanButton
import fr.toeic900.ui.components.ElanChip
import fr.toeic900.ui.components.ElanTopBar
import fr.toeic900.ui.components.LabelSmall
import fr.toeic900.ui.theme.ElanDimens
import fr.toeic900.ui.theme.ElanShapes
import fr.toeic900.ui.theme.ElanTheme
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Durées proposées, en minutes ; 0 = libre (la session dure jusqu'à ce qu'on l'arrête). */
val DURATIONS = listOf(5, 15, 30, 0)

fun durationLabel(minutes: Int) = if (minutes == 0) "Libre" else "$minutes min"

fun partLabel(part: Int) = if (part == TrainingRepository.ALL_PARTS) "Toutes" else "Part $part"

/** Ce que sert une part, en une ligne. */
fun partDescription(part: Int) = when (part) {
    5 -> "Phrases à compléter : une phrase, un trou, quatre propositions."
    6 -> "Textes à trous : un texte court, quatre trous, dont une phrase entière à insérer."
    7 -> "Compréhension de documents : un à trois documents, deux à cinq questions."
    else -> "Les trois parts du Reading mélangées, selon les révisions dues."
}

/** Le nom des exercices d'une part : « 3 questions », « 2 textes », « 4 documents ». */
fun unitCountLabel(part: Int, count: Int): String {
    val (one, many) = when (part) {
        5 -> "question" to "questions"
        6 -> "texte" to "textes"
        7 -> "jeu de documents" to "jeux de documents"
        else -> "exercice" to "exercices"
    }
    return "$count ${if (count < 2) one else many}"
}

data class SetupUiState(
    /** Les parts qui ont du contenu, précédées de « toutes ». */
    val parts: List<Int> = listOf(TrainingRepository.ALL_PARTS),
    val part: Int = TrainingRepository.ALL_PARTS,
    val skills: List<Skill> = emptyList(),
    /** null = toutes les compétences. */
    val selectedTag: String? = null,
    val minutes: Int = 15,
    val today: Long = 0,
)

class SetupViewModel(private val repository: TrainingRepository, clock: Clock) : ViewModel() {
    private val _state = MutableStateFlow(SetupUiState(today = clock.today()))
    val state: StateFlow<SetupUiState> = _state

    init {
        viewModelScope.launch {
            val parts = listOf(TrainingRepository.ALL_PARTS) + repository.availableParts()
            _state.update { it.copy(parts = parts) }
            loadSkills()
        }
    }

    /** À rappeler quand l'écran revient au premier plan : les boîtes ont pu changer. */
    fun refresh() {
        viewModelScope.launch { loadSkills() }
    }

    fun selectPart(part: Int) {
        if (part == _state.value.part) return
        _state.update { it.copy(part = part, selectedTag = null, skills = emptyList()) }
        viewModelScope.launch { loadSkills() }
    }

    fun selectTag(tagId: String?) = _state.update { it.copy(selectedTag = tagId) }
    fun selectMinutes(minutes: Int) = _state.update { it.copy(minutes = minutes) }

    private suspend fun loadSkills() {
        val part = _state.value.part
        val skills = repository.skills(part)
        _state.update { if (it.part == part) it.copy(skills = skills) else it }
    }
}

/** Onglet « Exercices » : l'entraînement libre. On choisit la part, la durée et la compétence. */
@Composable
fun TrainingSetupScreen(onStart: (part: Int, tag: String?, minutes: Int) -> Unit) {
    val container = appContainer()
    val viewModel = viewModel { SetupViewModel(container.repository, SystemClock) }
    val state by viewModel.state.collectAsStateWithLifecycle()
    androidx.lifecycle.compose.LifecycleResumeEffect(viewModel) {
        viewModel.refresh()
        onPauseOrDispose { }
    }

    Column(Modifier.fillMaxSize()) {
        ElanTopBar(title = "Exercices")
        LazyColumn(
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(start = ElanDimens.gutter, end = ElanDimens.gutter, bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item {
                LabelSmall("Part", Modifier.padding(bottom = 4.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    state.parts.forEach { part ->
                        ElanChip(text = partLabel(part), selected = state.part == part, onClick = { viewModel.selectPart(part) })
                    }
                }
                Text(
                    partDescription(state.part),
                    style = ElanTheme.type.bodyM,
                    color = ElanTheme.colors.muted,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
            item {
                LabelSmall("Durée", Modifier.padding(top = 12.dp, bottom = 4.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    DURATIONS.forEach { minutes ->
                        ElanChip(
                            text = durationLabel(minutes),
                            selected = state.minutes == minutes,
                            onClick = { viewModel.selectMinutes(minutes) },
                        )
                    }
                }
            }
            item { LabelSmall("Compétence", Modifier.padding(top = 12.dp, bottom = 4.dp)) }
            item {
                val due = state.skills.count { s -> s.state?.let { Leitner.isDue(it, state.today) } == true }
                val new = state.skills.count { it.state == null }
                SkillRow(
                    title = "Toutes les compétences",
                    status = "$due à réviser · $new à découvrir",
                    selected = state.selectedTag == null,
                    onClick = { viewModel.selectTag(null) },
                )
            }
            items(state.skills, key = { it.tagId }) { skill ->
                SkillRow(
                    title = skill.labelFr,
                    status = skillStatus(skill, state.part, state.today),
                    selected = state.selectedTag == skill.tagId,
                    onClick = { viewModel.selectTag(skill.tagId) },
                )
            }
        }
        ElanButton(
            text = "Commencer",
            onClick = { onStart(state.part, state.selectedTag, state.minutes) },
            modifier = Modifier.fillMaxWidth().padding(horizontal = ElanDimens.gutter).padding(top = 8.dp, bottom = 16.dp),
        )
    }
}

/** L'état de révision d'une compétence, en clair. */
fun skillStatus(skill: Skill, part: Int, today: Long): String {
    val state = skill.state ?: return "Nouvelle · ${unitCountLabel(part, skill.unitCount)}"
    val days = state.dueDay - today
    val due = when {
        days <= 0 -> "à réviser"
        days == 1L -> "revue demain"
        else -> "revue dans $days jours"
    }
    return "Boîte ${state.box} sur ${Leitner.LAST_BOX} · $due"
}

@Composable
private fun SkillRow(title: String, status: String, selected: Boolean, onClick: () -> Unit) {
    val c = ElanTheme.colors
    EdgeSurface(
        faceColor = if (selected) c.primaryContainer else c.surface,
        modifier = Modifier.fillMaxWidth(),
        onClick = onClick,
        shape = ElanShapes.option,
        edgeColor = if (selected) c.primary else c.outlineVariant,
        borderColor = if (selected) c.primary else c.outlineVariant,
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp),
        contentAlignment = Alignment.CenterStart,
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title, style = ElanTheme.type.titleM, color = if (selected) c.onPrimaryContainer else c.onSurface)
            Text(status, style = ElanTheme.type.bodyM, color = if (selected) c.onPrimaryContainer else c.muted)
        }
    }
}
