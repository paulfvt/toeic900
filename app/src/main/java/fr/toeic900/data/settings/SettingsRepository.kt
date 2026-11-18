package fr.toeic900.data.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import fr.toeic900.domain.DailyGoal
import fr.toeic900.domain.GoalType
import fr.toeic900.domain.WeeklyStreak

/** Langue des explications dans les corrections. Les questions, elles, sont toujours en anglais. */
enum class ExplanationLanguage(val code: String) {
    FRENCH("fr"),
    ENGLISH("en"),
    ;

    companion object {
        val DEFAULT = FRENCH
        fun fromCode(code: String?) = entries.firstOrNull { it.code == code } ?: DEFAULT
    }
}

/** Réglages de la motivation (Phase 3). */
data class ProgressSettings(
    val goal: DailyGoal = DailyGoal.DEFAULT,
    /** Jours d'objectif atteint qu'il faut pour valider une semaine. */
    val streakThreshold: Int = WeeklyStreak.DEFAULT_THRESHOLD,
    val sounds: Boolean = true,
    val haptics: Boolean = true,
    val reminderEnabled: Boolean = false,
    /** Heure du rappel, en minutes depuis minuit (19 h 00 par défaut). */
    val reminderMinuteOfDay: Int = 19 * 60,
)

private val Context.settingsDataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

/** Réglages de l'app, conservés sur le téléphone par DataStore (un petit fichier clé-valeur). */
class SettingsRepository(private val dataStore: DataStore<Preferences>) {
    constructor(context: Context) : this(context.applicationContext.settingsDataStore)

    val explanationLanguage: Flow<ExplanationLanguage> =
        dataStore.data.map { ExplanationLanguage.fromCode(it[KEY_EXPLANATION_LANGUAGE]) }

    suspend fun setExplanationLanguage(language: ExplanationLanguage) {
        dataStore.edit { it[KEY_EXPLANATION_LANGUAGE] = language.code }
    }

    val progress: Flow<ProgressSettings> = dataStore.data.map { prefs ->
        val type = GoalType.entries.firstOrNull { it.name == prefs[KEY_GOAL_TYPE] } ?: DailyGoal.DEFAULT.type
        val target = prefs[KEY_GOAL_TARGET]?.takeIf { it > 0 } ?: DailyGoal.DEFAULT.target
        ProgressSettings(
            goal = DailyGoal(type, target),
            streakThreshold = (prefs[KEY_STREAK_THRESHOLD] ?: WeeklyStreak.DEFAULT_THRESHOLD)
                .coerceIn(WeeklyStreak.THRESHOLD_RANGE.first, WeeklyStreak.THRESHOLD_RANGE.last),
            sounds = prefs[KEY_SOUNDS] ?: true,
            haptics = prefs[KEY_HAPTICS] ?: true,
            reminderEnabled = prefs[KEY_REMINDER_ENABLED] ?: false,
            reminderMinuteOfDay = (prefs[KEY_REMINDER_MINUTE] ?: (19 * 60)).coerceIn(0, 24 * 60 - 1),
        )
    }

    suspend fun setGoal(goal: DailyGoal) {
        dataStore.edit {
            it[KEY_GOAL_TYPE] = goal.type.name
            it[KEY_GOAL_TARGET] = goal.target
        }
    }

    suspend fun setStreakThreshold(days: Int) {
        dataStore.edit { it[KEY_STREAK_THRESHOLD] = days.coerceIn(WeeklyStreak.THRESHOLD_RANGE.first, WeeklyStreak.THRESHOLD_RANGE.last) }
    }

    suspend fun setSounds(enabled: Boolean) {
        dataStore.edit { it[KEY_SOUNDS] = enabled }
    }

    suspend fun setHaptics(enabled: Boolean) {
        dataStore.edit { it[KEY_HAPTICS] = enabled }
    }

    suspend fun setReminder(enabled: Boolean, minuteOfDay: Int) {
        dataStore.edit {
            it[KEY_REMINDER_ENABLED] = enabled
            it[KEY_REMINDER_MINUTE] = minuteOfDay.coerceIn(0, 24 * 60 - 1)
        }
    }

    private companion object {
        val KEY_EXPLANATION_LANGUAGE = stringPreferencesKey("explanation_language")
        val KEY_GOAL_TYPE = stringPreferencesKey("goal_type")
        val KEY_GOAL_TARGET = intPreferencesKey("goal_target")
        val KEY_STREAK_THRESHOLD = intPreferencesKey("streak_threshold")
        val KEY_SOUNDS = booleanPreferencesKey("sounds")
        val KEY_HAPTICS = booleanPreferencesKey("haptics")
        val KEY_REMINDER_ENABLED = booleanPreferencesKey("reminder_enabled")
        val KEY_REMINDER_MINUTE = intPreferencesKey("reminder_minute")
    }
}
