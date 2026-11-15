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
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import fr.toeic900.AppContainer
import fr.toeic900.ContentStatus
import fr.toeic900.Toeic900App
import fr.toeic900.data.TrainingRepository
import fr.toeic900.ui.components.BottomTab
import fr.toeic900.ui.components.ElanBottomBar
import fr.toeic900.ui.home.HomeScreen
import fr.toeic900.ui.lessons.LessonListScreen
import fr.toeic900.ui.lessons.LessonScreen
import fr.toeic900.ui.lessons.StepIntroScreen
import fr.toeic900.ui.path.PathScreen
import fr.toeic900.ui.settings.SettingsScreen
import fr.toeic900.ui.theme.ElanIcons
import fr.toeic900.ui.theme.ElanTheme
import fr.toeic900.ui.training.SessionSummaryScreen
import fr.toeic900.ui.training.TrainingScreen
import fr.toeic900.ui.training.TrainingSetupScreen
import kotlinx.serialization.Serializable

// Les « routes » : une par écran. Leurs champs sont les paramètres passés à l'écran.

// Les trois onglets de la barre du bas.
@Serializable data object HomeRoute
@Serializable data object PathRoute
@Serializable data object ExercisesRoute

/**
 * Une session de questions. Entraînement libre : part (0 = toutes), compétence et durée.
 * Étape du chemin : stepId, qui fixe le reste.
 */
@Serializable
data class TrainingRoute(
    val part: Int = TrainingRepository.ALL_PARTS,
    val focusTag: String? = null,
    val minutes: Int = 15,
    val stepId: String? = null,
)

@Serializable data class SummaryRoute(val sessionId: Long)
@Serializable data object SettingsRoute
@Serializable data object LessonListRoute

/** Une fiche de cours. canPractice = faux quand elle est ouverte depuis une session en cours. */
@Serializable data class LessonRoute(val tagId: String, val canPractice: Boolean = true)

/** Le début d'une étape du chemin : mini-fiche, puis exercices. */
@Serializable data class StepIntroRoute(val stepId: String)

private val TABS = listOf(
    BottomTab("Accueil", ElanIcons.Home) to HomeRoute,
    BottomTab("Chemin", ElanIcons.Map) to PathRoute,
    BottomTab("Exercices", ElanIcons.Target) to ExercisesRoute,
)

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

/** Change d'onglet : on revient à la racine, et chaque onglet retrouve l'état où on l'avait laissé. */
private fun NavHostController.goToTab(route: Any) = navigate(route) {
    popUpTo<HomeRoute> { saveState = true }
    launchSingleTop = true
    restoreState = true
}

/** Retour à l'onglet Chemin, qu'il soit déjà dans la pile d'écrans ou non. */
private fun NavHostController.backToPath() {
    if (!popBackStack<PathRoute>(inclusive = false)) goToTab(PathRoute)
}

@Composable
private fun AppNavHost() {
    val nav = rememberNavController()
    val entry by nav.currentBackStackEntryAsState()
    val destination = entry?.destination
    // La barre du bas n'apparaît que sur les trois onglets, pas pendant une session ou une fiche.
    val tabIndex = when {
        destination == null -> -1
        destination.hasRoute<HomeRoute>() -> 0
        destination.hasRoute<PathRoute>() -> 1
        destination.hasRoute<ExercisesRoute>() -> 2
        else -> -1
    }

    Column(Modifier.fillMaxSize()) {
        NavHost(navController = nav, startDestination = HomeRoute, modifier = Modifier.weight(1f)) {
            composable<HomeRoute> {
                HomeScreen(
                    onQuickStart = { nav.navigate(TrainingRoute(part = TrainingRepository.ALL_PARTS, minutes = 15)) },
                    onOpenStep = { stepId -> nav.navigate(StepIntroRoute(stepId)) },
                    onPath = { nav.goToTab(PathRoute) },
                    onLessons = { nav.navigate(LessonListRoute) },
                    onSettings = { nav.navigate(SettingsRoute) },
                )
            }
            composable<PathRoute> {
                PathScreen(
                    onOpenStep = { stepId -> nav.navigate(StepIntroRoute(stepId)) },
                    onLessons = { nav.navigate(LessonListRoute) },
                )
            }
            composable<ExercisesRoute> {
                TrainingSetupScreen(onStart = { part, tag, minutes -> nav.navigate(TrainingRoute(part, tag, minutes)) })
            }
            composable<StepIntroRoute> { entry ->
                val stepId = entry.toRoute<StepIntroRoute>().stepId
                StepIntroScreen(
                    stepId = stepId,
                    onBack = { nav.popBackStack() },
                    onStart = { nav.navigate(TrainingRoute(stepId = stepId)) },
                    onOpenLesson = { tagId -> nav.navigate(LessonRoute(tagId, canPractice = false)) },
                )
            }
            composable<TrainingRoute> { entry ->
                val route = entry.toRoute<TrainingRoute>()
                TrainingScreen(
                    part = route.part,
                    focusTag = route.focusTag,
                    minutes = route.minutes,
                    stepId = route.stepId,
                    onFinished = { sessionId ->
                        // Le bilan remplace l'écran de questions : « retour » ne rouvre pas la session.
                        nav.navigate(SummaryRoute(sessionId)) { popUpTo<TrainingRoute> { inclusive = true } }
                    },
                    onAbandon = { nav.popBackStack() },
                    onOpenLesson = { tagId -> nav.navigate(LessonRoute(tagId, canPractice = false)) },
                )
            }
            composable<SummaryRoute> { entry ->
                SessionSummaryScreen(
                    sessionId = entry.toRoute<SummaryRoute>().sessionId,
                    onNewSession = { nav.goToTab(ExercisesRoute) },
                    onHome = { nav.popBackStack<HomeRoute>(inclusive = false) },
                    onPath = { nav.backToPath() },
                    onOpenStep = { stepId ->
                        nav.backToPath()
                        nav.navigate(StepIntroRoute(stepId))
                    },
                    onRetryStep = { stepId ->
                        nav.navigate(TrainingRoute(stepId = stepId)) { popUpTo<SummaryRoute> { inclusive = true } }
                    },
                )
            }
            composable<LessonListRoute> {
                LessonListScreen(onBack = { nav.popBackStack() }, onOpen = { tagId -> nav.navigate(LessonRoute(tagId)) })
            }
            composable<LessonRoute> { entry ->
                val route = entry.toRoute<LessonRoute>()
                LessonScreen(
                    tagId = route.tagId,
                    onBack = { nav.popBackStack() },
                    onPractice = if (route.canPractice) {
                        { nav.navigate(TrainingRoute(part = TrainingRepository.ALL_PARTS, focusTag = route.tagId, minutes = 0)) }
                    } else {
                        null
                    },
                )
            }
            composable<SettingsRoute> {
                SettingsScreen(onBack = { nav.popBackStack() })
            }
        }
        if (tabIndex >= 0) {
            ElanBottomBar(
                tabs = TABS.map { it.first },
                selectedIndex = tabIndex,
                onSelect = { index -> if (index != tabIndex) nav.goToTab(TABS[index].second) },
            )
        }
    }
}
