package fr.toeic900.ui.training

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import fr.toeic900.data.AnswerOutcome
import fr.toeic900.data.PickedQuestion
import fr.toeic900.data.TrainingRepository
import fr.toeic900.data.settings.ExplanationLanguage
import fr.toeic900.data.settings.SettingsRepository
import fr.toeic900.domain.Clock
import fr.toeic900.ui.PART_5
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class TrainingUiState(
    val loading: Boolean = true,
    val picked: PickedQuestion? = null,
    val selectedIndex: Int? = null,
    /** Non nul une fois la réponse validée : la correction est affichée. */
    val outcome: AnswerOutcome? = null,
    val answered: Int = 0,
    val correct: Int = 0,
    /** 0 = session libre. */
    val plannedMinutes: Int = 0,
    val elapsedMs: Long = 0,
    val language: ExplanationLanguage = ExplanationLanguage.DEFAULT,
    /** Non nul quand la session est terminée : l'écran passe au bilan. */
    val finishedSessionId: Long? = null,
    /** Session quittée sans aucune réponse : retour direct, sans bilan. */
    val abandoned: Boolean = false,
) {
    val plannedMs: Long get() = plannedMinutes * 60_000L
    val timeIsUp: Boolean get() = plannedMinutes > 0 && elapsedMs >= plannedMs
}

/**
 * Déroule une session d'entraînement : demande la question suivante au dépôt de données,
 * enregistre chaque réponse, et s'arrête quand le temps est écoulé ou qu'il n'y a plus de question.
 */
class TrainingViewModel(
    private val repository: TrainingRepository,
    settings: SettingsRepository,
    private val clock: Clock,
    private val focusTag: String?,
    plannedMinutes: Int,
) : ViewModel() {
    private val _state = MutableStateFlow(TrainingUiState(plannedMinutes = plannedMinutes))
    val state: StateFlow<TrainingUiState> = _state

    private var sessionId = 0L
    private val asked = mutableListOf<String>()
    private var startedAt = 0L
    private var questionShownAt = 0L

    init {
        viewModelScope.launch { settings.explanationLanguage.collect { lang -> _state.update { it.copy(language = lang) } } }
        viewModelScope.launch {
            sessionId = repository.startSession(PART_5, focusTag, plannedMinutes)
            startedAt = clock.nowMillis()
            loadNext()
            // Le chronomètre : une mise à jour par seconde tant que la session n'est pas finie.
            while (_state.value.finishedSessionId == null && !_state.value.abandoned) {
                _state.update { it.copy(elapsedMs = clock.nowMillis() - startedAt) }
                delay(1_000)
            }
        }
    }

    fun select(index: Int) {
        if (_state.value.outcome == null) _state.update { it.copy(selectedIndex = index) }
    }

    fun validate() {
        val current = _state.value
        val picked = current.picked ?: return
        val selected = current.selectedIndex ?: return
        if (current.outcome != null) return
        viewModelScope.launch {
            val outcome = repository.submitAnswer(sessionId, picked, selected, clock.nowMillis() - questionShownAt)
            _state.update {
                it.copy(outcome = outcome, answered = it.answered + 1, correct = it.correct + if (outcome.correct) 1 else 0)
            }
        }
    }

    /** Après la correction : question suivante, ou fin de session si le temps est écoulé. */
    fun next() {
        viewModelScope.launch {
            if (_state.value.timeIsUp) finish() else loadNext()
        }
    }

    fun quit() {
        viewModelScope.launch { finish() }
    }

    private suspend fun loadNext() {
        val picked = repository.nextQuestion(PART_5, focusTag, asked)
        if (picked == null) {
            finish()
            return
        }
        asked += picked.question.id
        questionShownAt = clock.nowMillis()
        _state.update { it.copy(loading = false, picked = picked, selectedIndex = null, outcome = null) }
    }

    private suspend fun finish() {
        repository.finishSession(sessionId)
        _state.update {
            if (it.answered == 0) it.copy(abandoned = true) else it.copy(finishedSessionId = sessionId)
        }
    }
}
