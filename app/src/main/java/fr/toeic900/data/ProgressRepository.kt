package fr.toeic900.data

import fr.toeic900.data.content.ContentDao
import fr.toeic900.data.settings.ProgressSettings
import fr.toeic900.data.settings.SettingsRepository
import fr.toeic900.data.user.AnswerRow
import fr.toeic900.data.user.BadgeUnlockEntity
import fr.toeic900.data.user.GoalDayEntity
import fr.toeic900.data.user.MockResultEntity
import fr.toeic900.data.user.UserDao
import fr.toeic900.domain.AnswerRecord
import fr.toeic900.domain.Badge
import fr.toeic900.domain.BadgeSnapshot
import fr.toeic900.domain.Badges
import fr.toeic900.domain.Clock
import fr.toeic900.domain.DailyGoal
import fr.toeic900.domain.DayActivity
import fr.toeic900.domain.Leitner
import fr.toeic900.domain.Level
import fr.toeic900.domain.MockScore
import fr.toeic900.domain.ScoreEstimate
import fr.toeic900.domain.ScoreEstimator
import fr.toeic900.domain.Stats
import fr.toeic900.domain.StreakState
import fr.toeic900.domain.Tally
import fr.toeic900.domain.WeeklyStreak
import fr.toeic900.domain.Xp
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow

/** Un test blanc terminé, tel que l'écran Progrès l'affiche. */
data class MockSummary(val id: Long, val day: Long, val tally: Tally, val score: Int, val timedOut: Boolean)

data class TagStat(val tagId: String, val labelFr: String, val tally: Tally, val box: Int?)

data class BadgeView(val badge: Badge, val unlockedDay: Long?, val progress: Int) {
    val unlocked: Boolean get() = unlockedDay != null
}

data class ProgressOverview(
    val today: Long = 0,
    val level: Level = Xp.level(0),
    val goal: DailyGoal = DailyGoal.DEFAULT,
    val todayProgress: Int = 0,
    val streak: StreakState = StreakState(0, 0, WeeklyStreak.DEFAULT_THRESHOLD, false, 0),
    val reading: ScoreEstimate? = null,
    val mocks: List<MockSummary> = emptyList(),
    /** Entraînement seul (hors tests blancs), toutes périodes. */
    val parts: Map<Int, Tally> = emptyMap(),
    /** Compétences travaillées, de la plus faible à la plus solide. */
    val tags: List<TagStat> = emptyList(),
    /** Les 28 derniers jours, du plus ancien à aujourd'hui (tests blancs compris). */
    val lastDays: List<DayActivity> = emptyList(),
    val badges: List<BadgeView> = emptyList(),
    /** Badges débloqués que l'utilisateur n'a pas encore vus annoncer. */
    val unseenBadges: List<Badge> = emptyList(),
    val totalAnswers: Int = 0,
    /** Ce que les badges mesurent, pour savoir lesquels viennent d'être mérités. */
    val badgeSnapshot: BadgeSnapshot = BadgeSnapshot(),
) {
    val goalMetToday: Boolean get() = todayProgress >= goal.target
}

