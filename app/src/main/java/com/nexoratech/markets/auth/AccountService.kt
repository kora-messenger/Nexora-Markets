package com.nexoratech.markets.auth

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.nexoratech.markets.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException

/**
 * Nexora account service. Talks to the same Base44-hosted backend as the
 * chart analysis endpoint — swap `nexoraApiUrl` in gradle.properties and
 * every auth call moves with it. No auth logic lives in the app: passwords
 * are hashed and sessions issued server-side.
 */
class AccountService {

    private val http: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    sealed class AccountResult {
        /** [token] is the freshly issued session (null when merely validating). */
        data class Success(val user: AccountUser, val token: String? = null) : AccountResult()
        data class Failure(val message: String) : AccountResult()
    }

    suspend fun register(displayName: String, email: String, password: String): AccountResult =
        callAuth(
            "registerUser",
            buildBody {
                addProperty("displayName", displayName)
                addProperty("email", email)
                addProperty("password", password)
            }
        )

    suspend fun login(email: String, password: String): AccountResult =
        callAuth(
            "loginUser",
            buildBody {
                addProperty("email", email)
                addProperty("password", password)
            }
        )

    suspend fun validateSession(sessionToken: String): AccountResult =
        callAuth(
            "validateSession",
            buildBody { addProperty("sessionToken", sessionToken) }
        )

    suspend fun logout(sessionToken: String) {
        withContext(Dispatchers.IO) {
            runCatching {
                val body = buildBody { addProperty("sessionToken", sessionToken) }
                    .toString()
                    .toRequestBody("application/json".toMediaType())
                val request = Request.Builder()
                    .url(endpoint("logoutUser"))
                    .post(body)
                    .build()
                http.newCall(request).execute().use { it.close() }
            }
        }
    }

    private suspend fun callAuth(path: String, payload: JsonObject): AccountResult =
        withContext(Dispatchers.IO) {
            try {
                val request = Request.Builder()
                    .url(endpoint(path))
                    .post(payload.toString().toRequestBody("application/json".toMediaType()))
                    .build()
                http.newCall(request).execute().use { response ->
                    val text = response.body?.string().orEmpty()
                    val json = runCatching { JsonParser.parseString(text).asJsonObject }
                        .getOrElse {
                            return@withContext AccountResult.Failure("Unexpected response from server")
                        }
                    if (json.get("status")?.asString == "ok") {
                        val user = json.getAsJsonObject("user")
                        val session = json.getAsJsonObject("session")
                        AccountResult.Success(
                            user = AccountUser(
                                email = user.get("email")?.asString.orEmpty(),
                                displayName = user.get("displayName")?.asString.orEmpty(),
                            ),
                            token = session?.get("token")?.takeIf { !it.isJsonNull }?.asString,
                        )
                    } else {
                        AccountResult.Failure(
                            json.get("message")?.asString ?: "Request failed (${response.code})"
                        )
                    }
                }
            } catch (e: TimeoutException) {
                AccountResult.Failure("Connection timed out. Check your network and try again.")
            } catch (e: IOException) {
                AccountResult.Failure("No connection to Nexora Cloud. Try again in a moment.")
            }
        }


data class TradingProfileData(
    val tradingSessions: String? = null,
    val tradeFrequency: String? = null,
    val holdDuration: String? = null,
    val riskPercent: Double? = null,
    val instruments: String? = null,
    val capitalUsd: Double? = null,
) {
    val isComplete: Boolean
        get() = tradingSessions != null && tradeFrequency != null && holdDuration != null &&
            riskPercent != null && instruments != null && capitalUsd != null

    /** Dollar risk per trade — the number the signal engine sizes plans with. */
    val dollarRiskPerTrade: Double?
        get() = capitalUsd?.let { cap -> riskPercent?.let { pct -> cap * pct / 100.0 } }
}

sealed class ProfileResult {
    data class Loaded(val profile: TradingProfileData?) : ProfileResult()
    data class Failure(val message: String) : ProfileResult()
}

/**
 * Personalization profile — powers the post-signup questionnaire.
 * Same session-credential contract as auth; identity always from the
 * session token, never from client-supplied ids.
 */
suspend fun getProfile(sessionToken: String): ProfileResult {
    val payload = buildBody { addProperty("sessionToken", sessionToken) }
    val response = postJson("getTradingProfile", payload) ?: return ProfileResult.Failure("No connection to Nexora Cloud")
    val profile = response.get("profile")?.takeIf { it.isJsonObject }?.asJsonObject
    return ProfileResult.Loaded(profile?.let { p ->
        TradingProfileData(
            tradingSessions = p.stringOrNull("tradingSessions"),
            tradeFrequency = p.stringOrNull("tradeFrequency"),
            holdDuration = p.stringOrNull("holdDuration"),
            riskPercent = p.get("riskPercent")?.takeIf { !it.isJsonNull }?.asDouble,
            instruments = p.stringOrNull("instruments"),
            capitalUsd = p.get("capitalUsd")?.takeIf { !it.isJsonNull }?.asDouble,
        )
    })
}

suspend fun saveProfile(
    sessionToken: String,
    tradingSessions: String,
    tradeFrequency: String,
    holdDuration: String,
    riskPercent: Double,
    instruments: String,
    capitalUsd: Double,
): ProfileResult {
    val payload = buildBody {
        addProperty("sessionToken", sessionToken)
        addProperty("tradingSessions", tradingSessions)
        addProperty("tradeFrequency", tradeFrequency)
        addProperty("holdDuration", holdDuration)
        addProperty("riskPercent", riskPercent)
        addProperty("instruments", instruments)
        addProperty("capitalUsd", capitalUsd)
    }
    val response = postJson("saveTradingProfile", payload)
        ?: return ProfileResult.Failure("No connection to Nexora Cloud. Try again in a moment.")
    return if (response.get("status")?.asString == "ok") {
        ProfileResult.Loaded(null)
    } else {
        ProfileResult.Failure(response.get("message")?.asString ?: "Could not save your profile")
    }
}

private fun JsonObject.stringOrNull(key: String): String? =
    get(key)?.takeIf { !it.isJsonNull }?.asString

private suspend fun postJson(path: String, payload: JsonObject): JsonObject? =
    withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder()
                .url(endpoint(path))
                .post(payload.toString().toRequestBody("application/json".toMediaType()))
                .build()
            http.newCall(request).execute().use { response ->
                val text = response.body?.string().orEmpty()
                runCatching { JsonParser.parseString(text).asJsonObject }.getOrNull()
            }
        } catch (e: IOException) {
            null
        }
    }

    private fun endpoint(path: String): String =
        BuildConfig.NEXORA_API_URL.trimEnd('/') + "/functions/" + path

    private fun buildBody(block: JsonObject.() -> Unit): JsonObject =
        JsonObject().apply {
            addProperty("token", BuildConfig.NEXORA_API_TOKEN)
            block()
        }
}

data class AccountUser(val email: String, val displayName: String)
