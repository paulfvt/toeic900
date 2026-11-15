package fr.toeic900.ui.training

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import fr.toeic900.data.AnswerOutcome
import fr.toeic900.data.PathStepView
import fr.toeic900.data.PickedQuestion
import fr.toeic900.data.PickedUnit
import fr.toeic900.data.SessionScope
import fr.toeic900.data.TrainingRepository
import fr.toeic900.data.settings.ExplanationLanguage
import fr.toeic900.data.settings.SettingsRepository
import fr.toeic900.domain.Clock
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class TrainingUiState(
    val loading: Boolean = true,
    /** L'exercice en cours : une question seule (Part 5) ou un texte et ses questions (Parts 6 et 7). */
    val unit: PickedUnit? = null,
    /** Rang de la question en cours dans l'exercice. */
    val questionIndex: Int = 0,
    val selectedIndex: Int? = null,
    /** Non nul une fois la réponse validée : la correction est affichée. */
    val outcome: AnswerOutcome? = null,
    val answered: Int = 0,
    val correct: Int = 0,
    /** 0 = session libre. */
    val plannedMinutes: Int = 0,
    val elapsedMs: Long = 0,
    val language: ExplanationLanguage = ExplanationLanguage.DEFAULT,
    /** Les compétences qui ont une fiche de cours : le bouton « Voir la fiche » en dépend. */
    val lessonTags: Set<String> = emptySet(),
    /** L'étape du chemin jouée par cette session ; null pour un entraînement libre. */
    val step: PathStepView? = null,
    /** Exercices terminés depuis le début de la session. */
    val unitsDone: Int = 0,
    /** Non nul quand la session est terminée : l'écran passe au bilan. */
    val finishedSessionId: Long? = null,
    /** Session quittée sans aucune réponse : retour direct, sans bilan. */
    val abandoned: Boolean = false,
) {
    val plannedMs: Long get() = plannedMinutes * 60_000L
    val timeIsUp: Boolean get() = plannedMinutes > 0 && elapsedMs >= plannedMs
    val picked: PickedQuestion? get() = unit?.questions?.getOrNull(questionIndex)
    val isLastQuestionOfUnit: Boolean get() = unit?.let { questionIndex >= it.questions.lastIndex } ?: true

    /** Nombre d'exercices prévus par l'étape du chemin ; null en entraînement libre. */
    val targetUnits: Int? get() = step?.step?.exerciseCount

    /** Vrai si la correction affichée est la dernière de la session. */
    val isLastOfSession: Boolean
        get() = isLastQuestionOfUnit && (timeIsUp || targetUnits?.let { unitsDone + 1 >= it } == true)
}

/**
 * Déroule une session : demande l'exercice suivant au dépôt de données, enregistre chaque
 * réponse, et s'arrête quand le temps est écoulé, quand l'étape du chemin est finie ou quand
 * il n'y a plus d'exercice.
 */
class TrainingViewModel(
    private val repository: TrainingRepository,
    settings: SettingsRepository,
    private val clock: Clock,
    part: Int,
    focusTag: String?,
    plannedMinutes: Int,
    private val stepId: String?,
) : ViewModel() {
    // Une étape du chemin n'est pas chronométrée : elle se termine au bout de ses exercices.
    private val _state = MutableStateFlow(TrainingUiState(plannedMinutes = if (stepId == null) plannedMinutes else 0))
    val state: StateFlow<TrainingUiState> = _state

    private var scope = SessionScope(part, focusTag)
    private var sessionId = 0L
    private val asked = mutableListOf<String>()
    private var questionShownAt = 0L

    // Chronomètre : le temps ne court que lorsque l'écran de la session est affiché.
    private var activeMs = 0L
    private var resumedAt: Long? = null

    init {
        viewModelScope.launch { settings.explanationLanguage.collect { lang -> _state.update { it.copy(language = lang) } } }
        viewModelScope.launch {
            val step = stepId?.let { id -> repository.path().steps.firstOrNull { it.step.id == id } }
            if (step != null) scope = repository.scopeOf(step.step)
            sessionId = repository.startSession(scope, _state.value.plannedMinutes, stepId = step?.step?.id)
            _state.update { it.copy(step = step, lessonTags = repository.lessonTagIds()) }
            if (resumedAt == null) resumedAt = clock.nowMillis()
            loadNextUnit()
            // Une mise à jour par seconde tant que la session n'est pas finie.
            while (_state.value.finishedSessionId == null && !_state.value.abandoned) {
                _state.update { it.copy(elapsedMs = elapsed()) }
                delay(1_000)
            }
        }
    }

    private fun elapsed(): Long = activeMs + (resumedAt?.let { clock.nowMillis() - it } ?: 0)

    /** L'écran de la session repasse au premier plan. */
    fun resume() {
        if (resumedAt == null) resumedAt = clock.nowMillis()
    }

    /** L'écran de la session est masqué (fiche de cours ouverte, app en arrière-plan) : le temps s'arrête. */
    fun pause() {
        resumedAt?.let { activeMs += clock.nowMillis() - it }
        resumedAt = null
    }

    fun select(index: Int) {
        if (_state.value.outcome == null) _state.update { it.copy(selectedIndex = index) }
    }

    fun validate() {
        val current = _state.value
        val unit = current.unit ?: return
        val selected = current.selectedIndex ?: return
        if (current.outcome != null) return
        viewModelScope.launch {
            val outcome = repository.submitAnswer(sessionId, unit, current.questionIndex, selected, clock.nowMillis() - questionShownAt)
            _state.update {
                it.copy(outcome = outcome, answered = it.answered + 1, correct = it.correct + if (outcome.correct) 1 else 0)
            }
        }
    }

    /** Après la correction : question suivante du texte, exercice suivant, ou fin de session. */
    fun next() {
        val current = _state.value
        if (current.outcome == null) return
        viewModelScope.launch {
            if (!current.isLastQuestionOfUnit) {
                questionShownAt = clock.nowMillis()
                _state.update { it.copy(questionIndex = it.questionIndex + 1, selectedIndex = null, outcome = null) }
                return@launch
            }
            val done = current.unitsDone + 1
            _state.update { it.copy(unitsDone = done) }
            val target = current.targetUnits
            when {
                target != null && done >= target -> finish(exercisesCompleted = true)
                current.timeIsUp -> finish(exercisesCompleted = false)
                else -> loadNextUnit()
            }
        }
    }

    fun quit() {
        viewModelScope.launch { finish(exercisesCompleted = false) }
    }

    private suspend fun loadNextUnit() {
        val unit = repository.nextUnit(scope, asked)
        if (unit == null) {
            // Plus aucun exercice dans le périmètre : pour une étape du chemin, elle est allée au bout.
            finish(exercisesCompleted = _state.value.unitsDone > 0)
            return
        }
        asked += unit.id
        questionShownAt = clock.nowMillis()
        _state.update { it.copy(loading = false, unit = unit, questionIndex = 0, selectedIndex = null, outcome = null) }
    }

    private suspend fun finish(exercisesCompleted: Boolean) {
        repository.finishSession(sessionId, exercisesCompleted)
        _state.update {
            if (it.answered == 0) it.copy(abandoned = true) else it.copy(finishedSessionId = sessionId)
        }
    }
}
