package fr.toeic900.ui.settings

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TimePicker
import androidx.compose.material3.TimePickerDefaults
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import fr.toeic900.data.settings.ExplanationLanguage
import fr.toeic900.data.settings.ProgressSettings
import fr.toeic900.domain.DailyGoal
import fr.toeic900.domain.GoalType
import fr.toeic900.domain.ReminderTime
import fr.toeic900.reminder.Reminder
import fr.toeic900.ui.appContainer
import fr.toeic900.ui.components.ButtonStyle
import fr.toeic900.ui.components.ElanButton
import fr.toeic900.ui.components.ElanChip
import fr.toeic900.ui.components.ElanTopBar
import fr.toeic900.ui.components.LabelSmall
import fr.toeic900.ui.components.OptionRow
import fr.toeic900.ui.components.OptionState
import fr.toeic900.ui.theme.ElanDimens
import fr.toeic900.ui.theme.ElanIcons
import fr.toeic900.ui.theme.ElanTheme
import kotlinx.coroutines.launch

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SettingsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val settings = appContainer().settings
    val language by settings.explanationLanguage.collectAsStateWithLifecycle(ExplanationLanguage.DEFAULT)
    val progress by settings.progress.collectAsStateWithLifecycle(ProgressSettings())
    val scope = rememberCoroutineScope()
    val type = ElanTheme.type
    val c = ElanTheme.colors
    var pickTime by remember { mutableStateOf(false) }

    fun setReminder(enabled: Boolean, minute: Int = progress.reminderMinuteOfDay) {
        scope.launch {
            settings.setReminder(enabled, minute)
            Reminder.schedule(context, enabled, minute)
        }
    }
    // Android 13 et plus : il faut l'accord de l'utilisateur pour afficher une notification.
    val askPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        setReminder(granted)
    }

    if (pickTime) {
        ReminderTimeDialog(progress.reminderMinuteOfDay, onDismiss = { pickTime = false }) { minute ->
            pickTime = false
            setReminder(true, minute)
        }
    }

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
            LabelSmall("Objectif du jour")
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                ElanChip("En questions", selected = progress.goal.type == GoalType.QUESTIONS, onClick = {
                    scope.launch { settings.setGoal(DailyGoal(GoalType.QUESTIONS, DailyGoal.DEFAULT.target)) }
                })
                ElanChip("En minutes", selected = progress.goal.type == GoalType.MINUTES, onClick = {
                    scope.launch { settings.setGoal(DailyGoal(GoalType.MINUTES, 15)) }
                })
            }
            val choices = if (progress.goal.type == GoalType.QUESTIONS) DailyGoal.QUESTION_CHOICES else DailyGoal.MINUTE_CHOICES
            val unit = if (progress.goal.type == GoalType.QUESTIONS) "questions" else "min"
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                choices.forEach { n ->
                    ElanChip("$n $unit", selected = progress.goal.target == n, onClick = {
                        scope.launch { settings.setGoal(DailyGoal(progress.goal.type, n)) }
                    })
                }
            }
            Text(
                "En minutes, seul compte le temps passé sur les questions (5 minutes au plus par question). " +
                    "Changer d'objectif ne retire aucun jour déjà validé.",
                style = type.bodyM,
                color = c.muted,
            )

            Spacer(Modifier.height(8.dp))
            LabelSmall("Semaine validée")
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                (3..7).forEach { n ->
                    ElanChip("$n jours", selected = progress.streakThreshold == n, onClick = { scope.launch { settings.setStreakThreshold(n) } })
                }
            }
            Text(
                "La série compte les semaines (du lundi au dimanche) où l'objectif du jour est atteint au moins ce nombre de jours.",
                style = type.bodyM,
                color = c.muted,
            )

            Spacer(Modifier.height(8.dp))
            LabelSmall("Rappel")
            SwitchRow("Rappel quotidien", progress.reminderEnabled) { on ->
                if (on && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !Reminder.canNotify(context)) {
                    askPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
                } else {
                    setReminder(on)
                }
            }
            if (progress.reminderEnabled) {
                ElanButton("Heure : ${ReminderTime.label(progress.reminderMinuteOfDay)}", { pickTime = true }, Modifier.fillMaxWidth(), style = ButtonStyle.Outlined)
            }
            Text(
                "Une notification par jour à l'heure choisie, seulement si l'objectif du jour n'est pas encore atteint. " +
                    "Android peut la décaler de quelques minutes pour économiser la batterie.",
                style = type.bodyM,
                color = c.muted,
            )

            Spacer(Modifier.height(8.dp))
            LabelSmall("Sons et vibrations")
            SwitchRow("Sons des réponses", progress.sounds) { scope.launch { settings.setSounds(it) } }
            SwitchRow("Vibrations", progress.haptics) { scope.launch { settings.setHaptics(it) } }

            Spacer(Modifier.height(8.dp))
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
                style = type.bodyM,
                color = c.muted,
            )
        }
    }
}

@Composable
private fun SwitchRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    val c = ElanTheme.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = ElanDimens.touchTarget)
            .clickable(role = Role.Switch) { onChange(!checked) },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = ElanTheme.type.bodyL, color = c.onSurface, modifier = Modifier.weight(1f))
        Switch(
            checked = checked,
            onCheckedChange = null,
            colors = SwitchDefaults.colors(
                checkedThumbColor = c.onPrimary,
                checkedTrackColor = c.primary,
                uncheckedThumbColor = c.muted,
                uncheckedTrackColor = c.surfaceContainerHigh,
                uncheckedBorderColor = c.outline,
            ),
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ReminderTimeDialog(minuteOfDay: Int, onDismiss: () -> Unit, onConfirm: (Int) -> Unit) {
    val c = ElanTheme.colors
    val state = rememberTimePickerState(initialHour = minuteOfDay / 60, initialMinute = minuteOfDay % 60, is24Hour = true)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Heure du rappel", style = ElanTheme.type.titleL) },
        text = {
            TimePicker(
                state = state,
                colors = TimePickerDefaults.colors(
                    clockDialColor = c.surfaceContainerHigh,
                    selectorColor = c.primary,
                    timeSelectorSelectedContainerColor = c.primaryContainer,
                    timeSelectorSelectedContentColor = c.onPrimaryContainer,
                    timeSelectorUnselectedContainerColor = c.surfaceContainer,
                    timeSelectorUnselectedContentColor = c.onSurface,
                ),
            )
        },
        confirmButton = { ElanButton("OK", { onConfirm(state.hour * 60 + state.minute) }, style = ButtonStyle.Text) },
        dismissButton = { ElanButton("Annuler", onDismiss, style = ButtonStyle.Text) },
        containerColor = c.surface,
    )
}
