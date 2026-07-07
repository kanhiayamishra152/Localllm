package com.localllm.app.data.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.dataStore by preferencesDataStore(name = "neuraltask_prefs")

/**
 * Persists user preferences:
 * - Theme mode (0 = System, 1 = Light, 2 = Dark)
 * - Cloud API keys (Google Gemini / OpenAI-compatible) for remote LLM + background AI fallback
 * - Third-party integration toggles (GitHub, Gmail, Telegram, WhatsApp)
 */
@Singleton
class UserPreferencesRepository @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val dataStore: DataStore<Preferences> = context.dataStore

    companion object {
        val THEME_MODE = intPreferencesKey("theme_mode")
        val API_GEMINI = stringPreferencesKey("api_gemini")
        val API_OPENAI = stringPreferencesKey("api_openai")
        val API_BASE_URL = stringPreferencesKey("api_base_url")
        val INTEGRATION_GITHUB = booleanPreferencesKey("int_github")
        val INTEGRATION_GMAIL = booleanPreferencesKey("int_gmail")
        val INTEGRATION_TELEGRAM = booleanPreferencesKey("int_telegram")
        val INTEGRATION_WHATSAPP = booleanPreferencesKey("int_whatsapp")
    }

    val themeMode: Flow<Int> = dataStore.data.map { it[THEME_MODE] ?: 0 }
    suspend fun setThemeMode(mode: Int) = dataStore.edit { it[THEME_MODE] = mode }

    val geminiApiKey: Flow<String> = dataStore.data.map { it[API_GEMINI].orEmpty() }
    suspend fun setGeminiApiKey(v: String) = dataStore.edit { it[API_GEMINI] = v }

    val openAiApiKey: Flow<String> = dataStore.data.map { it[API_OPENAI].orEmpty() }
    suspend fun setOpenAiApiKey(v: String) = dataStore.edit { it[API_OPENAI] = v }

    val openAiBaseUrl: Flow<String> = dataStore.data.map { it[API_BASE_URL].orEmpty() }
    suspend fun setOpenAiBaseUrl(v: String) = dataStore.edit { it[API_BASE_URL] = v }

    val githubEnabled: Flow<Boolean> = dataStore.data.map { it[INTEGRATION_GITHUB] ?: false }
    suspend fun setGithubEnabled(v: Boolean) = dataStore.edit { it[INTEGRATION_GITHUB] = v }

    val gmailEnabled: Flow<Boolean> = dataStore.data.map { it[INTEGRATION_GMAIL] ?: false }
    suspend fun setGmailEnabled(v: Boolean) = dataStore.edit { it[INTEGRATION_GMAIL] = v }

    val telegramEnabled: Flow<Boolean> = dataStore.data.map { it[INTEGRATION_TELEGRAM] ?: false }
    suspend fun setTelegramEnabled(v: Boolean) = dataStore.edit { it[INTEGRATION_TELEGRAM] = v }

    val whatsappEnabled: Flow<Boolean> = dataStore.data.map { it[INTEGRATION_WHATSAPP] ?: false }
    suspend fun setWhatsappEnabled(v: Boolean) = dataStore.edit { it[INTEGRATION_WHATSAPP] = v }
}
