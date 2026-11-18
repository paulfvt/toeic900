package fr.toeic900.domain

/** Tout ce dont les badges ont besoin, calculé à partir de l'historique. */
data class BadgeSnapshot(
    val reading: ScoreEstimate? = null,
    /** Listening : pas d'estimation avant la Phase 4. */
    val listening: ScoreEstimate? = null,
    val bestStreak: Int = 0,
    val goalDays: Int = 0,
    val totalAnswers: Int = 0,
    /** Compétences en boîte 5 de Leitner. */
    val masteredSkills: Int = 0,
    val skillCount: Int = 24,
    val mocksCompleted: Int = 0,
    /** Meilleur taux de bonnes réponses à un demi-test, en pourcentage. */
    val bestMockPercent: Int = 0,
    val stepsCompleted: Int = 0,
    val stepCount: Int = 31,
)

enum class BadgeCategory(val labelFr: String) {
    SCORE("Score estimé"),
    REGULARITY("Régularité"),
    MASTERY("Maîtrise"),
    VOLUME("Entraînement"),
    TESTS("Tests blancs"),
}

/**
 * Un badge se débloque quand [progress] atteint [target]. Une fois débloqué, il est enregistré
 * dans la base utilisateur et le reste, même si la condition n'est plus remplie (score en baisse).
 */
data class Badge(
    val id: String,
    val category: BadgeCategory,
    val titleFr: String,
    val descriptionFr: String,
    val target: Int,
    val progress: (BadgeSnapshot) -> Int,
) {
    fun isReached(snapshot: BadgeSnapshot): Boolean = progress(snapshot) >= target
}

object Badges {
    /** Un score ne compte pour un badge qu'avec au moins un test blanc (pas une estimation provisoire). */
    private fun readingScore(s: BadgeSnapshot): Int = s.reading?.takeUnless { it.provisional }?.score ?: 0

    private fun totalScore(s: BadgeSnapshot): Int {
        val r = s.reading?.takeUnless { it.provisional } ?: return 0
        val l = s.listening?.takeUnless { it.provisional } ?: return 0
        return r.score + l.score
    }

    val ALL: List<Badge> = buildList {
        for (target in listOf(750, 800, 850, 900)) {
            add(Badge("score-$target", BadgeCategory.SCORE, "Cap des $target",
                "Score total estimé d'au moins $target (Listening estimé à partir de la Phase 4).", target, ::totalScore))
        }
        for (target in listOf(375, 400, 425, 450)) {
            add(Badge("reading-$target", BadgeCategory.SCORE, "Reading $target",
                "Score Reading estimé d'au moins $target, avec au moins un demi-test.", target, ::readingScore))
        }
        add(Badge("goal-first", BadgeCategory.REGULARITY, "Premier objectif", "Atteindre l'objectif quotidien une première fois.", 1) { it.goalDays })
        add(Badge("goal-30", BadgeCategory.REGULARITY, "Trente jours", "Atteindre l'objectif quotidien 30 jours.", 30) { it.goalDays })
        for ((target, title) in listOf(1 to "Semaine validée", 4 to "Un mois d'affilée", 8 to "Deux mois d'affilée")) {
            add(Badge("streak-$target", BadgeCategory.REGULARITY, title,
                if (target == 1) "Valider une semaine." else "Valider $target semaines d'affilée.", target) { it.bestStreak })
        }
        add(Badge("mastery-1", BadgeCategory.MASTERY, "Première maîtrise", "Amener une compétence en boîte 5.", 1) { it.masteredSkills })
        add(Badge("mastery-5", BadgeCategory.MASTERY, "Cinq maîtrises", "Amener 5 compétences en boîte 5.", 5) { it.masteredSkills })
        add(Badge("mastery-all", BadgeCategory.MASTERY, "Tout maîtrisé", "Amener toutes les compétences en boîte 5.", 1) {
            if (it.skillCount > 0 && it.masteredSkills >= it.skillCount) 1 else 0
        })
        add(Badge("path-half", BadgeCategory.MASTERY, "Mi-chemin", "Réussir la moitié des étapes du chemin.", 1) {
            if (it.stepCount > 0 && it.stepsCompleted * 2 >= it.stepCount) 1 else 0
        })
        add(Badge("path-done", BadgeCategory.MASTERY, "Au bout du chemin", "Réussir toutes les étapes du chemin.", 1) {
            if (it.stepCount > 0 && it.stepsCompleted >= it.stepCount) 1 else 0
        })
        for (target in listOf(100, 500, 1000, 2000)) {
            add(Badge("answers-$target", BadgeCategory.VOLUME, "$target réponses", "Répondre à $target questions.", target) { it.totalAnswers })
        }
        add(Badge("mock-first", BadgeCategory.TESTS, "Premier demi-test", "Terminer un demi-test Reading.", 1) { it.mocksCompleted })
        add(Badge("mock-80", BadgeCategory.TESTS, "80 % au demi-test", "Obtenir au moins 80 % de bonnes réponses à un demi-test.", 80) { it.bestMockPercent })
        add(Badge("mock-90", BadgeCategory.TESTS, "90 % au demi-test", "Obtenir au moins 90 % de bonnes réponses à un demi-test.", 90) { it.bestMockPercent })
    }

    val byId: Map<String, Badge> = ALL.associateBy { it.id }

    /** Les badges atteints maintenant et pas encore enregistrés. */
    fun newlyUnlocked(snapshot: BadgeSnapshot, alreadyUnlocked: Set<String>): List<Badge> =
        ALL.filter { it.id !in alreadyUnlocked && it.isReached(snapshot) }
}
