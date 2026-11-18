package fr.toeic900.domain

/*
 * Motivation : XP et niveaux, objectif quotidien, série hebdomadaire.
 * Tout est recalculé à partir de l'historique (réponses, étapes, tests blancs) : rien à stocker,
 * et une règle corrigée s'applique aussi au passé.
 */

/** Ce qui s'est passé un jour donné. */
data class DayActivity(
    val day: Long,
    val answers: Int,
    val correct: Int,
    /** Temps passé sur les questions, en millisecondes. */
    val activeMs: Long,
)

enum class GoalType { QUESTIONS, MINUTES }

data class DailyGoal(val type: GoalType, val target: Int) {
    init {
        require(target > 0) { "objectif nul" }
    }

    fun progress(activity: DayActivity?): Int = when (type) {
        GoalType.QUESTIONS -> activity?.answers ?: 0
        GoalType.MINUTES -> ((activity?.activeMs ?: 0L) / 60_000L).toInt()
    }

    fun isMet(activity: DayActivity?): Boolean = progress(activity) >= target

    companion object {
        val DEFAULT = DailyGoal(GoalType.QUESTIONS, 20)
        val QUESTION_CHOICES = listOf(10, 20, 30, 50)
        val MINUTE_CHOICES = listOf(10, 15, 20, 30)
    }
}

object Xp {
    const val CORRECT = 10
    /** L'effort compte aussi : une erreur rapporte un peu. */
    const val WRONG = 2
    const val STEP_COMPLETED = 20
    const val MOCK_COMPLETED = 50
    const val DAILY_GOAL = 15

    fun total(
        correct: Int,
        wrong: Int,
        stepsCompleted: Int,
        mocksCompleted: Int,
        goalDays: Int,
    ): Int = correct * CORRECT + wrong * WRONG + stepsCompleted * STEP_COMPLETED +
        mocksCompleted * MOCK_COMPLETED + goalDays * DAILY_GOAL

    /** XP qu'il faut avoir cumulée pour atteindre un niveau : 0, 100, 300, 600, 1 000… (50 n (n - 1)). */
    fun xpForLevel(level: Int): Int {
        require(level >= 1)
        return 50 * level * (level - 1)
    }

    fun level(xp: Int): Level {
        require(xp >= 0)
        var level = 1
        while (xpForLevel(level + 1) <= xp) level++
        val start = xpForLevel(level)
        return Level(level, xp, xp - start, xpForLevel(level + 1) - start)
    }
}

data class Level(val number: Int, val totalXp: Int, val xpInLevel: Int, val xpToNext: Int) {
    val fraction: Float get() = xpInLevel.toFloat() / xpToNext
}

/**
 * Série hebdomadaire : une semaine (du lundi au dimanche) est validée si l'objectif quotidien a été
 * atteint au moins [WeeklyStreak.threshold] jours. La série compte les semaines validées consécutives
 * jusqu'à la dernière semaine terminée ; la semaine en cours s'y ajoute dès qu'elle est validée, mais
 * ne casse jamais la série tant qu'elle n'est pas finie.
 */
object Weeks {
    /** Lundi de la semaine du jour (epoch day). Le 1er janvier 1970, jour 0, était un jeudi. */
    fun monday(day: Long): Long = day - Math.floorMod(day + 3, 7L)

    /** 0 = lundi … 6 = dimanche. */
    fun dayOfWeek(day: Long): Int = Math.floorMod(day + 3, 7L).toInt()
}

data class StreakState(
    /** Semaines validées d'affilée. */
    val weeks: Int,
    /** Jours où l'objectif est atteint dans la semaine en cours. */
    val daysThisWeek: Int,
    val threshold: Int,
    val currentWeekValidated: Boolean,
    /** Meilleure série jamais atteinte. */
    val best: Int,
) {
    /** Jours restants dans la semaine en cours, aujourd'hui compris. */
    fun daysLeft(today: Long): Int = 7 - Weeks.dayOfWeek(today)
}

object WeeklyStreak {
    const val DEFAULT_THRESHOLD = 5
    val THRESHOLD_RANGE = 1..7

    fun compute(goalDays: Set<Long>, today: Long, threshold: Int = DEFAULT_THRESHOLD): StreakState {
        require(threshold in THRESHOLD_RANGE)
        val validated = goalDays.filter { it <= today }.groupingBy(Weeks::monday).eachCount()
            .filterValues { it >= threshold }.keys
        val thisMonday = Weeks.monday(today)
        val daysThisWeek = goalDays.count { it in thisMonday..today }
        val currentValidated = thisMonday in validated

        var weeks = 0
        var monday = thisMonday - 7
        while (monday in validated) {
            weeks++
            monday -= 7
        }
        if (currentValidated) weeks++

        // Meilleure série : la plus longue suite de lundis validés consécutifs.
        var best = 0
        var run = 0
        var previous: Long? = null
        for (m in validated.sorted()) {
            run = if (previous != null && m - previous == 7L) run + 1 else 1
            best = maxOf(best, run)
            previous = m
        }
        return StreakState(weeks, daysThisWeek, threshold, currentValidated, maxOf(best, weeks))
    }
}
