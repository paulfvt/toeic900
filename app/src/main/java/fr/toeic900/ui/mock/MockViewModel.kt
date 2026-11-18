package fr.toeic900.ui.mock

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import fr.toeic900.data.MockRepository
import fr.toeic900.data.MockTest
import fr.toeic900.data.MockUnit
import fr.toeic900.data.ProgressRepository
import fr.toeic900.domain.Clock
import fr.toeic900.domain.MockPace
import fr.toeic900.domain.Pace
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

data class MockUiState(
    val test: MockTest? = null,
    /** Rang de la question affichée dans tout le test (0 à 49). */
    val index: Int = 0,
    /** Réponse choisie par question ; modifiable jusqu'à la remise de la copie. */
    val answers: Map<String, Int> = emptyMap(),
    val elapsedMs: Long = 0,
    val submitting: Boolean = false,
    /** Non nul une fois la copie rendue : l'écran passe aux résultats. */
    val finishedMockId: Long? = null,
) {
    val plannedMs: Long get() = (test?.minutes ?: 0) * 60_000L
    val total: Int get() = test?.questions?.size ?: 0
    val unanswered: Int get() = total - answers.size
    val pace: Pace? get() = test?.let { MockPace.status(it.parts, index, elapsedMs, plannedMs) }

    /** L'unité de la question affichée, et le rang de la question dans cette unité. */
    val position: Pair<MockUnit, Int>?
        get() {
            var start = 0
            for (unit in test?.units.orEmpty()) {
                if (index < start + unit.questions.size) return unit to index - start
                start += unit.questions.size
            }
            return null
        }
}

/**
 * Un demi-test chronométré, comme à l'examen : on avance et on revient librement, aucune
 * correction avant la fin, la copie est rendue d'office quand le temps est écoulé. Le temps
 * court en continu, même si l'on quitte l'app.
 */
class MockViewModel(
    private val repository: MockRepository,
    private val progress: ProgressRepository,
    private val clock: Clock,
    testId: String,
) : ViewModel() {
    private val _state = MutableStateFlow(MockUiState())
    val state: StateFlow<MockUiState> = _state

    private var mockId = 0L
    private var startedAt = 0L
    private var shownAt = 0L
    private val timeSpent = mutableMapOf<String, Long>()

    init {
        viewModelScope.launch {
            val test = repository.load(testId) ?: return@launch
            mockId = repository.start(test)
            startedAt = clock.nowMillis()
            shownAt = startedAt
            _state.update { it.copy(test = test) }
            while (isActive && _state.value.finishedMockId == null) {
                val elapsed = clock.nowMillis() - startedAt
                _state.update { it.copy(elapsedMs = elapsed) }
                if (elapsed >= _state.value.plannedMs) {
                    submit(timedOut = true)
                    break
                }
                delay(1_000)
            }
        }
    }

    private fun currentQuestionId(): String? = _state.value.position?.let { (unit, i) -> unit.questions[i].question.id }

    /** Ajoute au compteur de la question affichée le temps passé dessus. */
    private fun recordTime() {
        val now = clock.nowMillis()
        currentQuestionId()?.let { timeSpent[it] = (timeSpent[it] ?: 0) + (now - shownAt) }
        shownAt = now
    }

    fun select(optionIndex: Int) {
        val id = currentQuestionId() ?: return
        _state.update { s ->
            // Toucher à nouveau l'option choisie l'efface : on peut laisser une question en blanc.
            val answers = if (s.answers[id] == optionIndex) s.answers - id else s.answers + (id to optionIndex)
            s.copy(answers = answers)
        }
    }

    fun goTo(index: Int) {
        val s = _state.value
        if (index !in 0 until s.total || index == s.index) return
        recordTime()
        _state.update { it.copy(index = index) }
    }

    fun submit(timedOut: Boolean = false) {
        val s = _state.value
        val test = s.test ?: return
        if (s.submitting || s.finishedMockId != null) return
        recordTime()
        _state.update { it.copy(submitting = true) }
        viewModelScope.launch {
            val elapsed = (clock.nowMillis() - startedAt).coerceAtMost(s.plannedMs)
            repository.submit(mockId, test, _state.value.answers, timeSpent, elapsed, timedOut)
            progress.checkAchievements()
            _state.update { it.copy(finishedMockId = mockId) }
        }
    }
}
