package fr.toeic900.domain

import kotlin.math.roundToInt
import kotlin.math.sqrt

/**
 * Score TOEIC estimé d'une section (ici le Reading), de 5 à 495.
 *
 * Tout est documenté dans docs/score-estime.md. En bref :
 *  1. Une table de conversion MAISON va du taux de bonnes réponses au score de la section.
 *     ETS ne publie pas de table fixe (la conversion varie d'un test à l'autre) : celle-ci est une
 *     approximation lissée, et nos questions ne sont pas calibrées sur de vrais candidats.
 *  2. Les tests blancs pèsent 80 %, les plus récents davantage ; l'entraînement des 30 derniers
 *     jours pèse 20 %. Sans test blanc, l'estimation repose sur l'entraînement seul et elle est
 *     marquée « provisoire ».
 *  3. Une fourchette accompagne toujours le score : l'incertitude statistique (peu de questions =
 *     fourchette large) plus une marge fixe, parce que la table n'est pas calibrée.
 */
object ScoreEstimator {
    const val MIN_SCORE = 5
    const val MAX_SCORE = 495

    /** Poids des tests blancs quand il y en a, et de l'entraînement. */
    const val MOCK_WEIGHT = 0.8
    const val TRAINING_WEIGHT = 0.2

    /** Chaque test blanc plus ancien compte 0,6 fois le suivant. */
    const val RECENCY_DECAY = 0.6

    /** En dessous, l'entraînement ne suffit pas à estimer quoi que ce soit. */
    const val MIN_TRAINING_ANSWERS = 30

    /** Une réponse d'entraînement vaut un quart de question de test blanc pour la fourchette :
     *  sans chronomètre serré et avec la correction sous les yeux, elle renseigne moins. */
    const val TRAINING_INFORMATION = 0.25

    /** Marge fixe (en points) pour une table non calibrée, et marge en plus sans test blanc. */
    const val CALIBRATION_MARGIN = 20
    const val PROVISIONAL_MARGIN = 30

    /** z pour une fourchette à environ 90 %. */
    private const val Z = 1.645

    /**
     * La table maison : (taux de bonnes réponses, score). Interpolation linéaire entre les points.
     * Repères : 70 % de bonnes réponses donne 320 et 90 % donne 440
     * (le Reading qu'il faut pour un total de 900 avec un Listening à 460).
     */
    val READING_TABLE: List<Pair<Double, Int>> = listOf(
        0.00 to 5,
        0.10 to 5,
        0.20 to 55,
        0.30 to 105,
        0.40 to 155,
        0.50 to 210,
        0.60 to 265,
        0.70 to 320,
        0.80 to 380,
        0.90 to 440,
        0.95 to 470,
        0.97 to 485,
        1.00 to 495,
    )

    /** Score (non arrondi) pour un taux de bonnes réponses entre 0 et 1. */
    fun convert(rate: Double, table: List<Pair<Double, Int>> = READING_TABLE): Double {
        val r = rate.coerceIn(0.0, 1.0)
        val upper = table.indexOfFirst { it.first >= r }
        if (upper <= 0) return table.first().second.toDouble()
        val (x0, y0) = table[upper - 1]
        val (x1, y1) = table[upper]
        return y0 + (y1 - y0) * (r - x0) / (x1 - x0)
    }

    /** Arrondi au multiple de 5 le plus proche, comme les vrais scores, et borné à 5-495. */
    fun roundScore(score: Double): Int = ((score / 5).roundToInt() * 5).coerceIn(MIN_SCORE, MAX_SCORE)

    fun estimate(mocks: List<MockScore>, training: Tally): ScoreEstimate? {
        val hasTraining = training.total >= MIN_TRAINING_ANSWERS
        if (mocks.isEmpty() && !hasTraining) return null

        // Tests blancs : du plus récent au plus ancien, poids 1 ; 0,6 ; 0,36…
        val ordered = mocks.sortedWith(compareByDescending<MockScore> { it.day }.thenByDescending { it.order })
        var weightSum = 0.0
        var mockRate = 0.0
        var mockInformation = 0.0
        ordered.forEachIndexed { rank, mock ->
            val w = Math.pow(RECENCY_DECAY, rank.toDouble())
            weightSum += w
            mockRate += w * mock.tally.rate
            mockInformation += w * mock.tally.total
        }
        if (weightSum > 0) mockRate /= weightSum

        val rate: Double
        val information: Double
        when {
            ordered.isEmpty() -> {
                rate = training.rate
                information = training.total * TRAINING_INFORMATION
            }
            hasTraining -> {
                rate = MOCK_WEIGHT * mockRate + TRAINING_WEIGHT * training.rate
                information = mockInformation + training.total * TRAINING_INFORMATION
            }
            else -> {
                rate = mockRate
                information = mockInformation
            }
        }

        val provisional = ordered.isEmpty()
        val se = sqrt(rate * (1 - rate) / information.coerceAtLeast(1.0)).coerceAtLeast(0.02)
        val low = convert(rate - Z * se)
        val high = convert(rate + Z * se)
        val margin = CALIBRATION_MARGIN + if (provisional) PROVISIONAL_MARGIN else 0
        val center = convert(rate)
        return ScoreEstimate(
            score = roundScore(center),
            low = roundScore(low - margin),
            high = roundScore(high + margin),
            provisional = provisional,
            mockCount = ordered.size,
            trainingAnswers = training.total,
        )
    }
}

/** Un nombre de réponses et de bonnes réponses. */
data class Tally(val total: Int, val correct: Int) {
    init {
        require(total >= 0 && correct in 0..total) { "comptes incohérents : $correct / $total" }
    }

    val rate: Double get() = if (total == 0) 0.0 else correct.toDouble() / total

    operator fun plus(other: Tally) = Tally(total + other.total, correct + other.correct)

    companion object {
        val EMPTY = Tally(0, 0)
    }
}

/** Le résultat d'un test blanc d'une section. [order] départage deux tests du même jour. */
data class MockScore(val day: Long, val tally: Tally, val order: Long = 0)

data class ScoreEstimate(
    val score: Int,
    val low: Int,
    val high: Int,
    /** Vrai sans aucun test blanc : l'entraînement seul ne suffit pas à une vraie estimation. */
    val provisional: Boolean,
    val mockCount: Int,
    val trainingAnswers: Int,
)
