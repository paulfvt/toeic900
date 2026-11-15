package fr.toeic900.data

import fr.toeic900.data.content.ContentDao
import fr.toeic900.data.content.LessonDto
import fr.toeic900.data.content.PathStepEntity
import fr.toeic900.data.content.PathUnitEntity
import fr.toeic900.data.content.QuestionEntity
import fr.toeic900.data.content.TagEntity
import fr.toeic900.data.user.AttemptEntity
import fr.toeic900.data.user.LessonProgressEntity
import fr.toeic900.data.user.SessionEntity
import fr.toeic900.data.user.UserDao
import fr.toeic900.data.user.toEntity
import fr.toeic900.domain.Clock
import fr.toeic900.domain.Document
import fr.toeic900.domain.Explanation
import fr.toeic900.domain.Leitner
import fr.toeic900.domain.LessonPath
import fr.toeic900.domain.LessonRun
import fr.toeic900.domain.PathStep
import fr.toeic900.domain.PickReason
import fr.toeic900.domain.Question
import fr.toeic900.domain.QuestionRef
import fr.toeic900.domain.QuestionReview
import fr.toeic900.domain.Skill
import fr.toeic900.domain.StepKind
import fr.toeic900.domain.StepProgress
import fr.toeic900.domain.StepStatus
import fr.toeic900.domain.UnitPicker
import fr.toeic900.domain.UnitRef
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map

/** Le périmètre d'une session : dans quelles questions on a le droit de piocher. */
data class SessionScope(
    /** 5, 6 ou 7 ; [TrainingRepository.ALL_PARTS] pour toutes. */
    val part: Int,
    /** La compétence choisie, ou null pour toutes. */
    val focusTag: String? = null,
    /** Bilan d'unité : seulement les exercices qui travaillent l'une de ces compétences. */
    val onlyTags: Set<String>? = null,
)

/** Une question servie à l'utilisateur. */
data class PickedQuestion(
    val question: Question,
    /** Compétence au titre de laquelle la question compte. */
    val tagId: String,
    val tagLabelFr: String,
    val tagLabelEn: String,
    val reason: PickReason,
)

/**
 * L'exercice servi à l'utilisateur : une question seule (Part 5, sans document), ou un texte
 * et toutes ses questions (Parts 6 et 7).
 */
data class PickedUnit(
    val id: String,
    val part: Int,
    val documents: List<Document>,
    val questions: List<PickedQuestion>,
    val reason: PickReason,
) {
    val isStimulus: Boolean get() = documents.isNotEmpty()
}

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

/** Le résultat d'une session jouée depuis le chemin de leçons. */
data class LessonOutcome(
    val stepId: String,
    val kind: StepKind,
    val titleFr: String,
    val titleEn: String,
    /** Faux si la session a été quittée avant la fin des exercices. */
    val finished: Boolean,
    val passed: Boolean,
    /** Nombre de bonnes réponses qu'il fallait sur les questions posées. */
    val requiredCorrect: Int,
    /** La prochaine étape à faire après ce passage, s'il en reste une. */
    val nextStepId: String?,
)

data class SessionSummary(
    val questionCount: Int,
    val correctCount: Int,
    val durationMs: Long,
    val skills: List<SkillResult>,
    val lesson: LessonOutcome? = null,
)

/** Une étape du chemin, prête à afficher. */
data class PathStepView(
    val step: PathStep,
    val status: StepStatus,
    val progress: StepProgress?,
    val titleFr: String,
    val titleEn: String,
)

data class PathUnitView(
    val id: String,
    val titleFr: String,
    val titleEn: String,
    val steps: List<PathStepView>,
) {
    val doneCount: Int get() = steps.count { it.status == StepStatus.DONE }
}

data class PathOverview(val units: List<PathUnitView> = emptyList()) {
    val steps: List<PathStepView> get() = units.flatMap { it.steps }
    val doneCount: Int get() = units.sumOf { it.doneCount }
    val totalCount: Int get() = units.sumOf { it.steps.size }

    /** La prochaine étape à faire ; null si le chemin est vide ou entièrement réussi. */
    val next: PathStepView? get() = steps.firstOrNull { it.status == StepStatus.AVAILABLE }

