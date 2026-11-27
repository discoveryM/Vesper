package com.example.data

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.Immutable
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

@Immutable
data class FreeAiKeys(
  val geminiKey: String = "",
  val openRouterOrGroqKey: String = "",
) {
  val hasGeminiKey: Boolean get() = geminiKey.isNotBlank()
  val hasOpenRouterOrGroqKey: Boolean get() = openRouterOrGroqKey.isNotBlank()
  val hasAnyKey: Boolean get() = hasGeminiKey || hasOpenRouterOrGroqKey
  val isGroqKey: Boolean get() = openRouterOrGroqKey.trim().startsWith("gsk_", ignoreCase = true)
}

/**
 * Stores user-entered free-tier API keys exclusively on-device using hardware-backed
 * EncryptedSharedPreferences (AES256_GCM). Never logs or transmits keys outside direct API calls.
 */
class FreeAiKeysStore(context: Context) {

  private val prefs: SharedPreferences = createEncryptedPrefs(context.applicationContext)
  private val _keysFlow = MutableStateFlow(readKeys())
  val keysFlow: StateFlow<FreeAiKeys> = _keysFlow.asStateFlow()

  fun getKeys(): FreeAiKeys = _keysFlow.value

  fun saveKeys(geminiKey: String, openRouterOrGroqKey: String) {
    val cleanGemini = geminiKey.trim()
    val cleanOpenRouter = openRouterOrGroqKey.trim()
    prefs.edit()
      .putString(KEY_GEMINI, cleanGemini)
      .putString(KEY_OPENROUTER_GROQ, cleanOpenRouter)
      .apply()
    _keysFlow.value = FreeAiKeys(geminiKey = cleanGemini, openRouterOrGroqKey = cleanOpenRouter)
  }

  fun clearKeys() {
    prefs.edit().clear().apply()
    _keysFlow.value = FreeAiKeys()
  }

  private fun readKeys(): FreeAiKeys =
    FreeAiKeys(
      geminiKey = prefs.getString(KEY_GEMINI, "").orEmpty(),
      openRouterOrGroqKey = prefs.getString(KEY_OPENROUTER_GROQ, "").orEmpty(),
    )

  private fun createEncryptedPrefs(appContext: Context): SharedPreferences {
    val masterKey =
      MasterKey.Builder(appContext)
        .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
        .build()

    return EncryptedSharedPreferences.create(
      appContext,
      PREFS_FILE_NAME,
      masterKey,
      EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
      EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
    )
  }

  private companion object {
    const val PREFS_FILE_NAME = "vesper_encrypted_free_ai_keys"
    const val KEY_GEMINI = "gemini_free_api_key"
    const val KEY_OPENROUTER_GROQ = "openrouter_or_groq_free_key"
  }
}
