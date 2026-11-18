package fr.toeic900.ui.progress

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import fr.toeic900.data.MockSummary
import fr.toeic900.data.ProgressOverview
import fr.toeic900.data.ProgressRepository
import fr.toeic900.data.TagStat
import fr.toeic900.domain.DailyGoal
import fr.toeic900.domain.DayActivity
import fr.toeic900.domain.GoalType
import fr.toeic900.domain.ScoreEstimate
import fr.toeic900.ui.appContainer
import fr.toeic900.ui.components.ButtonStyle
import fr.toeic900.ui.components.CardStyle
import fr.toeic900.ui.components.ElanButton
import fr.toeic900.ui.components.ElanCard
import fr.toeic900.ui.components.ElanProgress
import fr.toeic900.ui.components.ElanTopBar
import fr.toeic900.ui.components.LabelSmall
import fr.toeic900.ui.home.plural
import fr.toeic900.ui.theme.ElanDimens
import fr.toeic900.ui.theme.ElanIcons
import fr.toeic900.ui.theme.ElanShapes
import fr.toeic900.ui.theme.ElanTheme
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.roundToInt
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn

class ProgressViewModel(repository: ProgressRepository) : ViewModel() {
    /** null tant que les chiffres ne sont pas calculés. */
    val overview: StateFlow<ProgressOverview?> = repository.observe()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
}

private val DAY_FORMAT = DateTimeFormatter.ofPattern("d MMM", Locale.FRENCH)
fun formatDay(day: Long): String = LocalDate.ofEpochDay(day).format(DAY_FORMAT)

fun percent(rate: Double): String = "${(rate * 100).roundToInt()} %"

@Composable
fun ProgressScreen(
    onMockTest: () -> Unit,
    onBadges: () -> Unit,
    onOpenLesson: (tagId: String) -> Unit,
    onOpenMock: (mockId: Long) -> Unit,
) {
    val container = appContainer()
    val viewModel = viewModel { ProgressViewModel(container.progress) }
    val overview by viewModel.overview.collectAsStateWithLifecycle()

    Column(Modifier.fillMaxSize()) {
        ElanTopBar(title = "Progrès")
        val o = overview ?: return@Column
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = ElanDimens.gutter)
                .padding(bottom = ElanDimens.gapMajor),
            verticalArrangement = Arrangement.spacedBy(ElanDimens.gapSection),
        ) {
            ScoreCard(o.reading, o.mocks.isEmpty(), onMockTest)
            LevelCard(o, onBadges)
            ActivityCard(o.lastDays, o.goal)
            if (o.mocks.isNotEmpty()) MockHistoryCard(o.mocks, onOpenMock)
            PartsCard(o)
            TagsCard(o.tags, onOpenLesson)
        }
    }
}

/** Le score estimé : toujours une fourchette, toujours étiqueté comme une estimation. */
@Composable
fun ScoreCard(reading: ScoreEstimate?, noMockYet: Boolean, onMockTest: () -> Unit) {
    val c = ElanTheme.colors
    val type = ElanTheme.type
    ElanCard(style = CardStyle.Filled) {
        LabelSmall("Score Reading estimé", color = c.onPrimaryContainer)
        Spacer(Modifier.height(8.dp))
        if (reading == null) {
            Text("Pas encore d'estimation", style = type.titleL, color = c.onPrimaryContainer)
            Spacer(Modifier.height(4.dp))
            Text(
                "Il faut au moins 30 réponses d'entraînement sur 30 jours, ou un demi-test.",
                style = type.bodyM,
                color = c.onPrimaryContainer,
            )
        } else {
            Row(verticalAlignment = Alignment.Bottom) {
                Text("≈ ${reading.score}", style = type.display, color = c.onPrimaryContainer)
                Spacer(Modifier.width(8.dp))
                Text("/ 495", style = type.titleM, color = c.onPrimaryContainer, modifier = Modifier.padding(bottom = 10.dp))
            }
            Text("Fourchette : ${reading.low} à ${reading.high}", style = type.titleM, color = c.onPrimaryContainer)
            Spacer(Modifier.height(4.dp))
            val basis = if (reading.provisional) {
                "Estimation provisoire, tirée de l'entraînement seul (${reading.trainingAnswers} réponses sur 30 jours). Un demi-test la rendra plus fiable."
            } else {
                "Estimation tirée de ${reading.mockCount} ${plural(reading.mockCount, "demi-test", "demi-tests")} (80 %) et de l'entraînement des 30 derniers jours (20 %)."
            }
            Text(basis, style = type.bodyM, color = c.onPrimaryContainer)
        }
        Spacer(Modifier.height(4.dp))
        Text("Listening : estimé à partir de la Phase 4.", style = type.bodyM, color = c.onPrimaryContainer)
        Spacer(Modifier.height(12.dp))
        ElanButton(if (noMockYet) "Passer le demi-test" else "Demi-test Reading", onMockTest, Modifier.fillMaxWidth())
    }
}

