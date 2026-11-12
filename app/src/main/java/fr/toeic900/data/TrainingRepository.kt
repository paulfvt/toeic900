package fr.toeic900.data

import fr.toeic900.data.content.ContentDao
import fr.toeic900.data.content.QuestionEntity
import fr.toeic900.data.user.AttemptEntity
import fr.toeic900.data.user.SessionEntity
import fr.toeic900.data.user.UserDao
import fr.toeic900.data.user.toEntity
import fr.toeic900.domain.Clock
import fr.toeic900.domain.Explanation
import fr.toeic900.domain.Leitner
import fr.toeic900.domain.PickReason
import fr.toeic900.domain.Question
import fr.toeic900.domain.QuestionPicker
import fr.toeic900.domain.QuestionRef
import fr.toeic900.domain.QuestionReview
import fr.toeic900.domain.Skill
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow

/** La question choisie pour l'utilisateur, et pourquoi. */
data class PickedQuestion(
    val question: Question,
    /** Compétence au titre de laquelle la question est posée. */
    val tagId: String,
    val tagLabelFr: String,
    val tagLabelEn: String,
    val reason: PickReason,
)

data class AnswerOutcome(
    val correct: Boolean,
    /** Boîte de la compétence avant la réponse ; null si elle n'avait jamais été travaillée. */
    val boxBefore: Int?,
    val boxAfter: Int,
)

data class HomeStats(
    val totalSkills: Int = 0,
    /** Compétences arrivées à échéance. */
    val dueSkills: Int = 0,
    /** Compétences jamais travaillées. */
    val newSkills: Int = 0,
    val answeredToday: Int = 0,
    val totalAnswered: Int = 0,
    val totalCorrect: Int = 0,
)

data class SkillResult(
    val tagId: String,
    val labelFr: String,
    val labelEn: String,
    val asked: Int,
    val correct: Int,
    val boxBefore: Int?,
    val boxAfter: Int,
)

data class SessionSummary(
    val questionCount: Int,
    val correctCount: Int,
    val durationMs: Long,
    val skills: List<SkillResult>,
)

/**
 * Fait le lien entre les deux bases et le domaine : il lit le contenu, demande au domaine
 * quoi poser, et enregistre les réponses dans la base utilisateur.
 */
