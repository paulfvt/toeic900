package fr.toeic900.ui.lessons

import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import fr.toeic900.data.LessonListItem
import fr.toeic900.data.PathStepView
import fr.toeic900.data.TrainingRepository
import fr.toeic900.data.content.LessonContentDto
import fr.toeic900.data.content.LessonDto
import fr.toeic900.data.settings.ExplanationLanguage
import fr.toeic900.data.settings.SettingsRepository
import fr.toeic900.domain.LessonPath
import fr.toeic900.domain.StepKind
import fr.toeic900.domain.StepStatus
import fr.toeic900.ui.appContainer
import fr.toeic900.ui.components.ButtonStyle
import fr.toeic900.ui.components.CardStyle
import fr.toeic900.ui.components.ElanButton
import fr.toeic900.ui.components.ElanCard
import fr.toeic900.ui.components.ElanTopBar
import fr.toeic900.ui.components.LabelSmall
import fr.toeic900.ui.components.MarkupText
import fr.toeic900.ui.theme.ElanDimens
import fr.toeic900.ui.theme.ElanIcons
import fr.toeic900.ui.theme.ElanShapes
import fr.toeic900.ui.theme.ElanTheme
import fr.toeic900.ui.training.unitCountLabel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

private fun LessonDto.content(language: ExplanationLanguage): LessonContentDto =
    if (language == ExplanationLanguage.ENGLISH) en else fr

// ───────────────────────────── Fiche complète ─────────────────────────────

data class LessonUiState(val loading: Boolean = true, val lesson: LessonDto? = null)

class LessonViewModel(repository: TrainingRepository, settings: SettingsRepository, tagId: String) : ViewModel() {
    private val _state = MutableStateFlow(LessonUiState())
    val state: StateFlow<LessonUiState> = _state
    val language: StateFlow<ExplanationLanguage> = settings.explanationLanguage
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ExplanationLanguage.DEFAULT)

    init {
        viewModelScope.launch { _state.value = LessonUiState(loading = false, lesson = repository.lesson(tagId)) }
    }
}

/**
 * Une fiche de cours : l'essentiel, les règles avec leurs exemples, les pièges classiques.
 * @param onPractice lance un entraînement sur la compétence ; null quand la fiche est ouverte
 *   depuis une session en cours.
 */
@Composable
fun LessonScreen(tagId: String, onBack: () -> Unit, onPractice: (() -> Unit)?) {
    val container = appContainer()
    val viewModel = viewModel(key = "lesson-$tagId") { LessonViewModel(container.repository, container.settings, tagId) }
    val state by viewModel.state.collectAsStateWithLifecycle()
    val language by viewModel.language.collectAsStateWithLifecycle()
    val english = language == ExplanationLanguage.ENGLISH
    val c = ElanTheme.colors
    val type = ElanTheme.type

    Column(Modifier.fillMaxSize()) {
        ElanTopBar(title = if (english) "Lesson" else "Fiche de cours", navigationIcon = ElanIcons.Back, navigationDescription = "Retour", onNavigate = onBack)
        val lesson = state.lesson
        if (lesson == null) {
            if (!state.loading) {
                Text("Pas encore de fiche pour cette compétence.", style = type.bodyL, color = c.muted, modifier = Modifier.padding(ElanDimens.gutter))
            }
            return@Column
        }
        val content = lesson.content(language)
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = ElanDimens.gutter)
                .padding(bottom = ElanDimens.gapMajor),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(content.title, style = type.headline, color = c.onBg)
            MarkupText(content.summary, type.bodyL, c.onBg)
            KeyPointsCard(content, english)

            content.rules.forEachIndexed { index, rule ->
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    LabelSmall(if (english) "Rule ${index + 1}" else "Règle ${index + 1}", Modifier.padding(top = 8.dp))
                    Text(rule.title, style = type.titleL, color = c.onBg)
                    MarkupText(rule.text, type.bodyL, c.onBg)
                    rule.examples.forEach { example ->
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(c.surface, ElanShapes.md)
                                .padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            MarkupText(example.en, type.document, c.onSurface)
                            MarkupText(example.note, type.bodyM, c.muted)
                        }
                    }
                }
            }

            LabelSmall(if (english) "Classic TOEIC traps" else "Pièges classiques du TOEIC", Modifier.padding(top = 8.dp))
            content.traps.forEach { trap ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(ElanShapes.option)
                        .background(c.accentContainer)
                        .padding(12.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Icon(ElanIcons.Bulb, contentDescription = null, tint = c.onAccentContainer, modifier = Modifier.size(18.dp))
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(trap.title, style = type.titleM, color = c.onAccentContainer)
                        MarkupText(trap.text, type.bodyM, c.onAccentContainer, emphasisColor = c.onAccentContainer)
                    }
                }
            }
        }
        if (onPractice != null) {
            ElanButton(
                text = if (english) "Practice this skill" else "S'entraîner sur cette compétence",
                onClick = onPractice,
                modifier = Modifier.fillMaxWidth().padding(horizontal = ElanDimens.gutter).padding(top = 8.dp, bottom = 16.dp),
            )
        }
    }
}

