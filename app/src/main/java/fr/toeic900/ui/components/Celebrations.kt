package fr.toeic900.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.scaleIn
import androidx.compose.animation.expandVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import fr.toeic900.data.Achievements
import fr.toeic900.domain.Xp
import fr.toeic900.ui.theme.ElanIcons
import fr.toeic900.ui.theme.ElanShapes
import fr.toeic900.ui.theme.ElanTheme

/** L'icône de la correction : la coche rebondit, la croix tremble. */
@Composable
fun AnswerBadge(correct: Boolean, tint: Color) {
    val scale = remember { Animatable(if (correct) 0.4f else 1f) }
    val shift = remember { Animatable(0f) }
    LaunchedEffect(correct) {
        if (correct) {
            scale.animateTo(1f, spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium))
        } else {
            for (x in listOf(-6f, 6f, -4f, 4f, 0f)) shift.animateTo(x, tween(50))
        }
    }
    Icon(
        if (correct) ElanIcons.CheckCircle else ElanIcons.XCircle,
        contentDescription = null,
        tint = tint,
        modifier = Modifier
            .size(24.dp)
            .graphicsLayer {
                scaleX = scale.value
                scaleY = scale.value
                translationX = shift.value * density
            },
    )
}

/** « +10 XP » qui apparaît en grossissant. */
@Composable
fun XpPop(xp: Int) {
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { visible = true }
    AnimatedVisibility(visible, enter = fadeIn(tween(200)) + scaleIn(spring(dampingRatio = Spring.DampingRatioMediumBouncy), initialScale = 0.5f)) {
        Text(
            "+$xp XP",
            style = ElanTheme.type.chip,
            color = ElanTheme.colors.onAccentContainer,
            modifier = Modifier.background(ElanTheme.colors.accentContainer, ElanShapes.full).padding(horizontal = 10.dp, vertical = 4.dp),
        )
    }
}

/** Objectif du jour atteint, badges débloqués : un bandeau qui se déplie dans la correction. */
@Composable
fun AchievementBanner(achievements: Achievements) {
    AnimatedVisibility(
        visible = !achievements.isEmpty,
        enter = expandVertically(tween(240)) + fadeIn(tween(240)) + scaleIn(spring(dampingRatio = Spring.DampingRatioLowBouncy), initialScale = 0.85f),
    ) {
        val c = ElanTheme.colors
        val type = ElanTheme.type
        Column(
            modifier = Modifier.fillMaxWidth().background(c.accentContainer, ElanShapes.md).padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            if (achievements.goalReached) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(ElanIcons.Flame, contentDescription = null, tint = c.onAccentContainer, modifier = Modifier.size(20.dp))
                    Text("Objectif du jour atteint ! +${Xp.DAILY_GOAL} XP", style = type.titleM, color = c.onAccentContainer)
                }
            }
            achievements.badges.forEach { badge ->
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(ElanIcons.Award, contentDescription = null, tint = c.onAccentContainer, modifier = Modifier.size(20.dp))
                    Column {
                        Text("Nouveau badge : ${badge.titleFr}", style = type.titleM, color = c.onAccentContainer)
                        Text(badge.descriptionFr, style = type.bodyM, color = c.onAccentContainer)
                    }
                }
            }
        }
    }
}