class TrainingRepository(
    private val contentDao: ContentDao,
    private val userDao: UserDao,
    private val clock: Clock,
    private val picker: QuestionPicker = QuestionPicker(),
) {
    /** Les compétences d'une part, dans l'ordre de la taxonomie, avec leur boîte de Leitner. */
    suspend fun skills(part: Int): List<Skill> {
        val states = userDao.skillStates().associate { it.tagId to it.toDomain() }
        return contentDao.tagsWithCount(part, POOL_TRAINING).map {
            Skill(it.id, it.labelFr, it.labelEn, it.questionCount, states[it.id])
        }
    }

    /** Les chiffres de l'accueil, mis à jour dès qu'une réponse est enregistrée. */
    fun observeHome(part: Int): Flow<HomeStats> = flow {
        val tagIds = contentDao.tagsWithCount(part, POOL_TRAINING).map { it.id }.toSet()
        val today = clock.today()
        emitAll(
            combine(
                userDao.observeSkillStates(),
                userDao.observeTotals(),
                userDao.observeAnsweredOn(today),
            ) { states, totals, answeredToday ->
                val known = states.filter { it.tagId in tagIds }
                HomeStats(
                    totalSkills = tagIds.size,
                    dueSkills = known.count { Leitner.isDue(it.toDomain(), today) },
                    newSkills = tagIds.size - known.size,
                    answeredToday = answeredToday,
                    totalAnswered = totals.total,
                    totalCorrect = totals.correct,
                )
            },
        )
    }

    suspend fun startSession(part: Int, focusTag: String?, plannedMinutes: Int): Long =
        userDao.insertSession(
            SessionEntity(startedAt = clock.nowMillis(), part = part, focusTagId = focusTag, plannedMinutes = plannedMinutes),
        )

    /** @return null quand toutes les questions du périmètre ont été posées dans la session */
    suspend fun nextQuestion(part: Int, focusTag: String?, askedThisSession: List<String>): PickedQuestion? {
        val tags = contentDao.tags().associateBy { it.id }
        val candidates = contentDao.questionTagRows(part, POOL_TRAINING)
            .groupBy { it.questionId }
            .map { (id, rows) -> QuestionRef(id, rows.sortedBy { it.position }.map { it.tagId }, rows.first().difficulty) }
            .sortedWith(compareBy<QuestionRef> { tags[it.primaryTag]?.sortOrder ?: Int.MAX_VALUE }.thenBy { it.id })

        val pick = picker.pick(
            candidates = candidates,
            focusTag = focusTag,
            skillStates = userDao.skillStates().associate { it.tagId to it.toDomain() },
            questionStates = userDao.questionStates().associate { it.questionId to it.toDomain() },
            askedThisSession = askedThisSession,
            today = clock.today(),
        ) ?: return null

        val entity = contentDao.question(pick.questionId) ?: return null
        val tagIds = candidates.first { it.id == pick.questionId }.tags
        val tag = tags[pick.tagId]
        return PickedQuestion(
            question = entity.toDomain(tagIds),
            tagId = pick.tagId,
            tagLabelFr = tag?.labelFr ?: pick.tagId,
            tagLabelEn = tag?.labelEn ?: pick.tagId,
            reason = pick.reason,
        )
    }

    /** Enregistre la réponse et met à jour la boîte de Leitner de chaque compétence de la question. */
    suspend fun submitAnswer(sessionId: Long, picked: PickedQuestion, selectedIndex: Int, timeSpentMs: Long): AnswerOutcome {
        val today = clock.today()
        val question = picked.question
        val correct = selectedIndex == question.answerIndex

        val previousSkills = userDao.skillStates().associate { it.tagId to it.toDomain() }
        val updatedSkills = (question.tags + picked.tagId).distinct().map { tag ->
            Leitner.review(previousSkills[tag] ?: Leitner.newSkill(tag, today), correct, today)
        }
        val previousQuestion = userDao.questionStates().firstOrNull { it.questionId == question.id }?.toDomain()
        val questionState = QuestionReview.afterAnswer(previousQuestion, question.id, correct, today)

        val boxBefore = previousSkills[picked.tagId]?.box
        val boxAfter = updatedSkills.first { it.tagId == picked.tagId }.box
        userDao.recordAnswer(
            attempt = AttemptEntity(
                sessionId = sessionId,
                questionId = question.id,
                tagId = picked.tagId,
                selectedIndex = selectedIndex,
                correct = correct,
                answeredAt = clock.nowMillis(),
                day = today,
                timeSpentMs = timeSpentMs,
                reason = picked.reason.name,
                boxBefore = boxBefore,
                boxAfter = boxAfter,
            ),
            skillStates = updatedSkills.map { it.toEntity() },
            questionState = questionState.toEntity(),
        )
        return AnswerOutcome(correct, boxBefore, boxAfter)
    }

    suspend fun finishSession(sessionId: Long) = userDao.finishSession(sessionId, clock.nowMillis())

    suspend fun summary(sessionId: Long): SessionSummary? {
        val session = userDao.session(sessionId) ?: return null
        val tags = contentDao.tags().associateBy { it.id }
        val skills = userDao.attempts(sessionId).groupBy { it.tagId }.map { (tagId, attempts) ->
            SkillResult(
                tagId = tagId,
                labelFr = tags[tagId]?.labelFr ?: tagId,
                labelEn = tags[tagId]?.labelEn ?: tagId,
                asked = attempts.size,
                correct = attempts.count { it.correct },
                boxBefore = attempts.first().boxBefore,
                boxAfter = attempts.last().boxAfter,
            )
        }
        return SessionSummary(
            questionCount = session.questionCount,
            correctCount = session.correctCount,
            durationMs = (session.endedAt ?: clock.nowMillis()) - session.startedAt,
            skills = skills.sortedBy { tags[it.tagId]?.sortOrder ?: Int.MAX_VALUE },
        )
    }

    companion object {
        const val POOL_TRAINING = "training"
    }
}

fun QuestionEntity.toDomain(tags: List<String>) = Question(
    id = id,
    part = part,
    tags = tags,
    difficulty = difficulty,
    stem = stem,
    options = options,
    answerIndex = answerIndex,
    explanationFr = Explanation(ruleFr, distractorNotesFr, trapFr),
    explanationEn = Explanation(ruleEn, distractorNotesEn, trapEn),
)
