package fr.toeic900.ui.training

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import fr.toeic900.data.AnswerOutcome
import fr.toeic900.data.PickedQuestion
import fr.toeic900.data.PickedUnit
import fr.toeic900.data.settings.ExplanationLanguage
import fr.toeic900.domain.Document
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
import fr.toeic900.ui.theme.ElanColors
import fr.toeic900.ui.theme.ElanDimens
import fr.toeic900.ui.theme.ElanIcons
import fr.toeic900.ui.theme.ElanShapes
import fr.toeic900.ui.theme.ElanTheme

private const val BLANK = "-------"
private const val LETTERS = "ABCD"

@Composable
fun TrainingScreen(
    part: Int,
    focusTag: String?,
    minutes: Int,
    stepId: String?,
    onFinished: (Long) -> Unit,
    onAbandon: () -> Unit,
    onOpenLesson: (tagId: String) -> Unit,
) {
    val container = appContainer()
    val viewModel = viewModel {
        TrainingViewModel(container.repository, container.settings, SystemClock, part, focusTag, minutes, stepId)
    }
    val state by viewModel.state.collectAsStateWithLifecycle()

    LaunchedEffect(state.finishedSessionId, state.abandoned) {
        state.finishedSessionId?.let(onFinished)
        if (state.abandoned) onAbandon()
    }
    // Le chronomètre s'arrête quand on ouvre une fiche ou qu'on quitte l'app, et reprend au retour.
    LifecycleResumeEffect(viewModel) {
        viewModel.resume()
        onPauseOrDispose { viewModel.pause() }
    }

    var confirmQuit by remember { mutableStateOf(false) }
    val askToQuit = { if (state.answered == 0) viewModel.quit() else confirmQuit = true }
    BackHandler(onBack = askToQuit)

    if (confirmQuit) {
        val lesson = state.step != null
        AlertDialog(
            onDismissRequest = { confirmQuit = false },
            title = { Text(if (lesson) "Quitter la leçon ?" else "Terminer la session ?", style = ElanTheme.type.titleL) },
            text = {
                Text(
                    if (lesson) {
                        "Tes réponses sont enregistrées, mais la leçon ne sera validée que si tu vas au bout des exercices."
                    } else {
                        "Tes réponses sont déjà enregistrées. Tu verras le bilan de la session."
                    },
                    style = ElanTheme.type.bodyL,
                )
            },
            confirmButton = {
                ElanButton(if (lesson) "Quitter" else "Terminer", { confirmQuit = false; viewModel.quit() }, style = ButtonStyle.Text)
            },
            dismissButton = { ElanButton("Continuer", { confirmQuit = false }, style = ButtonStyle.Text) },
            containerColor = ElanTheme.colors.surface,
        )
    }

    Column(Modifier.fillMaxSize()) {
        SessionBar(state, onClose = askToQuit)
        val unit = state.unit
        val picked = state.picked
        if (unit != null && picked != null) {
            val lessonTag = (listOf(picked.tagId) + picked.question.tags).firstOrNull { it in state.lessonTags }
            val openLesson = lessonTag?.let { tag -> { onOpenLesson(tag) } }
            if (unit.isStimulus) {
                StimulusArea(state, unit, picked, viewModel::select, viewModel::validate, viewModel::next, openLesson)
            } else {
                QuestionArea(state, picked, viewModel::select, viewModel::validate, viewModel::next, openLesson)
            }
        }
    }
}

private fun continueLabel(state: TrainingUiState) = when {
    state.isLastOfSession -> "Voir le bilan"
    !state.isLastQuestionOfUnit -> "Question suivante"
    else -> "Continuer"
}

