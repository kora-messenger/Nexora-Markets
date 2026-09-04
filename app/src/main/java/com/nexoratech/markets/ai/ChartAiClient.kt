package com.nexoratech.markets.ai

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Base64
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.nexoratech.markets.BuildConfig
import com.nexoratech.markets.data.Network
import com.nexoratech.markets.data.SettingsStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Request
import java.io.ByteArrayOutputStream

data class ChartAnalysisRequest(
    val imageUris: List<Uri>,
    val timeframe: String,
    val tradingStyle: String,
    val instrumentHint: String,
)

/**
 * Vision AI chart analysis — two modes, same output contract:
 *
 *  - Nexora Cloud (default): the request goes to the Nexora backend on
 *    Base44, which holds the AI provider key server-side. Nothing
 *    sensitive ships inside the APK.
 *  - Own key: calls any OpenAI-compatible chat API (OpenAI, Groq,
 *    OpenRouter, ...) directly with the user's key, stored encrypted.
 *
 * Sends up to two chart screenshots (e.g. 4H trend + 15M entry) and gets
 * back a structured trade plan.
 */
class ChartAiClient(private val settings: SettingsStore) {

    suspend fun analyze(
        request: ChartAnalysisRequest,
        sessionToken: String = "",
        readBitmap: suspend (Uri) -> Bitmap?,
    ): AiAnalysisResult {
        val cloud = settings.useCloud && BuildConfig.NEXORA_API_TOKEN.isNotBlank()
        if (!cloud && !settings.hasAiKey()) {
            return AiAnalysisResult.Error("No AI provider configured. Enable Nexora Cloud in Settings, or add your own API key (OpenAI, Groq or any OpenAI-compatible provider).")
        }
        val images = request.imageUris.mapNotNull { readBitmap(it)?.let { b -> encodeForTransport(b) } }
        if (images.isEmpty()) return AiAnalysisResult.Error("Could not read the selected chart image(s).")

        if (cloud) return analyzeViaCloud(request, sessionToken, images)

        val prompt = buildPrompt(request)

        val content = JsonArray()
        content.add(JsonObject().apply {
            addProperty("type", "text")
            addProperty("text", prompt)
        })
        images.forEach { content.add(JsonObject().apply {
            addProperty("type", "image_url")
            add("image_url", JsonObject().apply {
                addProperty("url", "data:image/jpeg;base64,$it")
            })
        }) }

        val body = JsonObject().apply {
            addProperty("model", settings.aiModel)
            addProperty("temperature", 0.3)
            add("messages", JsonArray().apply {
                add(JsonObject().apply {
                    addProperty("role", "user")
                    add("content", content)
                })
            })
        }

        return try {
            val response = postChat(body)
            val text = response
                .getAsJsonArray("choices").get(0).asJsonObject
                .getAsJsonObject("message").get("content").asString
            parseAnalysis(text)
        } catch (t: Throwable) {
            AiAnalysisResult.Error("AI request failed: ${t.message ?: "unknown error"}")
        }
    }

    /**
     * Nexora Cloud mode: POST the screenshots to the Base44 backend, which
     * runs the identical prompt server-side and returns the same JSON
     * contract ({status, analysis{signal, confidence, entry, ...}}).
     */
    private suspend fun analyzeViaCloud(
        request: ChartAnalysisRequest,
        sessionToken: String,
        images: List<String>,
    ): AiAnalysisResult =
        withContext(Dispatchers.IO) {
            try {
                val body = JsonObject().apply {
                    addProperty("token", BuildConfig.NEXORA_API_TOKEN)
                    if (sessionToken.isNotBlank()) addProperty("sessionToken", sessionToken)
                    add("images", JsonArray().apply { images.forEach { add(com.google.gson.JsonPrimitive(it)) } })
                    addProperty("timeframe", request.timeframe)
                    addProperty("trading_style", request.tradingStyle)
                    addProperty("instrument", request.instrumentHint)
                }
                val httpRequest = Request.Builder()
                    .url(BuildConfig.NEXORA_API_URL.trimEnd('/') + "/functions/analyzeChart")
                    .header("Content-Type", "application/json")
                    .post(body.toString().toRequestBody("application/json".toMediaType()))
                    .build()
                Network.http.newCall(httpRequest).execute().use { response ->
                    val text = response.body?.string().orEmpty()
                    if (!response.isSuccessful) {
                        val message = runCatching {
                            JsonParser.parseString(text).asJsonObject.get("message")?.asString
                        }.getOrNull() ?: "HTTP ${response.code}"
                        return@withContext AiAnalysisResult.Error(message)
                    }
                    val json = JsonParser.parseString(text).asJsonObject
                    val analysis = json.getAsJsonObject("analysis")
                    AiAnalysisResult.Success(
                        com.nexoratech.markets.data.model.AiAnalysis(
                            signal = analysis.get("signal")?.asString ?: "NEUTRAL",
                            confidence = analysis.get("confidence")?.takeIf { it !is com.google.gson.JsonNull }?.asString,
                            entry = analysis.get("entry")?.takeIf { it !is com.google.gson.JsonNull }?.asString,
                            stopLoss = analysis.get("stop_loss")?.takeIf { it !is com.google.gson.JsonNull }?.asString,
                            takeProfits = analysis.get("take_profits")?.asJsonArray?.map { it.asString }.orEmpty(),
                            keyLevels = analysis.get("key_levels")?.asJsonArray?.map { it.asString }.orEmpty(),
                            reasoning = analysis.get("reasoning")?.asString.orEmpty(),
                            raw = null,
                        )
                    )
                }
            } catch (t: Throwable) {
                AiAnalysisResult.Error("Cloud request failed: ${t.message ?: "unknown error"}")
            }
        }

