package fr.toeic900.ui.training

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import fr.toeic900.data.AnswerOutcome
import fr.toeic900.data.PickedQuestion
import fr.toeic900.data.settings.ExplanationLanguage
import fr.toeic900.domain.Leitner
import fr.toeic900.domain.PickReason
import fr.toeic900.domain.SystemClock
import fr.toeic900.ui.appContainer
import fr.toeic900.ui.components.ButtonStyle
import fr.toeic900.ui.components.Callout
import fr.toeic900.ui.components.ElanButton
import fr.toeic900.ui.components.ElanChip
import fr.toeic900.ui.components.ElanIconButton
import fr.toeic900.ui.components.ElanProgress
import fr.toeic900.ui.components.LabelSmall
import fr.toeic900.ui.components.OptionRow
import fr.toeic900.ui.components.OptionState
import fr.toeic900.ui.theme.ElanDimens
import fr.toeic900.ui.theme.ElanIcons
import fr.toeic900.ui.theme.ElanShapes
import fr.toeic900.ui.theme.ElanTheme

private const val BLANK = "-------"
private const val LETTERS = "ABCD"

@Composable
fun TrainingScreen(focusTag: String?, minutes: Int, onFinished: (Long) -> Unit, onAbandon: () -> Unit) {
    val container = appContainer()
    val viewModel = viewModel {
        TrainingViewModel(container.repository, container.settings, SystemClock, focusTag, minutes)
    }
    val state by viewModel.state.collectAsStateWithLifecycle()

    LaunchedEffect(state.finishedSessionId, state.abandoned) {
        state.finishedSessionId?.let(onFinished)
        if (state.abandoned) onAbandon()
    }

    var confirmQuit by remember { mutableStateOf(false) }
    val askToQuit = { if (state.answered == 0) viewModel.quit() else confirmQuit = true }
    BackHandler(onBack = askToQuit)

    if (confirmQuit) {
        AlertDialog(
            onDismissRequest = { confirmQuit = false },
            title = { Text("Terminer la session ?", style = ElanTheme.type.titleL) },
            text = { Text("Tes réponses sont déjà enregistrées. Tu verras le bilan de la session.", style = ElanTheme.type.bodyL) },
            confirmButton = {
                ElanButton("Terminer", { confirmQuit = false; viewModel.quit() }, style = ButtonStyle.Text)
            },
            dismissButton = { ElanButton("Continuer", { confirmQuit = false }, style = ButtonStyle.Text) },
            containerColor = ElanTheme.colors.surface,
        )
    }

    Column(Modifier.fillMaxSize()) {
        SessionBar(state, onClose = askToQuit)
        val picked = state.picked
        if (picked != null) {
            QuestionArea(
                state = state,
                picked = picked,
                onSelect = viewModel::select,
                onValidate = viewModel::validate,
                onNext = viewModel::next,
            )
        }
    }
}

