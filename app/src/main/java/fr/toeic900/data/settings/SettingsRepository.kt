package fr.toeic900.data.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

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

private val Context.settingsDataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

/** Réglages de l'app, conservés sur le téléphone par DataStore (un petit fichier clé-valeur). */
class SettingsRepository(private val dataStore: DataStore<Preferences>) {
    constructor(context: Context) : this(context.applicationContext.settingsDataStore)

    val explanationLanguage: Flow<ExplanationLanguage> =
        dataStore.data.map { ExplanationLanguage.fromCode(it[KEY_EXPLANATION_LANGUAGE]) }

    suspend fun setExplanationLanguage(language: ExplanationLanguage) {
        dataStore.edit { it[KEY_EXPLANATION_LANGUAGE] = language.code }
    }

    private companion object {
        val KEY_EXPLANATION_LANGUAGE = stringPreferencesKey("explanation_language")
    }
}