@Composable
private fun LevelCard(o: ProgressOverview, onBadges: () -> Unit) {
    val c = ElanTheme.colors
    val type = ElanTheme.type
    ElanCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                LabelSmall("Niveau")
                Text("Niveau ${o.level.number}", style = type.headline, color = c.onSurface)
            }
            Text("${o.level.totalXp} XP", style = type.labelL, color = c.muted)
        }
        Spacer(Modifier.height(8.dp))
        ElanProgress(o.level.fraction, Modifier.fillMaxWidth(), description = "Progression vers le niveau suivant")
        Spacer(Modifier.height(4.dp))
        Text("Encore ${o.level.xpToNext - o.level.xpInLevel} XP avant le niveau ${o.level.number + 1}", style = type.bodyM, color = c.muted)
        Spacer(Modifier.height(16.dp))
        StreakLine(o)
        Spacer(Modifier.height(12.dp))
        val unlocked = o.badges.count { it.unlocked }
        ElanButton("Badges · $unlocked sur ${o.badges.size}", onBadges, Modifier.fillMaxWidth(), style = ButtonStyle.Outlined)
    }
}

@Composable
fun StreakLine(o: ProgressOverview) {
    val c = ElanTheme.colors
    val type = ElanTheme.type
    val s = o.streak
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Icon(ElanIcons.Flame, contentDescription = null, tint = c.accentInk, modifier = Modifier.size(28.dp))
        Column(Modifier.weight(1f)) {
            Text(
                "${s.weeks} ${plural(s.weeks, "semaine validée", "semaines validées")} d'affilée",
                style = type.titleM,
                color = c.onSurface,
            )
            val week = if (s.currentWeekValidated) {
                "Semaine en cours validée (${s.daysThisWeek} jours)."
            } else {
                "Cette semaine : ${s.daysThisWeek} jour${if (s.daysThisWeek > 1) "s" else ""} sur ${s.threshold} visés, ${s.daysLeft(o.today)} restant${if (s.daysLeft(o.today) > 1) "s" else ""}."
            }
            Text(week, style = type.bodyM, color = c.muted)
        }
    }
}