/** Tout ce que l'écran Progrès, l'accueil et les badges lisent. */
class ProgressRepository(
    private val contentDao: ContentDao,
    private val userDao: UserDao,
    private val settings: SettingsRepository,
    private val clock: Clock,
) {
    private class Static(val labels: Map<String, String>, val order: Map<String, Int>, val skillCount: Int, val stepCount: Int)

    private suspend fun static(): Static {
        val tags = contentDao.tags()
        return Static(
            labels = tags.associate { it.id to it.labelFr },
            order = tags.associate { it.id to it.sortOrder },
            skillCount = tags.size,
            stepCount = contentDao.pathSteps().size,
        )
    }

    private class Inputs(
        val answers: List<AnswerRow>,
        val mockAnswers: List<AnswerRow>,
        val mocks: List<MockResultEntity>,
        val goalDays: List<Long>,
        val unlocks: List<BadgeUnlockEntity>,
        val stepsCompleted: Int,
        val skillBoxes: Map<String, Int>,
        val settings: ProgressSettings,
    )

    fun observe(): Flow<ProgressOverview> = flow {
        val static = static()
        val base = combine(
            userDao.observeAnswerRows(),
            userDao.observeMockAnswerRows(),
            userDao.observeCompletedMocks(),
            userDao.observeGoalDays(),
            userDao.observeBadgeUnlocks(),
        ) { answers, mockAnswers, mocks, goalDays, unlocks -> listOf(answers, mockAnswers, mocks, goalDays, unlocks) }
        val extra = combine(
            userDao.observeLessonProgress(),
            userDao.observeSkillStates(),
            settings.progress,
        ) { steps, skills, s -> Triple(steps.count { it.completedDay != null }, skills.associate { it.tagId to it.box }, s) }
        emitAll(
            combine(base, extra) { b, (steps, boxes, s) ->
                @Suppress("UNCHECKED_CAST")
                build(
                    static,
                    Inputs(
                        b[0] as List<AnswerRow>, b[1] as List<AnswerRow>, b[2] as List<MockResultEntity>,
                        b[3] as List<Long>, b[4] as List<BadgeUnlockEntity>, steps, boxes, s,
                    ),
                )
            },
        )
    }

    private suspend fun snapshot(): ProgressOverview = build(
        static(),
        Inputs(
            userDao.answerRows(), userDao.mockAnswerRows(), userDao.completedMocks(), userDao.goalDays(),
            userDao.badgeUnlocks(), userDao.lessonProgress().count { it.completedDay != null },
            userDao.skillStates().associate { it.tagId to it.box }, settings.progress.first(),
        ),
    )

    /**
     * À appeler après une réponse, une session ou un test blanc : enregistre le jour si l'objectif
     * vient d'être atteint, puis les badges nouvellement mérités. Renvoie ces badges.
     */
    suspend fun checkAchievements(): List<Badge> {
        var overview = snapshot()
        if (overview.goalMetToday && overview.today !in userDao.goalDays()) {
            userDao.insertGoalDay(GoalDayEntity(overview.today))
            overview = snapshot()
        }
        val newOnes = overview.badges.filter { !it.unlocked && it.badge.isReached(overview.badgeSnapshot) }.map { it.badge }
        if (newOnes.isNotEmpty()) {
            userDao.insertBadgeUnlocks(newOnes.map { BadgeUnlockEntity(it.id, overview.today) })
        }
        return newOnes
    }

    suspend fun markBadgesSeen() = userDao.markBadgesSeen()

    private fun build(static: Static, inputs: Inputs): ProgressOverview {
        val today = clock.today()
        val training = inputs.answers.map { AnswerRecord(it.day, it.questionId, it.tagId, it.correct, it.timeSpentMs, training = true) }
        val mockRecords = inputs.mockAnswers.map { AnswerRecord(it.day, it.questionId, it.tagId, it.correct, it.timeSpentMs, training = false) }
        val all = training + mockRecords
        val activity = Stats.days(all)
        val goal = inputs.settings.goal
        val goalDays = Stats.goalDays(activity, goal, inputs.goalDays.toSet())
        val streak = WeeklyStreak.compute(goalDays, today, inputs.settings.streakThreshold)

        val mocks = inputs.mocks.map {
            val tally = Tally(it.questionCount, it.correctCount)
            MockSummary(it.id, it.day, tally, ScoreEstimator.roundScore(ScoreEstimator.convert(tally.rate)), it.timedOut)
        }
        val reading = ScoreEstimator.estimate(
            mocks.map { MockScore(it.day, it.tally, order = it.id) },
            Stats.recentTraining(training, today),
        )

        val correct = all.count { it.correct }
        val xp = Xp.total(
            correct = correct,
            wrong = all.size - correct,
            stepsCompleted = inputs.stepsCompleted,
            mocksCompleted = mocks.size,
            goalDays = goalDays.count { it <= today },
        )

        val tags = Stats.byTag(training).map { (tag, tally) ->
            TagStat(tag, static.labels[tag] ?: tag, tally, inputs.skillBoxes[tag])
        }.sortedWith(compareBy<TagStat> { it.tally.rate }.thenBy { static.order[it.tagId] ?: Int.MAX_VALUE })

        val snapshot = BadgeSnapshot(
            reading = reading,
            bestStreak = streak.best,
            goalDays = goalDays.count { it <= today },
            totalAnswers = all.size,
            masteredSkills = inputs.skillBoxes.values.count { it >= Leitner.LAST_BOX },
            skillCount = static.skillCount,
            mocksCompleted = mocks.size,
            bestMockPercent = mocks.maxOfOrNull { (it.tally.rate * 100).toInt() } ?: 0,
            stepsCompleted = inputs.stepsCompleted,
            stepCount = static.stepCount,
        )
        val unlocks = inputs.unlocks.associateBy { it.badgeId }
        val badges = Badges.ALL.map { BadgeView(it, unlocks[it.id]?.unlockedDay, it.progress(snapshot)) }

        return ProgressOverview(
            today = today,
            level = Xp.level(xp),
            goal = goal,
            todayProgress = goal.progress(activity[today]),
            streak = streak,
            reading = reading,
            mocks = mocks,
            parts = Stats.byPart(training),
            tags = tags,
            lastDays = Stats.lastDays(activity, today, LAST_DAYS),
            badges = badges,
            unseenBadges = inputs.unlocks.filter { !it.seen }.mapNotNull { Badges.byId[it.badgeId] },
            totalAnswers = all.size,
            badgeSnapshot = snapshot,
        )
    }

    companion object {
        const val LAST_DAYS = 28
    }
}
