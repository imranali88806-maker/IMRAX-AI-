package com.example.data.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.data.model.AssistantLanguage
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "imrax_prefs")

data class LocalAppSettings(
    val darkTheme: Boolean = true,
    val preferredLanguage: AssistantLanguage = AssistantLanguage.ENGLISH,
    val autoSpeakResponses: Boolean = false
)

class AppPreferencesRepository(private val context: Context) {

    private val darkThemeKey = booleanPreferencesKey("dark_theme")
    private val languageKey = stringPreferencesKey("preferred_language")
    private val autoSpeakKey = booleanPreferencesKey("auto_speak_responses")

    val settingsFlow: Flow<LocalAppSettings> = context.dataStore.data.map { prefs ->
        LocalAppSettings(
            darkTheme = prefs[darkThemeKey] ?: true,
            preferredLanguage = AssistantLanguage.fromCode(prefs[languageKey]),
            autoSpeakResponses = prefs[autoSpeakKey] ?: false
        )
    }

    suspend fun setDarkTheme(enabled: Boolean) {
        context.dataStore.edit { it[darkThemeKey] = enabled }
    }

    suspend fun setPreferredLanguage(language: AssistantLanguage) {
        context.dataStore.edit { it[languageKey] = language.code }
    }

    suspend fun setAutoSpeakResponses(enabled: Boolean) {
        context.dataStore.edit { it[autoSpeakKey] = enabled }
    }
}
