package fr.toeic900.ui.mock

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import fr.toeic900.data.MockQuestion
import fr.toeic900.data.MockResultView
import fr.toeic900.data.MockTestInfo
import fr.toeic900.data.settings.ExplanationLanguage
import fr.toeic900.ui.appContainer
import fr.toeic900.ui.components.ButtonStyle
import fr.toeic900.ui.components.CardStyle
import fr.toeic900.ui.components.Callout
import fr.toeic900.ui.components.ElanButton
import fr.toeic900.ui.components.ElanCard
import fr.toeic900.ui.components.ElanChip
import fr.toeic900.ui.components.ElanProgress
import fr.toeic900.ui.components.ElanTopBar
import fr.toeic900.ui.components.LabelSmall
import fr.toeic900.ui.components.OptionRow
import fr.toeic900.ui.components.OptionState
import fr.toeic900.ui.progress.formatDay
import fr.toeic900.ui.progress.percent
import fr.toeic900.ui.theme.ElanDimens
import fr.toeic900.ui.theme.ElanIcons
import fr.toeic900.ui.theme.ElanTheme
import fr.toeic900.ui.training.DocumentCard
import fr.toeic900.ui.training.documentText

private const val LETTERS = "ABCD"

/** Avant le demi-test : ce qu'il mesure, comment il se déroule, et les passages précédents. */
@Composable
fun MockIntroScreen(onBack: () -> Unit, onStart: (testId: String) -> Unit, onOpenResult: (Long) -> Unit) {
    val container = appContainer()
    val tests by produceState<List<MockTestInfo>?>(null) { value = container.mock.tests() }
    val c = ElanTheme.colors
    val type = ElanTheme.type
    Column(Modifier.fillMaxSize()) {
        ElanTopBar(title = "Demi-test Reading", navigationIcon = ElanIcons.Back, navigationDescription = "Retour", onNavigate = onBack)
        val list = tests ?: return@Column
        Column(
            modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = ElanDimens.gutter).padding(bottom = ElanDimens.gapMajor),
            verticalArrangement = Arrangement.spacedBy(ElanDimens.gapSection),
        ) {
            if (list.isEmpty()) {
                Text("Aucun demi-test dans le contenu.", style = type.bodyL, color = c.muted)
            }
            list.forEach { t ->
                ElanCard(style = CardStyle.Filled) {
                    LabelSmall(t.titleFr, color = c.onPrimaryContainer)
                    Spacer(Modifier.height(8.dp))
                    Text("${t.questionCount} questions · ${t.minutes} min", style = type.headline, color = c.onPrimaryContainer)
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "La moitié d'une section Reading, aux mêmes proportions : Part 5, puis Part 6, puis Part 7. " +
                            "Tu avances et reviens librement, sans correction avant la fin. Le chronomètre ne s'arrête pas, " +
                            "même si tu quittes l'app, et la copie est rendue d'office quand le temps est écoulé.",
                        style = type.bodyM,
                        color = c.onPrimaryContainer,
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        if (t.completed.isEmpty()) {
                            "Ce test ne mesure vraiment qu'au premier passage : choisis un moment où tu as ${t.minutes} minutes au calme."
                        } else {
                            "Déjà passé : un nouveau passage compte dans le score, mais tu connais maintenant une partie des réponses."
                        },
                        style = type.bodyM,
                        color = c.onPrimaryContainer,
                    )
                    Spacer(Modifier.height(12.dp))
                    ElanButton(if (t.completed.isEmpty()) "Commencer" else "Repasser", { onStart(t.id) }, Modifier.fillMaxWidth())
                }
                t.completed.asReversed().forEach { r ->
                    ElanCard(onClick = { onOpenResult(r.id) }) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text("Passage du ${formatDay(r.day)}", style = type.titleM, color = c.onSurface)
                                Text("${r.correctCount} / ${r.questionCount}${if (r.timedOut) " · temps écoulé" else ""}", style = type.bodyM, color = c.muted)
                            }
                            Text("Voir", style = type.labelL, color = c.primaryInk)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun MockResultScreen(mockId: Long, onClose: () -> Unit, onOpenLesson: (String) -> Unit) {
    val container = appContainer()
    val view by produceState<MockResultView?>(null, mockId) { value = container.mock.result(mockId) }
    val language by container.settings.explanationLanguage.collectAsStateWithLifecycle(ExplanationLanguage.DEFAULT)
    val english = language == ExplanationLanguage.ENGLISH
    val c = ElanTheme.colors
    val type = ElanTheme.type
    Column(Modifier.fillMaxSize()) {
        ElanTopBar(title = "Résultats", navigationIcon = ElanIcons.Close, navigationDescription = "Fermer", onNavigate = onClose)
        val v = view ?: return@Column
        LaunchedEffect(Unit) { container.progress.markBadgesSeen() }
        Column(
            modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = ElanDimens.gutter).padding(bottom = ElanDimens.gapMajor),
            verticalArrangement = Arrangement.spacedBy(ElanDimens.gapSection),
        ) {
            val correct = v.test.questions.count(v::isCorrect)
            ElanCard(style = CardStyle.Filled) {
                LabelSmall("${v.test.titleFr} · ${formatDay(v.result.day)}", color = c.onPrimaryContainer)
                Spacer(Modifier.height(8.dp))
                Text("$correct / ${v.test.questions.size} bonnes réponses", style = type.headline, color = c.onPrimaryContainer)
                Spacer(Modifier.height(4.dp))
                Text("Score Reading estimé pour ce test : ≈ ${v.estimate.score}, fourchette ${v.estimate.low} à ${v.estimate.high}", style = type.titleM, color = c.onPrimaryContainer)
                Spacer(Modifier.height(4.dp))
                val time = if (v.result.timedOut) "Temps écoulé : la copie a été rendue d'office." else "Rendu en ${formatClock(v.result.elapsedMs)} sur ${v.result.plannedMinutes} min."
                Text(time, style = type.bodyM, color = c.onPrimaryContainer)
                Text("Ce résultat pèse désormais 80 % du score estimé de l'onglet Progrès.", style = type.bodyM, color = c.onPrimaryContainer)
            }

            ElanCard {
                LabelSmall("Par part")
                Spacer(Modifier.height(8.dp))
                v.byPart.forEach { (part, tally) -> ScoreRow("Part $part", "${tally.correct} / ${tally.total}", tally.rate) }
            }

            ElanCard {
                LabelSmall("Par compétence")
                Spacer(Modifier.height(8.dp))
                v.byTag.forEach { (q, tally) ->
                    ScoreRow(if (english) q.tagLabelEn else q.tagLabelFr, "${tally.correct} / ${tally.total}", tally.rate)
                }
            }

            val mistakes = v.mistakes
            LabelSmall(if (mistakes.isEmpty()) "Aucune erreur" else "Revue des erreurs (${mistakes.size})")
            mistakes.forEach { q -> MistakeCard(v, q, english, onOpenLesson) }
            ElanButton("Terminer", onClose, Modifier.fillMaxWidth())
        }
    }
}

@Composable
private fun ScoreRow(label: String, detail: String, rate: Double) {
    val type = ElanTheme.type
    val c = ElanTheme.colors
    Column(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(label, style = type.titleM, color = c.onSurface, modifier = Modifier.weight(1f))
            Text(detail, style = type.bodyM, color = c.muted)
            Spacer(Modifier.width(8.dp))
            Text(percent(rate), style = type.labelL, color = c.onSurface)
        }
        Spacer(Modifier.height(4.dp))
        ElanProgress(rate.toFloat(), Modifier.fillMaxWidth())
    }
}

