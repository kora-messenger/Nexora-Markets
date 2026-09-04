package com.nexoratech.markets.auth

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

/**
 * Encrypted local account state. The session token never touches plain
 * storage — it lives in an EncryptedSharedPreferences vault alongside
 * the signed-in user's profile.
 */
class AccountStore(context: Context) {

    private val prefs: SharedPreferences = EncryptedSharedPreferences.create(
        context,
        "nexora_account_prefs",
        MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build(),
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
    )

    var sessionToken: String
        get() = prefs.getString(KEY_SESSION_TOKEN, "").orEmpty()
        set(value) = prefs.edit().putString(KEY_SESSION_TOKEN, value).apply()

    var userEmail: String
        get() = prefs.getString(KEY_USER_EMAIL, "").orEmpty()
        set(value) = prefs.edit().putString(KEY_USER_EMAIL, value).apply()

    var userDisplayName: String
        get() = prefs.getString(KEY_USER_NAME, "").orEmpty()
        set(value) = prefs.edit().putString(KEY_USER_NAME, value).apply()

    val hasSession: Boolean get() = sessionToken.isNotBlank()

    fun saveSession(token: String, email: String, displayName: String) {
        prefs.edit()
            .putString(KEY_SESSION_TOKEN, token)
            .putString(KEY_USER_EMAIL, email)
            .putString(KEY_USER_NAME, displayName)
            .apply()
    }

    fun clear() {
        prefs.edit().clear().apply()
    }

    private companion object {
        const val KEY_SESSION_TOKEN = "session_token"
        const val KEY_USER_EMAIL = "user_email"
        const val KEY_USER_NAME = "user_display_name"
    }
}
