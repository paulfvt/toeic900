package fr.toeic900.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import fr.toeic900.AppContainer
import fr.toeic900.ContentStatus
import fr.toeic900.Toeic900App
import fr.toeic900.ui.home.HomeScreen
import fr.toeic900.ui.settings.SettingsScreen
import fr.toeic900.ui.theme.ElanTheme
import fr.toeic900.ui.training.SessionSummaryScreen
import fr.toeic900.ui.training.TrainingScreen
import fr.toeic900.ui.training.TrainingSetupScreen
import kotlinx.serialization.Serializable

// Les « routes » : une par écran. Leurs champs sont les paramètres passés à l'écran.
@Serializable data object HomeRoute
@Serializable data object SetupRoute
@Serializable data class TrainingRoute(val focusTag: String? = null, val minutes: Int = 15)
@Serializable data class SummaryRoute(val sessionId: Long)
@Serializable data object SettingsRoute

/** L'unique part disponible en Phase 1. */
const val PART_5 = 5

@Composable
fun appContainer(): AppContainer = (LocalContext.current.applicationContext as Toeic900App).container

@Composable
fun Toeic900Root() {
    val container = appContainer()
    val status by container.contentStatus.collectAsStateWithLifecycle()
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(ElanTheme.colors.bg)
            .windowInsetsPadding(WindowInsets.safeDrawing),
    ) {
        when (val s = status) {
            ContentStatus.Loading -> CenteredMessage("Préparation du contenu…")
            is ContentStatus.Failed -> CenteredMessage("Le contenu n'a pas pu être chargé.", s.message)
            ContentStatus.Ready -> AppNavHost()
        }
    }
}

@Composable
private fun CenteredMessage(title: String, detail: String? = null) {
    Column(
        modifier = Modifier.fillMaxSize().padding(32.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(title, style = ElanTheme.type.titleL, color = ElanTheme.colors.onBg, textAlign = TextAlign.Center)
        if (detail != null) {
            Text(detail, style = ElanTheme.type.bodyM, color = ElanTheme.colors.muted, textAlign = TextAlign.Center)
        }
    }
}

@Composable
private fun AppNavHost() {
    val nav = rememberNavController()
    NavHost(navController = nav, startDestination = HomeRoute) {
        composable<HomeRoute> {
            HomeScreen(
                onQuickStart = { nav.navigate(TrainingRoute(focusTag = null, minutes = 15)) },
                onChoose = { nav.navigate(SetupRoute) },
                onSettings = { nav.navigate(SettingsRoute) },
            )
        }
        composable<SetupRoute> {
            TrainingSetupScreen(
                onBack = { nav.popBackStack() },
                onStart = { tag, minutes -> nav.navigate(TrainingRoute(tag, minutes)) },
            )
        }
        composable<TrainingRoute> { entry ->
            val route = entry.toRoute<TrainingRoute>()
            TrainingScreen(
                focusTag = route.focusTag,
                minutes = route.minutes,
                onFinished = { sessionId ->
                    // Le bilan remplace l'écran de questions : « retour » ne rouvre pas la session.
                    nav.navigate(SummaryRoute(sessionId)) { popUpTo<TrainingRoute> { inclusive = true } }
                },
                onAbandon = { nav.popBackStack() },
            )
        }
        composable<SummaryRoute> { entry ->
            SessionSummaryScreen(
                sessionId = entry.toRoute<SummaryRoute>().sessionId,
                onNewSession = { nav.navigate(SetupRoute) { popUpTo<HomeRoute>() } },
                onHome = { nav.popBackStack<HomeRoute>(inclusive = false) },
            )
        }
        composable<SettingsRoute> {
            SettingsScreen(onBack = { nav.popBackStack() })
        }
    }
}