    private suspend fun postChat(body: JsonObject): JsonObject = withContext(Dispatchers.IO) {
        val base = settings.aiBaseUrl.trimEnd('/')
        val request = Request.Builder()
            .url("$base/chat/completions")
            .header("Authorization", "Bearer ${settings.aiApiKey}")
            .header("Content-Type", "application/json")
            .post(body.toString().toRequestBody("application/json".toMediaType()))
            .build()
        Network.http.newCall(request).execute().use { response ->
            val text = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                throw IllegalStateException("HTTP ${response.code}: ${text.take(200)}")
            }
            JsonParser.parseString(text).asJsonObject
        }
    }

    private fun buildPrompt(request: ChartAnalysisRequest): String {
        val style = if (request.tradingStyle.equals("swing", ignoreCase = true))
            "a swing trader (positions held days to weeks)"
        else
            "a scalper (positions held minutes to hours)"
        return """
            You are a senior technical analyst briefing $style on a chart.
            ${if (request.instrumentHint.isNotBlank()) "The trader says the instrument is: ${request.instrumentHint}. " else ""}
            Stated timeframe(s): ${request.timeframe}. ${if (request.imageUris.size > 1) "Two screenshots are attached: use the higher timeframe for trend bias and the lower timeframe for entry timing (multi-timeframe confluence). " else ""}
            Read the chart like a professional: instrument, market structure (trend, support/resistance zones), notable candlestick patterns, and any visible indicators (RSI, MACD, moving averages).
            Reply with ONLY a JSON object, no markdown fences, with exactly these keys:
            {
              "signal": "BUY" | "SELL" | "NEUTRAL",
              "confidence": "Low" | "Medium" | "High",
              "entry": "entry zone as a price range string",
              "stop_loss": "stop loss price string",
              "take_profits": ["TP1 price", "TP2 price"],
              "key_levels": ["level 1 description", "level 2 description"],
              "reasoning": "2-4 sentences of concrete, chart-grounded reasoning"
            }
            Use real price levels visible on the chart. Be decisive but honest about weak setups (use NEUTRAL).
        """.trimIndent()
    }

    private fun parseAnalysis(text: String): AiAnalysisResult {
        val cleaned = text.trim()
            .removePrefix("```json").removePrefix("```").removeSuffix("```").trim()
        return try {
            val obj = JsonParser.parseString(cleaned).asJsonObject
            AiAnalysisResult.Success(
                com.nexoratech.markets.data.model.AiAnalysis(
                    signal = obj.get("signal")?.asString ?: "NEUTRAL",
                    confidence = obj.get("confidence")?.asString,
                    entry = obj.get("entry")?.asString,
                    stopLoss = obj.get("stop_loss")?.asString,
                    takeProfits = obj.get("take_profits")?.asJsonArray?.map { it.asString }.orEmpty(),
                    keyLevels = obj.get("key_levels")?.asJsonArray?.map { it.asString }.orEmpty(),
                    reasoning = obj.get("reasoning")?.asString.orEmpty(),
                    raw = null,
                )
            )
        } catch (_: Exception) {
            // Model ignored the JSON instruction — degrade gracefully to raw text.
            AiAnalysisResult.Success(
                com.nexoratech.markets.data.model.AiAnalysis(
                    signal = "NEUTRAL", confidence = null, entry = null, stopLoss = null,
                    takeProfits = emptyList(), keyLevels = emptyList(),
                    reasoning = cleaned, raw = cleaned,
                )
            )
        }
    }

    private fun encodeForTransport(bitmap: Bitmap): String {
        val scaled = scaleDown(bitmap, maxDim = 1600)
        val out = ByteArrayOutputStream()
        scaled.compress(Bitmap.CompressFormat.JPEG, 85, out)
        return Base64.encodeToString(out.toByteArray(), Base64.NO_WRAP)
    }

    private fun scaleDown(bitmap: Bitmap, maxDim: Int): Bitmap {
        val largest = maxOf(bitmap.width, bitmap.height)
        if (largest <= maxDim) return bitmap
        val scale = maxDim.toFloat() / largest
        return Bitmap.createScaledBitmap(bitmap, (bitmap.width * scale).toInt(), (bitmap.height * scale).toInt(), true)
    }

    companion object {
        fun decodeUri(context: android.content.Context, uri: Uri): Bitmap? =
            context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it) }
    }
}

sealed class AiAnalysisResult {
    data class Success(val analysis: com.nexoratech.markets.data.model.AiAnalysis) : AiAnalysisResult()
    data class Error(val message: String) : AiAnalysisResult()
}