/** Barre du haut : quitter, avancement du temps, temps restant ou nombre de questions. */
@Composable
private fun SessionBar(state: TrainingUiState, onClose: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().height(ElanDimens.appBarHeight).padding(start = 8.dp, end = 20.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        ElanIconButton(ElanIcons.Close, "Quitter la session", onClose)
        if (state.plannedMinutes > 0) {
            val fraction = state.elapsedMs.toFloat() / state.plannedMs
            ElanProgress(fraction, Modifier.weight(1f), description = "Temps écoulé")
            val remaining = ((state.plannedMs - state.elapsedMs).coerceAtLeast(0) / 1000).toInt()
            Text("%d:%02d".format(remaining / 60, remaining % 60), style = ElanTheme.type.labelL, color = ElanTheme.colors.onSurface)
        } else {
            Text("Session libre", style = ElanTheme.type.titleM, color = ElanTheme.colors.onSurface, modifier = Modifier.weight(1f))
            Text("${state.correct} / ${state.answered}", style = ElanTheme.type.labelL, color = ElanTheme.colors.onSurface)
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun QuestionArea(
    state: TrainingUiState,
    picked: PickedQuestion,
    onSelect: (Int) -> Unit,
    onValidate: () -> Unit,
    onNext: () -> Unit,
) {
    val question = picked.question
    val outcome = state.outcome
    val english = state.language == ExplanationLanguage.ENGLISH

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val maxSheetHeight = maxHeight * 0.72f
        var sheetHeightPx by remember { mutableIntStateOf(0) }
        // On laisse sous la question la place du bouton ou de la feuille de correction,
        // pour pouvoir faire défiler et relire les options.
        val bottomSpace = if (outcome != null) with(LocalDensity.current) { sheetHeightPx.toDp() } + 16.dp else 96.dp
        val scroll = rememberScrollState()
        LaunchedEffect(question.id) { scroll.scrollTo(0) }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scroll)
                .padding(horizontal = ElanDimens.gutter)
                .padding(top = 4.dp, bottom = bottomSpace),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                ElanChip("Part 5", tonal = true)
                ElanChip(if (english) picked.tagLabelEn else picked.tagLabelFr, tonal = true)
                when (picked.reason) {
                    PickReason.RETRY -> ElanChip("Retour de contrôle", selected = true)
                    PickReason.DUE_SKILL -> ElanChip("Révision", selected = true)
                    else -> Unit
                }
            }
            Text(
                text = stemText(question.stem, if (outcome != null) question.options[question.answerIndex] else null),
                style = ElanTheme.type.reading,
                color = ElanTheme.colors.onBg,
                modifier = Modifier.padding(vertical = 8.dp),
            )
            question.options.forEachIndexed { index, option ->
                val optionState = when {
                    outcome == null -> if (state.selectedIndex == index) OptionState.Selected else OptionState.Default
                    index == question.answerIndex -> OptionState.Correct
                    index == state.selectedIndex -> OptionState.Wrong
                    else -> OptionState.Default
                }
                OptionRow(
                    letter = LETTERS[index].toString(),
                    text = option,
                    state = optionState,
                    onClick = if (outcome == null) ({ onSelect(index) }) else null,
                )
            }
        }

        if (outcome == null) {
            ElanButton(
                text = "Valider",
                onClick = onValidate,
                enabled = state.selectedIndex != null,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .padding(horizontal = ElanDimens.gutter)
                    .padding(bottom = 16.dp),
            )
        }

        AnimatedVisibility(
            visible = outcome != null,
            modifier = Modifier.align(Alignment.BottomCenter),
            enter = slideInVertically(tween(240)) { it },
            exit = ExitTransition.None,
        ) {
            if (outcome != null) {
                FeedbackSheet(
                    picked = picked,
                    selectedIndex = state.selectedIndex ?: -1,
                    outcome = outcome,
                    english = english,
                    continueLabel = if (state.timeIsUp) "Voir le bilan" else "Continuer",
                    onContinue = onNext,
                    modifier = Modifier.heightIn(max = maxSheetHeight).onSizeChanged { sheetHeightPx = it.height },
                )
            }
        }
    }
}

/** L'énoncé, avec le trou souligné ; une fois corrigé, le trou est rempli par la bonne réponse. */
@Composable
private fun stemText(stem: String, answer: String?): AnnotatedString {
    val c = ElanTheme.colors
    val start = stem.indexOf(BLANK)
    if (start < 0) return AnnotatedString(stem)
    return buildAnnotatedString {
        append(stem.substring(0, start))
        if (answer == null) {
            withStyle(SpanStyle(color = c.primaryInk, fontWeight = FontWeight.Bold)) { append("_______") }
        } else {
            withStyle(SpanStyle(color = c.successInk, fontWeight = FontWeight.Bold, textDecoration = TextDecoration.Underline)) {
                append(answer)
            }
        }
        append(stem.substring(start + BLANK.length))
    }
}

