package com.nexoratech.markets.ai

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Base64
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParser
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
 * Vision AI chart analysis. Works with any OpenAI-compatible chat API
 * (OpenAI, Groq, OpenRouter, ...) — the user's own key, stored encrypted.
 * Sends up to two chart screenshots (e.g. 4H trend + 15M entry) and gets
 * back a structured trade plan.
 */
class ChartAiClient(private val settings: SettingsStore) {

    suspend fun analyze(request: ChartAnalysisRequest, readBitmap: suspend (Uri) -> Bitmap?): AiAnalysisResult {
        if (!settings.hasAiKey()) {
            return AiAnalysisResult.Error("No AI API key set. Add one in Settings (OpenAI, Groq or any OpenAI-compatible provider).")
        }
        val images = request.imageUris.mapNotNull { readBitmap(it)?.let { b -> encodeForTransport(b) } }
        if (images.isEmpty()) return AiAnalysisResult.Error("Could not read the selected chart image(s).")

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