/** Barre du haut : quitter, puis l'avancement de la leçon, le temps restant ou le score. */
@Composable
private fun SessionBar(state: TrainingUiState, onClose: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().height(ElanDimens.appBarHeight).padding(start = 8.dp, end = 20.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        ElanIconButton(ElanIcons.Close, "Quitter la session", onClose)
        val target = state.targetUnits
        when {
            target != null -> {
                ElanProgress(state.unitsDone.toFloat() / target, Modifier.weight(1f), description = "Avancement de la leçon")
                Text("${minOf(state.unitsDone + 1, target)} / $target", style = ElanTheme.type.labelL, color = ElanTheme.colors.onSurface)
            }
            state.plannedMinutes > 0 -> {
                val fraction = state.elapsedMs.toFloat() / state.plannedMs
                ElanProgress(fraction, Modifier.weight(1f), description = "Temps écoulé")
                val remaining = ((state.plannedMs - state.elapsedMs).coerceAtLeast(0) / 1000).toInt()
                Text("%d:%02d".format(remaining / 60, remaining % 60), style = ElanTheme.type.labelL, color = ElanTheme.colors.onSurface)
            }
            else -> {
                Text("Session libre", style = ElanTheme.type.titleM, color = ElanTheme.colors.onSurface, modifier = Modifier.weight(1f))
                Text("${state.correct} / ${state.answered}", style = ElanTheme.type.labelL, color = ElanTheme.colors.onSurface)
            }
        }
    }
}

@Composable
private fun ReasonChip(reason: PickReason) {
    when (reason) {
        PickReason.RETRY -> ElanChip("Retour de contrôle", selected = true)
        PickReason.DUE_SKILL -> ElanChip("Révision", selected = true)
        else -> Unit
    }
}

// ───────────────────────────── Part 5 : une phrase à compléter ─────────────────────────────

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun QuestionArea(
    state: TrainingUiState,
    picked: PickedQuestion,
    onSelect: (Int) -> Unit,
    onValidate: () -> Unit,
    onNext: () -> Unit,
    onOpenLesson: (() -> Unit)?,
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
                ElanChip("Part ${question.part}", tonal = true)
                ElanChip(if (english) picked.tagLabelEn else picked.tagLabelFr, tonal = true)
                ReasonChip(picked.reason)
            }
            Text(
                text = stemText(question.stem, if (outcome != null) question.options[question.answerIndex] else null),
                style = ElanTheme.type.reading,
                color = ElanTheme.colors.onBg,
                modifier = Modifier.padding(vertical = 8.dp),
            )
            question.options.forEachIndexed { index, option ->
                OptionRow(
                    letter = LETTERS[index].toString(),
                    text = option,
                    state = optionState(index, state.selectedIndex, question.answerIndex, outcome != null),
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
                    continueLabel = continueLabel(state),
                    onContinue = onNext,
                    onOpenLesson = onOpenLesson,
                    modifier = Modifier.heightIn(max = maxSheetHeight).onSizeChanged { sheetHeightPx = it.height },
                )
            }
        }
    }
}