/** Une erreur : l'énoncé, ta réponse, la bonne, la règle et le piège ; le texte d'origine sur demande. */
@Composable
private fun MistakeCard(v: MockResultView, q: MockQuestion, english: Boolean, onOpenLesson: (String) -> Unit) {
    val c = ElanTheme.colors
    val type = ElanTheme.type
    val question = q.question
    val selected = v.answers[question.id]?.takeIf { it >= 0 }
    val unit = v.test.units.first { u -> u.questions.any { it.question.id == question.id } }
    val inUnit = unit.questions.indexOfFirst { it.question.id == question.id }
    var showText by remember(question.id) { mutableStateOf(false) }
    val explanation = if (english) question.explanationEn else question.explanationFr
    ElanCard {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ElanChip("Part ${question.part}", tonal = true)
            ElanChip("Question ${v.test.questions.indexOf(q) + 1}", tonal = true)
        }
        Spacer(Modifier.height(8.dp))
        val stem = question.stem.ifBlank { "Trou (${inUnit + 1}) du texte" }
        Text(stem, style = type.document, color = c.onSurface)
        Spacer(Modifier.height(8.dp))
        question.options.forEachIndexed { i, option ->
            OptionRow(
                letter = LETTERS[i].toString(),
                text = option,
                state = when (i) {
                    question.answerIndex -> OptionState.Correct
                    selected -> OptionState.Wrong
                    else -> OptionState.Default
                },
                compact = true,
                onClick = null,
            )
            Spacer(Modifier.height(6.dp))
        }
        if (selected == null) Text("Sans réponse.", style = type.bodyM, color = c.muted)
        Spacer(Modifier.height(4.dp))
        Text(explanation.rule, style = type.bodyM, color = c.onSurface)
        selected?.let { explanation.distractorNotes.getOrNull(it) }?.takeIf { it.isNotBlank() }?.let {
            Spacer(Modifier.height(4.dp))
            Text("Ta réponse : $it", style = type.bodyM, color = c.muted)
        }
        Spacer(Modifier.height(8.dp))
        Callout(if (english) "Trap" else "Piège", explanation.trap)
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (unit.documents.isNotEmpty()) {
                ElanButton(if (showText) "Masquer le texte" else "Voir le texte", { showText = !showText }, Modifier.weight(1f), style = ButtonStyle.Outlined)
            }
            ElanButton("Fiche", { onOpenLesson(q.tagId) }, Modifier.weight(1f), style = ButtonStyle.Outlined)
        }
        AnimatedVisibility(showText) {
            Column(Modifier.padding(top = 12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                val questions = unit.questions.map { it.question }
                unit.documents.forEachIndexed { i, doc ->
                    DocumentCard(doc, i, unit.documents.size, documentText(doc, questions, inUnit, corrected = true, c = c))
                }
            }
        }
    }
}
