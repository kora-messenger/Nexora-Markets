package com.nexoratech.markets.data

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

/**
 * Encrypted local settings. The AI API key never touches plain storage —
 * everything lives in an EncryptedSharedPreferences file.
 */
class SettingsStore(context: Context) {

    private val prefs: SharedPreferences = EncryptedSharedPreferences.create(
        context,
        "nexora_secure_prefs",
        MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build(),
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
    )

    var aiApiKey: String
        get() = prefs.getString(KEY_AI_API_KEY, "").orEmpty()
        set(value) = prefs.edit().putString(KEY_AI_API_KEY, value).apply()

    var aiBaseUrl: String
        get() = prefs.getString(KEY_AI_BASE_URL, DEFAULT_BASE_URL).orEmpty()
        set(value) = prefs.edit().putString(KEY_AI_BASE_URL, value).apply()

    var aiModel: String
        get() = prefs.getString(KEY_AI_MODEL, DEFAULT_MODEL).orEmpty()
        set(value) = prefs.edit().putString(KEY_AI_MODEL, value).apply()

    var cryptoWatchlist: List<String>
        get() = prefs.getStringSet(KEY_CRYPTO, DEFAULT_CRYPTO)?.toList() ?: DEFAULT_CRYPTO.toList()
        set(value) = prefs.edit().putStringSet(KEY_CRYPTO, value.toSet()).apply()

    var forexWatchlist: List<String>
        get() = prefs.getStringSet(KEY_FOREX, DEFAULT_FOREX)?.toList() ?: DEFAULT_FOREX.toList()
        set(value) = prefs.edit().putStringSet(KEY_FOREX, value.toSet()).apply()

    /** When true, AI analysis runs on the Nexora Cloud backend (Base44)
     *  instead of calling the provider directly with the user's own key. */
    var useCloud: Boolean
        get() = prefs.getBoolean(KEY_USE_CLOUD, true)
        set(value) = prefs.edit().putBoolean(KEY_USE_CLOUD, value).apply()

    fun hasAiKey(): Boolean = aiApiKey.isNotBlank()

    companion object {
        private const val KEY_AI_API_KEY = "ai_api_key"
        private const val KEY_AI_BASE_URL = "ai_base_url"
        private const val KEY_AI_MODEL = "ai_model"
        private const val KEY_CRYPTO = "watch_crypto"
        private const val KEY_FOREX = "watch_forex"
        private const val KEY_USE_CLOUD = "use_cloud"
        private const val DEFAULT_BASE_URL = "https://api.openai.com/v1"
        private const val DEFAULT_MODEL = "gpt-4o-mini"

        val DEFAULT_CRYPTO = setOf("BTCUSDT", "ETHUSDT", "SOLUSDT", "BNBUSDT", "XRPUSDT")
        val DEFAULT_FOREX = setOf("EURUSD", "GBPUSD", "USDJPY", "USDNGN", "USDEUR")
    }
}