private fun optionState(index: Int, selectedIndex: Int?, answerIndex: Int, corrected: Boolean) = when {
    !corrected -> if (selectedIndex == index) OptionState.Selected else OptionState.Default
    index == answerIndex -> OptionState.Correct
    index == selectedIndex -> OptionState.Wrong
    else -> OptionState.Default
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

// ───────────────────────────── Parts 6 et 7 : un texte et ses questions ─────────────────────────────

/**
 * Vue de lecture : les documents défilent en haut ; la question reste en bas, dans un panneau
 * qu'on replie pour lire le texte sur presque tout l'écran et qu'on déplie pour répondre.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun StimulusArea(
    state: TrainingUiState,
    unit: PickedUnit,
    picked: PickedQuestion,
    onSelect: (Int) -> Unit,
    onValidate: () -> Unit,
    onNext: () -> Unit,
    onOpenLesson: (() -> Unit)?,
) {
    val outcome = state.outcome
    val english = state.language == ExplanationLanguage.ENGLISH
    // Part 6 : texte court, on répond tout de suite. Part 7 : on lit d'abord.
    var expanded by remember(unit.id) { mutableStateOf(unit.part == 6) }
    val scroll = rememberScrollState()
    LaunchedEffect(unit.id) { scroll.scrollTo(0) }

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val maxPanelHeight = maxHeight * 0.62f
        Column(Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .verticalScroll(scroll)
                    .padding(horizontal = ElanDimens.gutter)
                    .padding(top = 4.dp, bottom = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    ElanChip("Part ${unit.part}", tonal = true)
                    ElanChip(if (unit.part == 6) "Texte à trous" else documentCountLabel(unit.documents.size), tonal = true)
                    ReasonChip(unit.reason)
                }
                unit.documents.forEachIndexed { index, document ->
                    DocumentCard(
                        document = document,
                        index = index,
                        total = unit.documents.size,
                        text = documentText(document, unit, state.questionIndex, outcome != null, ElanTheme.colors),
                    )
                }
            }
            if (outcome == null) {
                QuestionPanel(
                    state = state,
                    unit = unit,
                    picked = picked,
                    english = english,
                    expanded = expanded,
                    onToggle = { expanded = !expanded },
                    onSelect = onSelect,
                    onValidate = onValidate,
                    modifier = Modifier.heightIn(max = maxPanelHeight),
                )
            } else {
                FeedbackSheet(
                    picked = picked,
                    selectedIndex = state.selectedIndex ?: -1,
                    outcome = outcome,
                    english = english,
                    continueLabel = continueLabel(state),
                    onContinue = { expanded = true; onNext() },
                    onOpenLesson = onOpenLesson,
                    modifier = Modifier.heightIn(max = maxPanelHeight),
                )
            }
        }
    }
}

private fun documentCountLabel(count: Int) = if (count == 1) "1 document" else "$count documents"

/** Le nom anglais d'un genre de document, comme dans les consignes de l'examen. */
private val GENRE_LABELS = mapOf(
    "email" to "E-mail", "letter" to "Letter", "memo" to "Memo", "notice" to "Notice",
    "announcement" to "Announcement", "article" to "Article", "advertisement" to "Advertisement",
    "press-release" to "Press release", "newsletter" to "Newsletter", "webpage" to "Web page",
    "review" to "Review", "form" to "Form", "survey" to "Survey", "schedule" to "Schedule",
    "itinerary" to "Itinerary", "invoice" to "Invoice", "receipt" to "Receipt", "chat" to "Text-message chain",
    "instructions" to "Instructions", "job-posting" to "Job posting", "brochure" to "Brochure", "report" to "Report",
)

@Composable
private fun DocumentCard(document: Document, index: Int, total: Int, text: AnnotatedString) {
    val c = ElanTheme.colors
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(c.surface, ElanShapes.lg)
            .border(ElanDimens.border, c.outlineVariant, ElanShapes.lg)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        val genre = GENRE_LABELS[document.genre] ?: document.genre
        LabelSmall(if (total > 1) "Document ${index + 1} sur $total · $genre" else genre)
        document.title?.let { Text(it, style = ElanTheme.type.titleM, color = c.onSurface) }
        Text(text, style = ElanTheme.type.document, color = c.onSurface)
    }
}

private val P6_BLANK = Regex("""\[\[([1-4])]]""")
private val HEADER_LABEL = Regex("""(?m)^(To|From|Cc|Date|Sent|Subject|Re|Attachment|Attachments):""")
private val CHAT_SPEAKER = Regex("""(?m)^[^\n:(]{2,40}\(\d{1,2}:\d{2} [AP]\.M\.\):?""")
private val P7_MARKER = Regex("""\[[1-4]]""")

/**
 * Le texte d'un document, mis en forme :
 *  - Part 6 : les trous déjà corrigés sont remplis par la bonne réponse, le trou en cours est
 *    mis en avant, les suivants sont numérotés ;
 *  - une fois la question corrigée, les passages qui justifient la réponse sont surlignés ;
 *  - en-têtes d'e-mail, noms des auteurs d'un échange de messages et repères [1] à [4] en gras.
 */
