package fr.toeic900.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import fr.toeic900.data.settings.ExplanationLanguage
import fr.toeic900.ui.appContainer
import fr.toeic900.ui.components.ElanTopBar
import fr.toeic900.ui.components.LabelSmall
import fr.toeic900.ui.components.OptionRow
import fr.toeic900.ui.components.OptionState
import fr.toeic900.ui.theme.ElanDimens
import fr.toeic900.ui.theme.ElanIcons
import fr.toeic900.ui.theme.ElanTheme
import kotlinx.coroutines.launch

@Composable
fun SettingsScreen(onBack: () -> Unit) {
    val settings = appContainer().settings
    val language by settings.explanationLanguage.collectAsStateWithLifecycle(ExplanationLanguage.DEFAULT)
    val scope = rememberCoroutineScope()

    Column(Modifier.fillMaxSize()) {
        ElanTopBar(title = "Réglages", navigationIcon = ElanIcons.Back, navigationDescription = "Retour", onNavigate = onBack)
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = ElanDimens.gutter)
                .padding(bottom = ElanDimens.gapMajor),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            LabelSmall("Langue des explications")
            OptionRow(
                letter = "FR",
                text = "Français",
                state = if (language == ExplanationLanguage.FRENCH) OptionState.Selected else OptionState.Default,
                onClick = { scope.launch { settings.setExplanationLanguage(ExplanationLanguage.FRENCH) } },
            )
            OptionRow(
                letter = "EN",
                text = "English",
                state = if (language == ExplanationLanguage.ENGLISH) OptionState.Selected else OptionState.Default,
                onClick = { scope.launch { settings.setExplanationLanguage(ExplanationLanguage.ENGLISH) } },
            )
            Text(
                "Ce réglage change la langue des corrections : la règle, la raison pour laquelle chaque " +
                    "autre réponse est fausse, et le piège à retenir. Les questions restent en anglais, comme à l'examen.",
                style = ElanTheme.type.bodyM,
                color = ElanTheme.colors.muted,
            )
        }
    }
}