    fun unitOf(stepId: String): PathUnitView? = units.firstOrNull { unit -> unit.steps.any { it.step.id == stepId } }
}

/** Une fiche dans la liste des fiches. */
data class LessonListItem(
    val tagId: String,
    val group: String,
    val titleFr: String,
    val titleEn: String,
    val summaryFr: String,
    val summaryEn: String,
)

/**
 * Fait le lien entre les deux bases et le domaine : il lit le contenu, demande au domaine
 * quoi servir, et enregistre les réponses dans la base utilisateur.
 */
class TrainingRepository(
    private val contentDao: ContentDao,
    private val userDao: UserDao,
    private val clock: Clock,
    private val picker: UnitPicker = UnitPicker(),
) {
    /** Les exercices d'un périmètre, et ce qu'il faut savoir d'eux pour les charger. */
    private class Catalog(val units: List<UnitRef>, val stimulusIds: Set<String>, val parts: Set<Int>)

    private suspend fun catalog(part: Int, tags: List<TagEntity>): Catalog {
        val rank = tags.associate { it.id to it.sortOrder }
        val questions = contentDao.questionTagRows(part, POOL_TRAINING)
            .groupBy { it.questionId }
            .map { (id, rows) -> rows.first() to QuestionRef(id, rows.sortedBy { it.position }.map { it.tagId }, rows.first().difficulty) }
        val units = questions
            .groupBy { (row, _) -> row.stimulusId ?: row.questionId }
            .map { (unitId, members) -> UnitRef(unitId, members.sortedBy { it.first.questionPosition }.map { it.second }) }
            .sortedWith(compareBy<UnitRef> { unit -> unit.primaryTags.minOf { rank[it] ?: Int.MAX_VALUE } }.thenBy { it.id })
        return Catalog(
            units = units,
            stimulusIds = questions.mapNotNull { it.first.stimulusId }.toSet(),
            parts = questions.map { it.first.part }.toSet(),
        )
    }

    /** Les parts qui ont du contenu d'entraînement, dans l'ordre. */
    suspend fun availableParts(): List<Int> = catalog(ALL_PARTS, contentDao.tags()).parts.sorted()

    /**
     * Les compétences d'un périmètre, dans l'ordre de la taxonomie, avec leur boîte de Leitner
     * et le nombre d'exercices qui les travaillent.
     */
    suspend fun skills(part: Int): List<Skill> {
        val tags = contentDao.tags()
        val states = userDao.skillStates().associate { it.tagId to it.toDomain() }
        val counts = catalog(part, tags).units
            .flatMap { unit -> unit.questions.flatMap { it.tags }.distinct() }
            .groupingBy { it }
            .eachCount()
        return tags.filter { it.id in counts }.map { Skill(it.id, it.labelFr, it.labelEn, counts.getValue(it.id), states[it.id]) }
    }

    /** Les chiffres de l'accueil, toutes parts confondues, mis à jour dès qu'une réponse est enregistrée. */
    fun observeHome(): Flow<HomeStats> = flow {
        val tagIds = catalog(ALL_PARTS, contentDao.tags()).units.flatMap { it.primaryTags }.toSet()
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

    suspend fun startSession(scope: SessionScope, plannedMinutes: Int, stepId: String? = null): Long =
        userDao.insertSession(
            SessionEntity(
                startedAt = clock.nowMillis(),
                part = scope.part,
                focusTagId = scope.focusTag,
                plannedMinutes = plannedMinutes,
                stepId = stepId,
            ),
        )

    /**
     * Le prochain exercice de la session.
     * @param askedThisSession les exercices déjà servis dans la session (identifiants d'unités)
     * @return null quand tous les exercices du périmètre ont été servis
     */
    suspend fun nextUnit(scope: SessionScope, askedThisSession: List<String>): PickedUnit? {
        val tags = contentDao.tags()
        val tagById = tags.associateBy { it.id }
        val catalog = catalog(scope.part, tags)
        val only = scope.onlyTags
        val candidates = if (only == null) catalog.units else catalog.units.filter { unit -> unit.primaryTags.any { it in only } }

        val pick = picker.pick(
            candidates = candidates,
            focusTag = scope.focusTag,
            skillStates = userDao.skillStates().associate { it.tagId to it.toDomain() },
            questionStates = userDao.questionStates().associate { it.questionId to it.toDomain() },
            askedThisSession = askedThisSession,
            today = clock.today(),
            tagOrder = tags.map { it.id },
        ) ?: return null

        val refs = candidates.first { it.id == pick.unitId }.questions.associateBy { it.id }
        fun picked(entity: QuestionEntity, tagId: String) = PickedQuestion(
            question = entity.toDomain(refs.getValue(entity.id).tags),
            tagId = tagId,
            tagLabelFr = tagById[tagId]?.labelFr ?: tagId,
            tagLabelEn = tagById[tagId]?.labelEn ?: tagId,
            reason = pick.reason,
        )

        if (pick.unitId !in catalog.stimulusIds) {
            // Part 5 : la question compte pour la compétence au titre de laquelle elle est posée.
            val entity = contentDao.question(pick.unitId) ?: return null
            return PickedUnit(entity.id, entity.part, emptyList(), listOf(picked(entity, pick.tagId)), pick.reason)
        }
        // Parts 6 et 7 : le texte entier ; chaque question compte pour sa propre compétence.
        val stimulus = contentDao.stimulus(pick.unitId) ?: return null
        val questions = contentDao.questionsOfStimulus(pick.unitId).filter { it.id in refs }
        if (questions.isEmpty()) return null
        return PickedUnit(
            id = stimulus.id,
            part = stimulus.part,
            documents = stimulus.documents.map { Document(it.genre, it.title, it.text) },
            questions = questions.map { picked(it, refs.getValue(it.id).primaryTag) },
            reason = pick.reason,
        )
    }

    /** Enregistre la réponse et met à jour la boîte de Leitner de chaque compétence de la question. */
    suspend fun submitAnswer(sessionId: Long, unit: PickedUnit, questionIndex: Int, selectedIndex: Int, timeSpentMs: Long): AnswerOutcome {
        val today = clock.today()
        val picked = unit.questions[questionIndex]
        val question = picked.question
        val correct = selectedIndex == question.answerIndex

        val previousSkills = userDao.skillStates().associate { it.tagId to it.toDomain() }
        val updatedSkills = (question.tags + picked.tagId).distinct().map { tag ->
            Leitner.review(previousSkills[tag] ?: Leitner.newSkill(tag, today), correct, today)
        }
        val previousQuestion = userDao.questionState(question.id)?.toDomain()
        // Un texte revenu pour contrôle a fait son tour en entier : voir UnitPicker.
        val controlReturn = (unit.isStimulus && unit.reason == PickReason.RETRY) ||
            (previousQuestion != null && QuestionReview.isRetryDue(previousQuestion, today))
        val questionState = QuestionReview.afterAnswer(previousQuestion, question.id, correct, today, controlReturn)

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
                reason = unit.reason.name,
                boxBefore = boxBefore,
                boxAfter = boxAfter,
                stimulusId = question.stimulusId,
            ),
            skillStates = updatedSkills.map { it.toEntity() },
            questionState = questionState.toEntity(),
        )
        return AnswerOutcome(correct, boxBefore, boxAfter)
    }

    /**
     * Termine la session. Si elle jouait une étape du chemin, enregistre ce passage dans la
     * progression de l'étape.
     * @param exercisesCompleted vrai si tous les exercices prévus par l'étape ont été faits
     */
    suspend fun finishSession(sessionId: Long, exercisesCompleted: Boolean = false) {
        val session = userDao.session(sessionId) ?: return
        if (session.endedAt != null) return
        userDao.finishSession(sessionId, clock.nowMillis(), exercisesCompleted)
        val stepId = session.stepId ?: return
        if (session.questionCount == 0) return
        val run = LessonRun(session.correctCount, session.questionCount, exercisesCompleted)
        val previous = userDao.lessonProgress(stepId)?.toDomain()
        userDao.upsertLessonProgress(LessonPath.afterRun(previous, stepId, run, clock.today()).toEntity())
    }

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
        val lesson = session.stepId?.let { stepId ->
            val path = path()
            val view = path.steps.firstOrNull { it.step.id == stepId } ?: return@let null
            val run = LessonRun(session.correctCount, session.questionCount, session.exercisesCompleted)
            LessonOutcome(
                stepId = stepId,
                kind = view.step.kind,
                titleFr = view.titleFr,
                titleEn = view.titleEn,
                finished = run.finished,
                passed = LessonPath.isPassed(run),
                requiredCorrect = LessonPath.requiredCorrect(run.answered),
                nextStepId = path.next?.step?.id,
            )
        }
        return SessionSummary(
            questionCount = session.questionCount,
            correctCount = session.correctCount,
            durationMs = (session.endedAt ?: clock.nowMillis()) - session.startedAt,
            skills = skills.sortedBy { tags[it.tagId]?.sortOrder ?: Int.MAX_VALUE },
            lesson = lesson,
        )
    }

    // ───────────────────────────── Chemin de leçons ─────────────────────────────

    /** Le chemin et l'état de chaque étape, mis à jour dès qu'une étape est jouée. */
    fun observePath(): Flow<PathOverview> = flow {
        val units = contentDao.pathUnits()
        val steps = contentDao.pathSteps()
        val tags = contentDao.tags().associateBy { it.id }
        emitAll(userDao.observeLessonProgress().map { overview(units, steps, tags, it) })
    }

    suspend fun path(): PathOverview = overview(
        contentDao.pathUnits(),
        contentDao.pathSteps(),
        contentDao.tags().associateBy { it.id },
        userDao.lessonProgress(),
    )

    private fun overview(
        units: List<PathUnitEntity>,
        steps: List<PathStepEntity>,
        tags: Map<String, TagEntity>,
        progress: List<LessonProgressEntity>,
    ): PathOverview {
        val byStep = progress.associate { it.stepId to it.toDomain() }
        val completed = byStep.values.filter { it.isCompleted }.map { it.stepId }.toSet()
        val statuses = steps.map { it.id }.zip(LessonPath.statuses(steps.map { it.id }, completed)).toMap()
        return PathOverview(
            units.map { unit ->
                PathUnitView(
                    id = unit.id,
                    titleFr = unit.titleFr,
                    titleEn = unit.titleEn,
                    steps = steps.filter { it.unitId == unit.id }.map { entity ->
                        val step = entity.toDomain()
                        val tag = tags[step.tags.first()]
                        PathStepView(
                            step = step,
                            status = statuses.getValue(step.id),
                            progress = byStep[step.id],
                            titleFr = if (step.kind == StepKind.REVIEW) "Bilan de l'unité" else tag?.labelFr ?: step.id,
                            titleEn = if (step.kind == StepKind.REVIEW) "Unit review" else tag?.labelEn ?: step.id,
                        )
                    },
                )
            },
        )
    }

    /** Le périmètre des exercices d'une étape : son tag pour une leçon, tous ceux de l'unité pour un bilan. */
    fun scopeOf(step: PathStep): SessionScope = when (step.kind) {
        StepKind.LESSON -> SessionScope(step.part, focusTag = step.tags.first())
        StepKind.REVIEW -> SessionScope(step.part, onlyTags = step.tags.toSet())
    }

    // ───────────────────────────── Fiches de cours ─────────────────────────────

    suspend fun lesson(tagId: String): LessonDto? = contentDao.lesson(tagId)?.content

    /** Les compétences qui ont une fiche. */
    suspend fun lessonTagIds(): Set<String> = contentDao.lessonTagIds().toSet()

    /** Toutes les fiches, dans l'ordre de la taxonomie. */
    suspend fun lessonList(): List<LessonListItem> {
        val lessons = contentDao.lessons().associateBy { it.tagId }
        return contentDao.tags().mapNotNull { tag ->
            val content = lessons[tag.id]?.content ?: return@mapNotNull null
            LessonListItem(tag.id, tag.groupName, content.fr.title, content.en.title, content.fr.summary, content.en.summary)
        }
    }

    companion object {
        const val POOL_TRAINING = "training"

        /** « Toutes les parts » dans un périmètre de session. */
        const val ALL_PARTS = 0
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
    stimulusId = stimulusId,
    evidence = evidence,
)

fun PathStepEntity.toDomain() = PathStep(
    id = id,
    unitId = unitId,
    kind = if (kind == "review") StepKind.REVIEW else StepKind.LESSON,
    tags = tags,
    part = part,
    exerciseCount = exerciseCount,
)