/** Feuille de correction : bonne réponse, règle, raison de chaque distracteur, piège, effet sur la révision. */
@Composable
private fun FeedbackSheet(
    picked: PickedQuestion,
    selectedIndex: Int,
    outcome: AnswerOutcome,
    english: Boolean,
    continueLabel: String,
    onContinue: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = ElanTheme.colors
    val type = ElanTheme.type
    val question = picked.question
    val explanation = if (english) question.explanationEn else question.explanationFr
    val ink = if (outcome.correct) c.successInk else c.errorInk
    fun tr(fr: String, en: String) = if (english) en else fr

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(c.surface, ElanShapes.sheet)
            .border(ElanDimens.border, c.outlineVariant, ElanShapes.sheet)
            .padding(start = ElanDimens.gutter, end = ElanDimens.gutter, top = 12.dp, bottom = 16.dp),
    ) {
        Box(Modifier.align(Alignment.CenterHorizontally).size(width = 32.dp, height = 4.dp).background(c.outlineVariant, ElanShapes.full))
        Spacer(Modifier.height(12.dp))
        Column(
            modifier = Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(if (outcome.correct) ElanIcons.CheckCircle else ElanIcons.XCircle, contentDescription = null, tint = ink, modifier = Modifier.size(24.dp))
                Spacer(Modifier.width(8.dp))
                Text(
                    if (outcome.correct) tr("Bonne réponse !", "Correct!") else tr("Pas tout à fait", "Not quite"),
                    style = type.titleL,
                    color = ink,
                )
            }
            Text(
                tr("Bonne réponse : ", "Correct answer: ") + "(${LETTERS[question.answerIndex]}) ${question.options[question.answerIndex]}",
                style = type.titleM,
                color = c.onSurface,
            )
            Text(explanation.rule, style = type.bodyL, color = c.onSurface)

            LabelSmall(tr("Pourquoi pas les autres", "Why not the others"))
            question.options.forEachIndexed { index, option ->
                if (index != question.answerIndex) {
                    val chosen = index == selectedIndex
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(
                            "(${LETTERS[index]}) $option" + if (chosen) tr(" · ta réponse", " · your answer") else "",
                            style = type.labelL,
                            color = if (chosen) c.errorInk else c.onSurface,
                        )
                        Text(explanation.distractorNotes[index], style = type.bodyM, color = c.muted)
                    }
                }
            }

            Callout(tr("Piège à retenir", "Trap to remember"), explanation.trap)
            Text(
                leitnerMessage(if (english) picked.tagLabelEn else picked.tagLabelFr, outcome, english),
                style = type.bodyM,
                color = c.muted,
            )
        }
        Spacer(Modifier.height(12.dp))
        ElanButton(continueLabel, onContinue, Modifier.fillMaxWidth())
    }
}

/** Ce que la réponse change pour la révision espacée de la compétence, en une phrase. */
fun leitnerMessage(label: String, outcome: AnswerOutcome, english: Boolean): String {
    val days = Leitner.intervalFor(outcome.boxAfter)
    val before = outcome.boxBefore
    val moved = before == null || before != outcome.boxAfter || !outcome.correct
    return if (english) {
        val whenText = if (days == 1) "tomorrow" else "in $days days"
        when {
            !moved -> "$label stays in box ${outcome.boxAfter}: it was not due for review yet."
            !outcome.correct && before == null -> "$label starts in box 1: review $whenText."
            !outcome.correct -> "$label goes back to box 1: review $whenText."
            else -> "$label moves to box ${outcome.boxAfter} of ${Leitner.LAST_BOX}: review $whenText."
        }
    } else {
        val whenText = if (days == 1) "demain" else "dans $days jours"
        when {
            !moved -> "$label reste en boîte ${outcome.boxAfter} : elle n'était pas encore à réviser."
            !outcome.correct && before == null -> "$label démarre en boîte 1 : à revoir $whenText."
            !outcome.correct -> "$label revient en boîte 1 : à revoir $whenText."
            else -> "$label passe en boîte ${outcome.boxAfter} sur ${Leitner.LAST_BOX} : à revoir $whenText."
        }
    }
}
