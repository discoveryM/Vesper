package com.example.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.appVaultDataStore: DataStore<Preferences> by
  preferencesDataStore(name = "vesper_app_vault")

/**
 * Persists per-app access opt-outs (all installed user apps allowed by default)
 * and remembered user selections for ambiguous multi-app intents.
 */
class AppVaultStore(private val context: Context) {

  private object Keys {
    val DISABLED_PACKAGES = stringSetPreferencesKey("disabled_packages")
    fun rememberedChoiceKey(taskKey: String) = stringPreferencesKey("choice_$taskKey")
  }

  val disabledPackagesFlow: Flow<Set<String>> =
    context.appVaultDataStore.data.map { prefs -> prefs[Keys.DISABLED_PACKAGES] ?: emptySet() }

  suspend fun getDisabledPackages(): Set<String> = disabledPackagesFlow.first()

  suspend fun setAppEnabled(packageName: String, enabled: Boolean) {
    context.appVaultDataStore.edit { prefs ->
      val current = prefs[Keys.DISABLED_PACKAGES] ?: emptySet()
      prefs[Keys.DISABLED_PACKAGES] =
        if (enabled) current - packageName else current + packageName
    }
  }

  suspend fun getRememberedChoice(taskKey: String): String? =
    context.appVaultDataStore.data.first()[Keys.rememberedChoiceKey(taskKey)]

  suspend fun saveRememberedChoice(taskKey: String, packageName: String) {
    context.appVaultDataStore.edit { prefs ->
      prefs[Keys.rememberedChoiceKey(taskKey)] = packageName
    }
  }
}
