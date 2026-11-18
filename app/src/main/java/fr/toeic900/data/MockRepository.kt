package fr.toeic900.data

import fr.toeic900.data.content.ContentDao
import fr.toeic900.data.user.MockAnswerEntity
import fr.toeic900.data.user.MockResultEntity
import fr.toeic900.data.user.UserDao
import fr.toeic900.domain.Clock
import fr.toeic900.domain.Document
import fr.toeic900.domain.MockScore
import fr.toeic900.domain.Question
import fr.toeic900.domain.ScoreEstimate
import fr.toeic900.domain.ScoreEstimator
import fr.toeic900.domain.Tally

/** Une question de test blanc, avec la compétence qu'elle mesure. */
data class MockQuestion(val question: Question, val tagId: String, val tagLabelFr: String, val tagLabelEn: String)

/** Une unité du test : une question Part 5, ou un texte (Part 6) ou un jeu de documents (Part 7) avec ses questions. */
data class MockUnit(val id: String, val part: Int, val documents: List<Document>, val questions: List<MockQuestion>)

data class MockTest(val id: String, val titleFr: String, val minutes: Int, val units: List<MockUnit>) {
    val questions: List<MockQuestion> get() = units.flatMap { it.questions }

    /** La part de chaque question, dans l'ordre : ce dont l'indicateur de rythme a besoin. */
    val parts: List<Int> get() = questions.map { it.question.part }
}

data class MockTestInfo(val id: String, val titleFr: String, val minutes: Int, val questionCount: Int, val completed: List<MockResultEntity>)

/** Ce que l'écran de résultats affiche. */
data class MockResultView(
    val result: MockResultEntity,
    val test: MockTest,
    /** Réponse donnée par question (absente ou -1 : sans réponse). */
    val answers: Map<String, Int>,
    /** Le score de ce test seul, avec sa fourchette. */
    val estimate: ScoreEstimate,
    val byPart: Map<Int, Tally>,
    /** Compétences, de la plus fragile à la plus solide. */
    val byTag: List<Pair<MockQuestion, Tally>>,
) {
    fun isCorrect(q: MockQuestion) = answers[q.question.id] == q.question.answerIndex
    val mistakes: List<MockQuestion> get() = test.questions.filterNot(::isCorrect)
}

class MockRepository(
    private val contentDao: ContentDao,
    private val userDao: UserDao,
    private val clock: Clock,
) {
    suspend fun tests(): List<MockTestInfo> {
        val done = userDao.completedMocks().groupBy { it.testId }
        return contentDao.mockTests().map { t ->
            val count = load(t.id)?.questions?.size ?: 0
            MockTestInfo(t.id, t.titleFr, t.minutes, count, done[t.id].orEmpty())
        }
    }

    suspend fun load(testId: String): MockTest? {
        val entity = contentDao.mockTest(testId) ?: return null
        val tags = contentDao.tags().associateBy { it.id }
        val tagsOf = contentDao.questionTagRows(0, POOL_MOCK).groupBy { it.questionId }
            .mapValues { (_, rows) -> rows.sortedBy { it.position }.map { it.tagId } }

        fun mockQuestion(e: fr.toeic900.data.content.QuestionEntity): MockQuestion {
            val questionTags = tagsOf[e.id].orEmpty()
            val primary = questionTags.firstOrNull() ?: ""
            return MockQuestion(e.toDomain(questionTags), primary, tags[primary]?.labelFr ?: primary, tags[primary]?.labelEn ?: primary)
        }

        val units = entity.units.mapNotNull { unitId ->
            val stimulus = contentDao.stimulus(unitId)
            if (stimulus == null) {
                val q = contentDao.question(unitId) ?: return@mapNotNull null
                MockUnit(q.id, q.part, emptyList(), listOf(mockQuestion(q)))
            } else {
                MockUnit(
                    id = stimulus.id,
                    part = stimulus.part,
                    documents = stimulus.documents.map { Document(it.genre, it.title, it.text) },
                    questions = contentDao.questionsOfStimulus(stimulus.id).map(::mockQuestion),
                )
            }
        }
        return MockTest(entity.id, entity.titleFr, entity.minutes, units)
    }

    suspend fun start(test: MockTest): Long = userDao.insertMockResult(
        MockResultEntity(
            testId = test.id,
            startedAt = clock.nowMillis(),
            day = clock.today(),
            plannedMinutes = test.minutes,
            questionCount = test.questions.size,
        ),
    )

    /** Rend la copie : une ligne par question du test, répondue ou non. */
    suspend fun submit(mockId: Long, test: MockTest, answers: Map<String, Int>, timeSpent: Map<String, Long>, elapsedMs: Long, timedOut: Boolean) {
        val today = clock.today()
        val rows = test.questions.map { q ->
            val selected = answers[q.question.id] ?: -1
            MockAnswerEntity(
                mockId = mockId,
                questionId = q.question.id,
                tagId = q.tagId,
                selectedIndex = selected,
                correct = selected == q.question.answerIndex,
                day = today,
                timeSpentMs = timeSpent[q.question.id] ?: 0,
            )
        }
        userDao.submitMock(mockId, rows, clock.nowMillis(), elapsedMs, timedOut)
    }

    suspend fun result(mockId: Long): MockResultView? {
        val result = userDao.mockResult(mockId) ?: return null
        val test = load(result.testId) ?: return null
        val answers = userDao.mockAnswers(mockId).associate { it.questionId to it.selectedIndex }
        fun correct(q: MockQuestion) = answers[q.question.id] == q.question.answerIndex
        fun tally(qs: List<MockQuestion>) = Tally(qs.size, qs.count(::correct))
        val byPart = test.questions.groupBy { it.question.part }.mapValues { tally(it.value) }.toSortedMap()
        val byTag = test.questions.groupBy { it.tagId }.values
            .map { qs -> qs.first() to tally(qs) }
            .sortedWith(compareBy({ it.second.rate }, { -it.second.total }))
        // Le score de ce test seul : un test blanc et aucun entraînement, donc jamais null.
        val estimate = ScoreEstimator.estimate(listOf(MockScore(result.day, tally(test.questions))), Tally.EMPTY)!!
        return MockResultView(result, test, answers, estimate, byPart, byTag)
    }

    companion object {
        const val POOL_MOCK = "mock"
    }
}
