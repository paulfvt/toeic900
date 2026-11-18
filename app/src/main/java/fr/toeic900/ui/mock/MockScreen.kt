package fr.toeic900.ui.mock

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import fr.toeic900.data.MockQuestion
import fr.toeic900.data.MockUnit
import fr.toeic900.domain.PaceState
import fr.toeic900.domain.SystemClock
import fr.toeic900.ui.appContainer
import fr.toeic900.ui.components.ButtonStyle
import fr.toeic900.ui.components.ElanButton
import fr.toeic900.ui.components.ElanIconButton
import fr.toeic900.ui.components.ElanProgress
import fr.toeic900.ui.components.LabelSmall
import fr.toeic900.ui.components.OptionRow
import fr.toeic900.ui.components.OptionState
import fr.toeic900.ui.theme.ElanDimens
import fr.toeic900.ui.theme.ElanIcons
import fr.toeic900.ui.theme.ElanShapes
import fr.toeic900.ui.theme.ElanTheme
import fr.toeic900.ui.training.DocumentCard
import fr.toeic900.ui.training.documentText

private const val LETTERS = "ABCD"
private const val BLANK = "-------"

fun formatClock(ms: Long): String {
    val seconds = (ms.coerceAtLeast(0) / 1000).toInt()
    return "%d:%02d".format(seconds / 60, seconds % 60)
}

@Composable
fun MockScreen(testId: String, onFinished: (mockId: Long) -> Unit, onAbandon: () -> Unit) {
    val container = appContainer()
    val viewModel = viewModel { MockViewModel(container.mock, container.progress, SystemClock, testId) }
    val state by viewModel.state.collectAsStateWithLifecycle()

    LaunchedEffect(state.finishedMockId) { state.finishedMockId?.let(onFinished) }

    var confirmQuit by remember { mutableStateOf(false) }
    var confirmSubmit by remember { mutableStateOf(false) }
    BackHandler { confirmQuit = true }

    if (confirmQuit) {
        AlertDialog(
            onDismissRequest = { confirmQuit = false },
            title = { Text("Abandonner le demi-test ?", style = ElanTheme.type.titleL) },
            text = { Text("Tes réponses ne seront pas enregistrées et ce passage ne comptera pas dans le score estimé.", style = ElanTheme.type.bodyL) },
            confirmButton = { ElanButton("Abandonner", { confirmQuit = false; onAbandon() }, style = ButtonStyle.Text) },
            dismissButton = { ElanButton("Continuer", { confirmQuit = false }, style = ButtonStyle.Text) },
            containerColor = ElanTheme.colors.surface,
        )
    }
    if (confirmSubmit) {
        val blank = state.unanswered
        AlertDialog(
            onDismissRequest = { confirmSubmit = false },
            title = { Text("Rendre la copie ?", style = ElanTheme.type.titleL) },
            text = {
                Text(
                    if (blank == 0) {
                        "Tu as répondu aux ${state.total} questions. Il reste ${formatClock(state.plannedMs - state.elapsedMs)} : tu peux encore te relire."
                    } else {
                        "$blank ${if (blank > 1) "questions restent" else "question reste"} sans réponse et ${if (blank > 1) "compteront" else "comptera"} comme ${if (blank > 1) "fausses" else "fausse"}. À l'examen, mieux vaut toujours répondre au hasard."
                    },
                    style = ElanTheme.type.bodyL,
                )
            },
            confirmButton = { ElanButton("Rendre", { confirmSubmit = false; viewModel.submit() }, style = ButtonStyle.Text) },
            dismissButton = { ElanButton("Revenir", { confirmSubmit = false }, style = ButtonStyle.Text) },
            containerColor = ElanTheme.colors.surface,
        )
    }

    Column(Modifier.fillMaxSize()) {
        val (unit, inUnit) = state.position ?: return@Column
        MockBar(state, onClose = { confirmQuit = true })
        val question = unit.questions[inUnit]
        val selected = state.answers[question.question.id]
        Column(Modifier.weight(1f)) {
            if (unit.documents.isEmpty()) {
                SentenceQuestion(state.index, state.total, question, selected, viewModel::select)
            } else {
                DocumentQuestion(state, unit, inUnit, question, selected, viewModel::select)
            }
        }
        // Navigation libre, comme sur une copie papier.
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = ElanDimens.gutter, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            ElanButton("Précédente", { viewModel.goTo(state.index - 1) }, Modifier.weight(1f), style = ButtonStyle.Outlined, enabled = state.index > 0)
            if (state.index < state.total - 1) {
                ElanButton("Suivante", { viewModel.goTo(state.index + 1) }, Modifier.weight(1f))
            } else {
                ElanButton("Rendre", { confirmSubmit = true }, Modifier.weight(1f), enabled = !state.submitting)
            }
        }
    }
}