/** Réponses par jour sur 28 jours ; la ligne pointillée marque l'objectif quotidien s'il se compte en questions. */
@Composable
private fun ActivityCard(days: List<DayActivity>, goal: DailyGoal) {
    val c = ElanTheme.colors
    val type = ElanTheme.type
    if (days.isEmpty()) return
    var selected by remember(days) { mutableStateOf(days.lastIndex) }
    val value: (DayActivity) -> Int = { if (goal.type == GoalType.MINUTES) (it.activeMs / 60_000).toInt() else it.answers }
    val unit = if (goal.type == GoalType.MINUTES) "min" else "questions"
    ElanCard {
        LabelSmall("Activité · 4 dernières semaines")
        Spacer(Modifier.height(4.dp))
        val day = days[selected]
        Text("${formatDay(day.day)} : ${value(day)} $unit", style = type.titleM, color = c.onSurface)
        Spacer(Modifier.height(12.dp))
        val max = maxOf(days.maxOf(value), goal.target, 1)
        val total = days.sumOf(value)
        val activeDays = days.count { value(it) > 0 }
        val barColor = c.primary
        val selectedColor = c.primaryStrong
        val goalColor = c.muted
        val baseColor = c.outlineVariant
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(120.dp)
                .semantics { contentDescription = "Activité sur 28 jours : $total $unit en tout, $activeDays jours actifs. Objectif : ${goal.target} $unit par jour." }
                .pointerInput(days) {
                    detectTapGestures { offset ->
                        selected = (offset.x / (size.width / days.size)).toInt().coerceIn(0, days.lastIndex)
                    }
                },
        ) {
            val slot = size.width / days.size
            val gap = 2.dp.toPx()
            val barWidth = (slot - gap).coerceAtLeast(1f)
            val radius = CornerRadius(minOf(4.dp.toPx(), barWidth / 2))
            days.forEachIndexed { index, d ->
                val h = size.height * value(d) / max
                if (h > 0f) {
                    // Bout arrondi en haut, posé sur la ligne de base.
                    val path = Path().apply {
                        addRoundRect(
                            RoundRect(
                                left = index * slot, top = size.height - h, right = index * slot + barWidth, bottom = size.height,
                                topLeftCornerRadius = radius, topRightCornerRadius = radius,
                            ),
                        )
                    }
                    drawPath(path, if (index == selected) selectedColor else barColor)
                }
            }
            drawLine(baseColor, Offset(0f, size.height), Offset(size.width, size.height), strokeWidth = 1.dp.toPx())
            val goalY = size.height * (1 - goal.target.toFloat() / max)
            drawLine(goalColor, Offset(0f, goalY), Offset(size.width, goalY), strokeWidth = 1.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 8f)))
        }
        Spacer(Modifier.height(6.dp))
        Row(Modifier.fillMaxWidth()) {
            Text(formatDay(days.first().day), style = type.bodyM, color = c.muted, modifier = Modifier.weight(1f))
            Text("Aujourd'hui", style = type.bodyM, color = c.muted)
        }
        Spacer(Modifier.height(4.dp))
        Text("Pointillés : objectif de ${goal.target} $unit par jour. Touche une barre pour voir le jour.", style = type.bodyM, color = c.muted)
    }
}

@Composable
private fun MockHistoryCard(mocks: List<MockSummary>, onOpenMock: (Long) -> Unit) {
    val c = ElanTheme.colors
    val type = ElanTheme.type
    ElanCard {
        LabelSmall("Demi-tests Reading")
        Spacer(Modifier.height(8.dp))
        if (mocks.size >= 2) {
            ScoreLine(mocks.map { it.score })
            Spacer(Modifier.height(12.dp))
        }
        mocks.asReversed().forEach { m ->
            ElanCard(style = CardStyle.Tonal, onClick = { onOpenMock(m.id) }, modifier = Modifier.padding(vertical = 4.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(formatDay(m.day), style = type.titleM, color = c.onSurface)
                        Text(
                            "${m.tally.correct} / ${m.tally.total} · ${percent(m.tally.rate)}${if (m.timedOut) " · temps écoulé" else ""}",
                            style = type.bodyM,
                            color = c.muted,
                        )
                    }
                    Text("≈ ${m.score}", style = type.titleL, color = c.onSurface)
                    Icon(ElanIcons.ChevronRight, contentDescription = null, tint = c.muted, modifier = Modifier.size(20.dp))
                }
            }
        }
    }
}