/** La mini-fiche : les points à retenir. */
@Composable
private fun KeyPointsCard(content: LessonContentDto, english: Boolean) {
    val c = ElanTheme.colors
    ElanCard(style = CardStyle.Filled) {
        LabelSmall(if (english) "Key points" else "À retenir", color = c.onPrimaryContainer)
        Spacer(Modifier.height(8.dp))
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            content.keyPoints.forEach { point ->
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Box(Modifier.padding(top = 9.dp).size(6.dp).background(c.onPrimaryContainer, ElanShapes.full))
                    MarkupText(point, ElanTheme.type.bodyL, c.onPrimaryContainer, emphasisColor = c.onPrimaryContainer)
                }
            }
        }
    }
}

// ───────────────────────────── Liste des fiches ─────────────────────────────

class LessonListViewModel(repository: TrainingRepository, settings: SettingsRepository) : ViewModel() {
    private val _lessons = MutableStateFlow<List<LessonListItem>>(emptyList())
    val lessons: StateFlow<List<LessonListItem>> = _lessons
    val language: StateFlow<ExplanationLanguage> = settings.explanationLanguage
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ExplanationLanguage.DEFAULT)

    init {
        viewModelScope.launch { _lessons.value = repository.lessonList() }
    }
}

private fun groupLabel(group: String, english: Boolean) = when (group) {
    "grammar" -> if (english) "Grammar" else "Grammaire"
    "vocabulary" -> if (english) "Vocabulary" else "Vocabulaire"
    "reading" -> if (english) "Reading" else "Lecture"
    else -> group
}

@Composable
fun LessonListScreen(onBack: () -> Unit, onOpen: (tagId: String) -> Unit) {
    val container = appContainer()
    val viewModel = viewModel { LessonListViewModel(container.repository, container.settings) }
    val lessons by viewModel.lessons.collectAsStateWithLifecycle()
    val language by viewModel.language.collectAsStateWithLifecycle()
    val english = language == ExplanationLanguage.ENGLISH
    val c = ElanTheme.colors

    Column(Modifier.fillMaxSize()) {
        ElanTopBar(title = if (english) "Lessons" else "Fiches de cours", navigationIcon = ElanIcons.Back, navigationDescription = "Retour", onNavigate = onBack)
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = ElanDimens.gutter, end = ElanDimens.gutter, bottom = ElanDimens.gapMajor),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            lessons.groupBy { it.group }.forEach { (group, items) ->
                item(key = "group-$group") { LabelSmall(groupLabel(group, english), Modifier.padding(top = 12.dp, bottom = 4.dp)) }
                items(items, key = { it.tagId }) { lesson ->
                    ElanCard(onClick = { onOpen(lesson.tagId) }) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            Text(
                                if (english) lesson.titleEn else lesson.titleFr,
                                style = ElanTheme.type.titleM,
                                color = c.onSurface,
                                modifier = Modifier.weight(1f),
                            )
                            Icon(ElanIcons.ChevronRight, contentDescription = null, tint = c.muted, modifier = Modifier.size(20.dp))
                        }
                    }
                }
            }
        }
    }
}

// ───────────────────────────── Début d'une étape du chemin ─────────────────────────────

data class StepIntroUiState(
    val loading: Boolean = true,
    val step: PathStepView? = null,
    val unitTitle: String = "",
    /** La fiche de la leçon ; null pour un bilan. */
    val lesson: LessonDto? = null,
    /** Bilan : les compétences révisées. */
    val reviewedSkills: List<String> = emptyList(),
)

