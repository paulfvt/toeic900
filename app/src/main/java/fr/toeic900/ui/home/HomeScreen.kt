package fr.toeic900.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import fr.toeic900.data.HomeStats
import fr.toeic900.data.TrainingRepository
import fr.toeic900.ui.PART_5
import fr.toeic900.ui.appContainer
import fr.toeic900.ui.components.ButtonStyle
import fr.toeic900.ui.components.CardStyle
import fr.toeic900.ui.components.ElanButton
import fr.toeic900.ui.components.ElanCard
import fr.toeic900.ui.components.ElanIconButton
import fr.toeic900.ui.components.ElanTopBar
import fr.toeic900.ui.components.LabelSmall
import fr.toeic900.ui.theme.ElanDimens
import fr.toeic900.ui.theme.ElanIcons
import fr.toeic900.ui.theme.ElanTheme
import kotlin.math.roundToInt
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn

class HomeViewModel(repository: TrainingRepository) : ViewModel() {
    val stats: StateFlow<HomeStats> = repository.observeHome(PART_5)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeStats())
}

@Composable
fun HomeScreen(onQuickStart: () -> Unit, onChoose: () -> Unit, onSettings: () -> Unit) {
    val container = appContainer()
    val viewModel = viewModel { HomeViewModel(container.repository) }
    val stats by viewModel.stats.collectAsStateWithLifecycle()
    val c = ElanTheme.colors
    val type = ElanTheme.type

    Column(Modifier.fillMaxSize()) {
        ElanTopBar(title = "TOEIC 900") {
            ElanIconButton(ElanIcons.Settings, "Réglages", onSettings)
        }
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = ElanDimens.gutter)
                .padding(bottom = ElanDimens.gapMajor),
            verticalArrangement = Arrangement.spacedBy(ElanDimens.gapSection),
        ) {
            ElanCard(style = CardStyle.Filled) {
                LabelSmall("Session du jour · Part 5", color = c.onPrimaryContainer)
                Spacer(Modifier.height(8.dp))
                Text("Phrases à compléter", style = type.headline, color = c.onPrimaryContainer)
                Spacer(Modifier.height(8.dp))
                HomeLine(ElanIcons.Refresh, "Compétences à réviser", stats.dueSkills.toString())
                HomeLine(ElanIcons.Target, "Compétences à découvrir", stats.newSkills.toString())
                Spacer(Modifier.height(12.dp))
                ElanButton("Commencer · 15 min", onQuickStart, Modifier.fillMaxWidth())
                ElanButton(
                    "Choisir la session",
                    onChoose,
                    Modifier.fillMaxWidth(),
                    style = ButtonStyle.Text,
                )
            }

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                ElanCard(Modifier.weight(1f)) {
                    LabelSmall("Aujourd'hui")
                    Spacer(Modifier.height(4.dp))
                    Text(stats.answeredToday.toString(), style = type.headline, color = c.onSurface)
                    Text(plural(stats.answeredToday, "question", "questions"), style = type.bodyM, color = c.muted)
                }
                ElanCard(Modifier.weight(1f)) {
                    LabelSmall("Depuis le début")
                    Spacer(Modifier.height(4.dp))
                    Text(stats.totalAnswered.toString(), style = type.headline, color = c.onSurface)
                    val detail = if (stats.totalAnswered == 0) {
                        "questions"
                    } else {
                        "${(100f * stats.totalCorrect / stats.totalAnswered).roundToInt()} % de réussite"
                    }
                    Text(detail, style = type.bodyM, color = c.muted)
                }
            }

            Text(
                "Chaque compétence avance dans cinq boîtes : une bonne réponse le jour de la révision " +
                    "l'envoie dans la boîte suivante (revue 2, 4, 8 puis 16 jours plus tard), une erreur " +
                    "la ramène à la première.",
                style = type.bodyM,
                color = c.muted,
            )
        }
    }
}

@Composable
private fun HomeLine(icon: ImageVector, label: String, value: String) {
    val ink = ElanTheme.colors.onPrimaryContainer
    Row(
        modifier = Modifier.fillMaxWidth().height(40.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Icon(icon, contentDescription = null, tint = ink, modifier = Modifier.size(18.dp))
        Text(label, style = ElanTheme.type.bodyL, color = ink, modifier = Modifier.weight(1f))
        Text(value, style = ElanTheme.type.labelL, color = ink)
    }
}

fun plural(count: Int, singular: String, plural: String) = if (count < 2) singular else plural