/** Temps restant, avancement, et indicateur de rythme (jamais la couleur seule : un mot et une icône). */
@Composable
private fun MockBar(state: MockUiState, onClose: () -> Unit) {
    val c = ElanTheme.colors
    val type = ElanTheme.type
    Column {
        Row(
            modifier = Modifier.fillMaxWidth().height(ElanDimens.appBarHeight).padding(start = 8.dp, end = 20.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            ElanIconButton(ElanIcons.Close, "Abandonner le demi-test", onClose)
            Column(Modifier.weight(1f)) {
                val part = state.position?.first?.part
                Text("Question ${state.index + 1} / ${state.total}${part?.let { " · Part $it" } ?: ""}", style = type.titleM, color = c.onSurface)
                ElanProgress(state.answers.size.toFloat() / state.total.coerceAtLeast(1), Modifier.fillMaxWidth(), description = "${state.answers.size} réponses sur ${state.total}")
            }
            Icon(ElanIcons.Clock, contentDescription = null, tint = c.onSurface, modifier = Modifier.size(18.dp))
            Text(formatClock(state.plannedMs - state.elapsedMs), style = type.labelL, color = c.onSurface)
        }
        val pace = state.pace ?: return@Column
        val (text, bg, ink) = when (pace.state) {
            PaceState.BEHIND -> Triple(
                "En retard d'environ ${(pace.deltaMs / 60_000).coerceAtLeast(1)} min : ${formatClock(pace.remainingMs)} pour ${pace.remainingQuestions} questions, dont ${pace.remainingPart7} de Part 7. Accélère.",
                c.warningContainer, c.onWarningContainer,
            )
            PaceState.AHEAD -> Triple("Rythme : en avance", c.surfaceContainer, c.muted)
            PaceState.ON_TRACK -> Triple("Rythme : dans les temps", c.surfaceContainer, c.muted)
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = ElanDimens.gutter)
                .background(bg, ElanShapes.md)
                .padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Icon(if (pace.state == PaceState.BEHIND) ElanIcons.Bolt else ElanIcons.Clock, contentDescription = null, tint = ink, modifier = Modifier.size(16.dp))
            Text(text, style = type.bodyM, color = ink)
        }
        Spacer(Modifier.height(8.dp))
    }
}

@Composable
private fun SentenceQuestion(index: Int, total: Int, q: MockQuestion, selected: Int?, onSelect: (Int) -> Unit) {
    val scroll = rememberScrollState()
    LaunchedEffect(q.question.id) { scroll.scrollTo(0) }
    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(scroll).padding(horizontal = ElanDimens.gutter).padding(top = 4.dp, bottom = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        val stem = q.question.stem
        val start = stem.indexOf(BLANK)
        val c = ElanTheme.colors
        Text(
            buildAnnotatedString {
                if (start < 0) {
                    append(stem)
                } else {
                    append(stem.substring(0, start))
                    withStyle(SpanStyle(color = c.primaryInk, fontWeight = FontWeight.Bold)) {
                        append(selected?.let { q.question.options[it] } ?: "_______")
                    }
                    append(stem.substring(start + BLANK.length))
                }
            },
            style = ElanTheme.type.reading,
            color = c.onBg,
            modifier = Modifier.padding(vertical = 8.dp),
        )
        Options(q, selected, onSelect, compact = false)
    }
}

@Composable
private fun Options(q: MockQuestion, selected: Int?, onSelect: (Int) -> Unit, compact: Boolean) {
    q.question.options.forEachIndexed { i, option ->
        OptionRow(
            letter = LETTERS[i].toString(),
            text = option,
            state = if (selected == i) OptionState.Selected else OptionState.Default,
            compact = compact,
            onClick = { onSelect(i) },
        )
    }
}

/** Parts 6 et 7 : les documents défilent en haut, la question et ses options dans un panneau repliable. */
@Composable
private fun DocumentQuestion(state: MockUiState, unit: MockUnit, inUnit: Int, q: MockQuestion, selected: Int?, onSelect: (Int) -> Unit) {
    val c = ElanTheme.colors
    val type = ElanTheme.type
    var expanded by remember(unit.id) { mutableStateOf(unit.part == 6) }
    val scroll = rememberScrollState()
    LaunchedEffect(unit.id) { scroll.scrollTo(0) }
    val questions = unit.questions.map { it.question }
    BoxWithConstraints(Modifier.fillMaxSize()) {
        // La moitié de la zone au plus : le texte et son trou restent visibles au-dessus.
        val maxPanel = maxHeight * 0.5f
        Column(Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier.weight(1f).fillMaxWidth().verticalScroll(scroll).padding(horizontal = ElanDimens.gutter).padding(bottom = 12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                unit.documents.forEachIndexed { i, doc ->
                    val text = documentText(doc, questions, inUnit, corrected = false, c = c, chosen = { blank ->
                        questions.getOrNull(blank)?.let { question -> state.answers[question.id]?.let { question.options[it] } }
                    })
                    DocumentCard(doc, i, unit.documents.size, text)
                }
            }
            Column(
                modifier = Modifier
                    .heightIn(max = maxPanel)
                    .fillMaxWidth()
                    .background(c.surface, ElanShapes.sheet)
                    .border(ElanDimens.border, c.outlineVariant, ElanShapes.sheet)
                    .padding(start = ElanDimens.gutter, end = ElanDimens.gutter, top = 4.dp, bottom = 8.dp),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = ElanDimens.touchTarget)
                        .clip(ElanShapes.md)
                        .clickable(role = Role.Button) { expanded = !expanded },
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    LabelSmall("Question ${inUnit + 1} sur ${unit.questions.size} de ce ${if (unit.part == 6) "texte" else "document"}", Modifier.weight(1f))
                    Text(if (expanded) "Lire le texte" else "Répondre", style = type.labelL, color = c.primaryInk)
                    Icon(if (expanded) ElanIcons.ChevronDown else ElanIcons.ChevronUp, contentDescription = null, tint = c.primaryInk, modifier = Modifier.size(20.dp))
                }
                val stem = when {
                    q.question.stem.isNotBlank() -> q.question.stem
                    q.question.options.any { it.length > 60 } -> "Quelle phrase complète le trou (${inUnit + 1}) ?"
                    else -> "Quel mot complète le trou (${inUnit + 1}) ?"
                }
                Text(stem, style = type.document.copy(fontWeight = FontWeight.W700), color = c.onSurface, modifier = Modifier.padding(bottom = 4.dp))
                if (expanded) {
                    Spacer(Modifier.height(8.dp))
                    Column(
                        modifier = Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Options(q, selected, onSelect, compact = true)
                    }
                }
            }
        }
    }
}
