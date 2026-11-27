package com.example.vault

import android.content.Context
import android.content.SharedPreferences
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.compose.runtime.Immutable
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import java.util.UUID
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject

@Immutable
data class VaultCredentialMeta(
  val id: String,
  val packageName: String,
  val appLabel: String,
  val username: String,
  val updatedAtMs: Long,
)

/**
 * Hardware-backed On-Device Password Vault using Android Keystore (AES256_GCM) and
 * EncryptedSharedPreferences. Never transmits, logs, or syncs credentials to any cloud API.
 */
class LocalPasswordVault(context: Context) {

  private val prefs: SharedPreferences = createEncryptedVaultPrefs(context.applicationContext)

  private val _credentialsFlow = MutableStateFlow(readAllMetadata())
  val credentialsFlow: StateFlow<List<VaultCredentialMeta>> = _credentialsFlow.asStateFlow()

  private val _biometricLockFlow =
    MutableStateFlow(prefs.getBoolean(KEY_BIOMETRIC_LOCK_ENABLED, true))
  val biometricLockFlow: StateFlow<Boolean> = _biometricLockFlow.asStateFlow()

  fun saveCredential(
    packageName: String,
    appLabel: String,
    username: String,
    passwordChars: CharArray,
  ): VaultCredentialMeta {
    val cleanUser = username.trim()
    val existing =
      _credentialsFlow.value.firstOrNull {
        it.packageName == packageName && it.username.equals(cleanUser, ignoreCase = true)
      }
    val id = existing?.id ?: UUID.randomUUID().toString()
    val meta =
      VaultCredentialMeta(
        id = id,
        packageName = packageName,
        appLabel = appLabel,
        username = cleanUser,
        updatedAtMs = System.currentTimeMillis(),
      )

    // Store secret password encrypted under dedicated key
    val secretValue = String(passwordChars)
    prefs.edit().putString("$SECRET_PREFIX$id", secretValue).apply()
    passwordChars.fill('\u0000')

    val updatedList =
      (listOf(meta) + _credentialsFlow.value.filterNot { it.id == id })
        .sortedBy { it.appLabel.lowercase() }
    writeAllMetadata(updatedList)
    _credentialsFlow.value = updatedList
    return meta
  }

  fun findCredentialForApp(
    packageName: String,
    accountHint: String? = null,
  ): VaultCredentialMeta? {
    val matching = _credentialsFlow.value.filter { it.packageName == packageName }
    if (matching.isEmpty()) return null
    if (!accountHint.isNullOrBlank()) {
      matching.firstOrNull { it.username.contains(accountHint, ignoreCase = true) }?.let {
        return it
      }
    }
    return matching.first()
  }

  /**
   * Reads the encrypted password into a mutable CharArray in temporary RAM.
   * Callers MUST wipe the returned CharArray (`fill('\u0000')`) immediately after use.
   */
  fun readPasswordIntoTempRam(credentialId: String): CharArray? {
    val raw = prefs.getString("$SECRET_PREFIX$credentialId", null) ?: return null
    return raw.toCharArray()
  }

  fun deleteCredential(credentialId: String) {
    prefs.edit().remove("$SECRET_PREFIX$credentialId").apply()
    val updated = _credentialsFlow.value.filterNot { it.id == credentialId }
    writeAllMetadata(updated)
    _credentialsFlow.value = updated
  }

  fun setBiometricLockEnabled(enabled: Boolean) {
    prefs.edit().putBoolean(KEY_BIOMETRIC_LOCK_ENABLED, enabled).apply()
    _biometricLockFlow.value = enabled
  }

  private fun readAllMetadata(): List<VaultCredentialMeta> {
    val rawJson = prefs.getString(KEY_METADATA_INDEX, null) ?: return emptyList()
    return runCatching {
      val array = JSONArray(rawJson)
      buildList {
        for (i in 0 until array.length()) {
          val obj = array.getJSONObject(i)
          add(
            VaultCredentialMeta(
              id = obj.getString("id"),
              packageName = obj.getString("packageName"),
              appLabel = obj.getString("appLabel"),
              username = obj.getString("username"),
              updatedAtMs = obj.optLong("updatedAtMs", 0L),
            ),
          )
        }
      }
    }.getOrDefault(emptyList())
  }

  private fun writeAllMetadata(items: List<VaultCredentialMeta>) {
    val array = JSONArray()
    items.forEach { item ->
      array.put(
        JSONObject()
          .put("id", item.id)
          .put("packageName", item.packageName)
          .put("appLabel", item.appLabel)
          .put("username", item.username)
          .put("updatedAtMs", item.updatedAtMs),
      )
    }
    prefs.edit().putString(KEY_METADATA_INDEX, array.toString()).apply()
  }

  private fun createEncryptedVaultPrefs(appContext: Context): SharedPreferences {
    val masterKey =
      MasterKey.Builder(appContext)
        .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
        .build()

    return EncryptedSharedPreferences.create(
      appContext,
      VAULT_PREFS_NAME,
      masterKey,
      EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
      EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
    )
  }

  companion object {
    private const val VAULT_PREFS_NAME = "vesper_hardware_password_vault"
    private const val KEY_METADATA_INDEX = "vault_metadata_index"
    private const val SECRET_PREFIX = "vault_secret_"
    private const val KEY_BIOMETRIC_LOCK_ENABLED = "vault_biometric_lock_enabled"

    fun authenticateWithBiometrics(
      activity: FragmentActivity,
      title: String,
      subtitle: String,
      onSuccess: () -> Unit,
      onError: (String) -> Unit,
    ) {
      val authenticators =
        BiometricManager.Authenticators.BIOMETRIC_STRONG or
          BiometricManager.Authenticators.BIOMETRIC_WEAK or
          BiometricManager.Authenticators.DEVICE_CREDENTIAL

      val canAuth = BiometricManager.from(activity).canAuthenticate(authenticators)
      if (canAuth != BiometricManager.BIOMETRIC_SUCCESS) {
        // Device without enrolled screen lock / biometrics: allow with warning fallback or fail cleanly
        if (canAuth == BiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED) {
          onSuccess()
        } else {
          onError("Biometric or device credential hardware unavailable.")
        }
        return
      }

      val executor = ContextCompat.getMainExecutor(activity)
      val prompt =
        BiometricPrompt(
          activity,
          executor,
          object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
              super.onAuthenticationSucceeded(result)
              onSuccess()
            }

            override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
              super.onAuthenticationError(errorCode, errString)
              if (errorCode != BiometricPrompt.ERROR_USER_CANCELED &&
                errorCode != BiometricPrompt.ERROR_NEGATIVE_BUTTON
              ) {
                onError(errString.toString())
              }
            }
          },
        )

      val promptInfo =
        BiometricPrompt.PromptInfo.Builder()
          .setTitle(title)
          .setSubtitle(subtitle)
          .setAllowedAuthenticators(authenticators)
          .build()

      prompt.authenticate(promptInfo)
    }
  }
}
