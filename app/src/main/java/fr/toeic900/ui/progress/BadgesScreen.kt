package fr.toeic900.ui.progress

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import fr.toeic900.data.BadgeView
import fr.toeic900.domain.BadgeCategory
import fr.toeic900.ui.appContainer
import fr.toeic900.ui.components.ElanCard
import fr.toeic900.ui.components.ElanProgress
import fr.toeic900.ui.components.ElanTopBar
import fr.toeic900.ui.components.LabelSmall
import fr.toeic900.ui.theme.ElanDimens
import fr.toeic900.ui.theme.ElanIcons
import fr.toeic900.ui.theme.ElanShapes
import fr.toeic900.ui.theme.ElanTheme

@Composable
fun BadgesScreen(onBack: () -> Unit) {
    val container = appContainer()
    val viewModel = viewModel { ProgressViewModel(container.progress) }
    val overview by viewModel.overview.collectAsStateWithLifecycle()
    // Ouvrir cet écran vaut annonce : les nouveaux badges ne sont plus signalés sur l'accueil.
    LaunchedEffect(Unit) { container.progress.markBadgesSeen() }

    Column(Modifier.fillMaxSize()) {
        ElanTopBar(title = "Badges", navigationIcon = ElanIcons.Back, navigationDescription = "Retour", onNavigate = onBack)
        val o = overview ?: return@Column
        Column(
            modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = ElanDimens.gutter).padding(bottom = ElanDimens.gapMajor),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("${o.badges.count { it.unlocked }} badges sur ${o.badges.size}", style = ElanTheme.type.titleM, color = ElanTheme.colors.muted)
            BadgeCategory.entries.forEach { category ->
                val badges = o.badges.filter { it.badge.category == category }
                if (badges.isNotEmpty()) {
                    Spacer(Modifier.height(4.dp))
                    LabelSmall(category.labelFr)
                    badges.forEach { BadgeRow(it) }
                }
            }
        }
    }
}

@Composable
private fun BadgeRow(view: BadgeView) {
    val c = ElanTheme.colors
    val type = ElanTheme.type
    val badge = view.badge
    ElanCard {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Box(
                Modifier.size(44.dp).background(if (view.unlocked) c.accentContainer else c.surfaceContainerHigh, ElanShapes.full),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    if (view.unlocked) ElanIcons.Award else ElanIcons.Lock,
                    contentDescription = if (view.unlocked) "Débloqué" else "Verrouillé",
                    tint = if (view.unlocked) c.onAccentContainer else c.muted,
                    modifier = Modifier.size(22.dp),
                )
            }
            Column(Modifier.weight(1f)) {
                Text(badge.titleFr, style = type.titleM, color = if (view.unlocked) c.onSurface else c.muted)
                Text(badge.descriptionFr, style = type.bodyM, color = c.muted)
                if (view.unlocked) {
                    Text("Débloqué le ${formatDay(view.unlockedDay!!)}", style = type.bodyM, color = c.successInk)
                } else if (badge.target > 1) {
                    Spacer(Modifier.height(6.dp))
                    ElanProgress(view.progress.toFloat() / badge.target, Modifier.fillMaxWidth())
                    Text("${minOf(view.progress, badge.target)} / ${badge.target}", style = type.bodyM, color = c.muted)
                }
            }
        }
    }
}