private fun documentText(
    document: Document,
    unit: PickedUnit,
    questionIndex: Int,
    corrected: Boolean,
    c: ElanColors,
): AnnotatedString = buildAnnotatedString {
    val evidence = if (corrected) unit.questions.getOrNull(questionIndex)?.question?.evidence.orEmpty() else emptyList()
    val bold = SpanStyle(fontWeight = FontWeight.W700)

    fun appendPlain(segment: String) {
        val base = length
        append(segment)
        for (match in HEADER_LABEL.findAll(segment)) addStyle(bold, base + match.range.first, base + match.range.last + 1)
        if (document.genre == "chat") {
            for (match in CHAT_SPEAKER.findAll(segment)) addStyle(bold, base + match.range.first, base + match.range.last + 1)
        }
        for (match in P7_MARKER.findAll(segment)) {
            addStyle(SpanStyle(fontWeight = FontWeight.W700, color = c.primaryInk), base + match.range.first, base + match.range.last + 1)
        }
        for (quote in evidence) {
            var from = segment.indexOf(quote)
            while (from >= 0) {
                addStyle(SpanStyle(background = c.accentContainer, color = c.onAccentContainer), base + from, base + from + quote.length)
                from = segment.indexOf(quote, from + quote.length)
            }
        }
    }

    var cursor = 0
    for (match in P6_BLANK.findAll(document.text)) {
        appendPlain(document.text.substring(cursor, match.range.first))
        val blankIndex = match.groupValues[1].toInt() - 1
        val question = unit.questions.getOrNull(blankIndex)?.question
        val filled = blankIndex < questionIndex || (blankIndex == questionIndex && corrected)
        when {
            filled && question != null ->
                withStyle(SpanStyle(color = c.successInk, fontWeight = FontWeight.W700, textDecoration = TextDecoration.Underline)) {
                    append(question.options[question.answerIndex])
                }
            blankIndex == questionIndex ->
                withStyle(SpanStyle(color = c.onPrimaryContainer, background = c.primaryContainer, fontWeight = FontWeight.W700)) {
                    append(" (${blankIndex + 1}) _______ ")
                }
            else -> withStyle(SpanStyle(color = c.muted, fontWeight = FontWeight.W700)) { append("(${blankIndex + 1}) _______") }
        }
        cursor = match.range.last + 1
    }
    appendPlain(document.text.substring(cursor))
}

/** Le panneau de la question, sous les documents : replié (la question seule) ou déplié (avec les options). */
@Composable
private fun QuestionPanel(
    state: TrainingUiState,
    unit: PickedUnit,
    picked: PickedQuestion,
    english: Boolean,
    expanded: Boolean,
    onToggle: () -> Unit,
    onSelect: (Int) -> Unit,
    onValidate: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = ElanTheme.colors
    val type = ElanTheme.type
    val question = picked.question
    val number = state.questionIndex + 1
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(c.surface, ElanShapes.sheet)
            .border(ElanDimens.border, c.outlineVariant, ElanShapes.sheet)
            .padding(start = ElanDimens.gutter, end = ElanDimens.gutter, top = 4.dp, bottom = 16.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = ElanDimens.touchTarget)
                .clip(ElanShapes.md)
                .clickable(role = Role.Button, onClick = onToggle),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            LabelSmall(
                "Question $number sur ${unit.questions.size} · ${if (english) picked.tagLabelEn else picked.tagLabelFr}",
                Modifier.weight(1f),
            )
            Text(if (expanded) "Lire le texte" else "Répondre", style = type.labelL, color = c.primaryInk)
            Icon(
                if (expanded) ElanIcons.ChevronDown else ElanIcons.ChevronUp,
                contentDescription = null,
                tint = c.primaryInk,
                modifier = Modifier.size(20.dp),
            )
        }
        val stem = when {
            question.stem.isNotBlank() -> question.stem
            question.options.any { it.length > 60 } -> "Quelle phrase complète le trou ($number) ?"
            else -> "Quel mot complète le trou ($number) ?"
        }
        Text(stem, style = type.document.copy(fontWeight = FontWeight.W700), color = c.onSurface, modifier = Modifier.padding(bottom = 4.dp))
        if (expanded) {
            Spacer(Modifier.height(8.dp))
            Column(
                modifier = Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                question.options.forEachIndexed { index, option ->
                    OptionRow(
                        letter = LETTERS[index].toString(),
                        text = option,
                        state = optionState(index, state.selectedIndex, question.answerIndex, corrected = false),
                        compact = true,
                        onClick = { onSelect(index) },
                    )
                }
            }
            Spacer(Modifier.height(12.dp))
            ElanButton("Valider", onValidate, Modifier.fillMaxWidth(), enabled = state.selectedIndex != null)
        }
    }
}

// ───────────────────────────── Correction ─────────────────────────────

/** Feuille de correction : bonne réponse, règle, raison de chaque distracteur, piège, effet sur la révision. */
@Composable
private fun FeedbackSheet(
    picked: PickedQuestion,
    selectedIndex: Int,
    outcome: AnswerOutcome,
    english: Boolean,
    continueLabel: String,
    onContinue: () -> Unit,
    onOpenLesson: (() -> Unit)?,
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
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            if (onOpenLesson != null) {
                ElanButton(tr("Voir la fiche", "See the lesson"), onOpenLesson, style = ButtonStyle.Text)
            }
            ElanButton(continueLabel, onContinue, Modifier.weight(1f))
        }
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