/** Courbe des scores des demi-tests (au moins deux points), axe de 5 à 495. */
@Composable
private fun ScoreLine(scores: List<Int>) {
    val line = ElanTheme.colors.primary
    val ring = ElanTheme.colors.surface
    val base = ElanTheme.colors.outlineVariant
    Canvas(
        Modifier
            .fillMaxWidth()
            .height(100.dp)
            .semantics { contentDescription = "Scores des demi-tests : ${scores.joinToString(", ")}" },
    ) {
        val lo = (scores.min() - 40).coerceAtLeast(5)
        val hi = (scores.max() + 40).coerceAtMost(495)
        val span = (hi - lo).coerceAtLeast(1)
        val inset = 8.dp.toPx()
        val points = scores.mapIndexed { i, s ->
            Offset(
                inset + (size.width - 2 * inset) * i / (scores.size - 1),
                inset + (size.height - 2 * inset) * (1 - (s - lo).toFloat() / span),
            )
        }
        drawLine(base, Offset(0f, size.height), Offset(size.width, size.height), strokeWidth = 1.dp.toPx())
        val path = Path().apply {
            moveTo(points.first().x, points.first().y)
            points.drop(1).forEach { lineTo(it.x, it.y) }
        }
        drawPath(path, line, style = Stroke(width = 2.dp.toPx()))
        points.forEach {
            drawCircle(ring, radius = 6.dp.toPx(), center = it)
            drawCircle(line, radius = 4.dp.toPx(), center = it)
        }
    }
}

@Composable
private fun PartsCard(o: ProgressOverview) {
    val type = ElanTheme.type
    val c = ElanTheme.colors
    ElanCard {
        LabelSmall("Réussite par part (entraînement)")
        Spacer(Modifier.height(8.dp))
        if (o.parts.isEmpty()) {
            Text("Aucune réponse pour l'instant.", style = type.bodyM, color = c.muted)
        }
        o.parts.forEach { (part, tally) ->
            RateRow("Part $part", "${tally.total} ${plural(tally.total, "réponse", "réponses")}", tally.rate)
        }
    }
}

@Composable
private fun TagsCard(tags: List<TagStat>, onOpenLesson: (String) -> Unit) {
    val type = ElanTheme.type
    val c = ElanTheme.colors
    ElanCard {
        LabelSmall("Par compétence, de la plus fragile à la plus solide")
        Spacer(Modifier.height(8.dp))
        if (tags.isEmpty()) {
            Text("Aucune compétence travaillée pour l'instant.", style = type.bodyM, color = c.muted)
        }
        tags.forEach { t ->
            val box = t.box?.let { " · boîte $it/5" } ?: ""
            RateRow(t.labelFr, "${t.tally.correct}/${t.tally.total}$box", t.tally.rate, onClick = { onOpenLesson(t.tagId) })
        }
        if (tags.isNotEmpty()) {
            Spacer(Modifier.height(4.dp))
            Text("Touche une compétence pour ouvrir sa fiche de cours.", style = type.bodyM, color = c.muted)
        }
    }
}

@Composable
private fun RateRow(label: String, detail: String, rate: Double, onClick: (() -> Unit)? = null) {
    val type = ElanTheme.type
    val c = ElanTheme.colors
    val content: @Composable () -> Unit = {
        Column(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(label, style = type.titleM, color = c.onSurface, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                Spacer(Modifier.width(8.dp))
                Text(percent(rate), style = type.labelL, color = c.onSurface)
            }
            Spacer(Modifier.height(4.dp))
            ElanProgress(rate.toFloat(), Modifier.fillMaxWidth())
            Spacer(Modifier.height(2.dp))
            Text(detail, style = type.bodyM, color = c.muted)
        }
    }
    if (onClick != null) {
        Box(
            Modifier
                .fillMaxWidth()
                .clip(ElanShapes.card)
                .clickable(onClick = onClick),
        ) { content() }
    } else {
        content()
    }
}