class StepIntroViewModel(private val repository: TrainingRepository, settings: SettingsRepository, private val stepId: String) : ViewModel() {
    private val _state = MutableStateFlow(StepIntroUiState())
    val state: StateFlow<StepIntroUiState> = _state
    val language: StateFlow<ExplanationLanguage> = settings.explanationLanguage
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ExplanationLanguage.DEFAULT)

    init {
        viewModelScope.launch {
            val path = repository.path()
            val step = path.steps.firstOrNull { it.step.id == stepId }
            val lesson = step?.takeIf { it.step.kind == StepKind.LESSON }?.let { repository.lesson(it.step.tags.first()) }
            val labels = path.steps.filter { it.step.kind == StepKind.LESSON }.associate { it.step.tags.first() to it.titleFr }
            _state.update {
                StepIntroUiState(
                    loading = false,
                    step = step,
                    unitTitle = path.unitOf(stepId)?.titleFr.orEmpty(),
                    lesson = lesson,
                    reviewedSkills = step?.takeIf { it.step.kind == StepKind.REVIEW }?.step?.tags?.map { labels[it] ?: it }.orEmpty(),
                )
            }
        }
    }
}

/**
 * Le début d'une étape du chemin : la mini-fiche (les points à retenir), puis le bouton qui
 * lance les exercices.
 */
@Composable
fun StepIntroScreen(stepId: String, onBack: () -> Unit, onStart: () -> Unit, onOpenLesson: (tagId: String) -> Unit) {
    val container = appContainer()
    val viewModel = viewModel(key = "step-$stepId") { StepIntroViewModel(container.repository, container.settings, stepId) }
    val state by viewModel.state.collectAsStateWithLifecycle()
    val language by viewModel.language.collectAsStateWithLifecycle()
    val english = language == ExplanationLanguage.ENGLISH
    val c = ElanTheme.colors
    val type = ElanTheme.type
    val view = state.step

    Column(Modifier.fillMaxSize()) {
        ElanTopBar(title = state.unitTitle, navigationIcon = ElanIcons.Back, navigationDescription = "Retour", onNavigate = onBack)
        if (view == null) {
            if (!state.loading) {
                Text("Cette étape n'existe plus dans le chemin.", style = type.bodyL, color = c.muted, modifier = Modifier.padding(ElanDimens.gutter))
            }
            return@Column
        }
        val step = view.step
        val review = step.kind == StepKind.REVIEW
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = ElanDimens.gutter)
                .padding(bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            LabelSmall(if (review) "Bilan" else "Leçon")
            val lesson = state.lesson
            if (lesson != null) {
                val content = lesson.content(language)
                Text(content.title, style = type.headline, color = c.onBg)
                MarkupText(content.summary, type.bodyL, c.onBg)
                KeyPointsCard(content, english)
                ElanButton(
                    if (english) "Read the full lesson" else "Lire la fiche complète",
                    { onOpenLesson(step.tags.first()) },
                    style = ButtonStyle.Text,
                )
            } else {
                Text(view.titleFr, style = type.headline, color = c.onBg)
                if (review) {
                    Text("Un mélange d'exercices sur les compétences de l'unité :", style = type.bodyL, color = c.onBg)
                    ElanCard(style = CardStyle.Tonal) {
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            state.reviewedSkills.forEach { Text(it, style = type.titleM, color = c.onSurface) }
                        }
                    }
                }
            }
            Text(
                "${unitCountLabel(step.part, step.exerciseCount)} (Part ${step.part}). " +
                    "L'étape est validée avec au moins ${LessonPath.PASS_PERCENT} % de bonnes réponses" +
                    (if (step.part == 5) " : ${LessonPath.requiredCorrect(step.exerciseCount)} sur ${step.exerciseCount}." else "."),
                style = type.bodyM,
                color = c.muted,
            )
        }
        val locked = view.status == StepStatus.LOCKED
        ElanButton(
            text = when {
                locked -> "Étape verrouillée"
                view.status == StepStatus.DONE -> "Refaire les exercices"
                else -> "Commencer les exercices"
            },
            onClick = onStart,
            enabled = !locked,
            modifier = Modifier.fillMaxWidth().padding(horizontal = ElanDimens.gutter).padding(top = 8.dp, bottom = 16.dp),
        )
    }
}
