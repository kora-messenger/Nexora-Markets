package com.nexoratech.markets.data

import com.google.gson.JsonObject
import com.nexoratech.markets.data.model.AssetClass
import com.nexoratech.markets.data.model.AssetNames
import com.nexoratech.markets.data.model.Candle
import com.nexoratech.markets.domain.SignalEngine
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope

/**
 * Live market data with the same self-healing fallback chain as the
 * Nexora pipeline:
 *   crypto: Binance -> Kraken
 *   forex : ECB (Frankfurter) -> Yahoo Finance
 */
class MarketRepository {

    suspend fun fetchCandles(symbol: String, assetClass: AssetClass): List<Candle> =
        when (assetClass) {
            AssetClass.CRYPTO -> fetchCrypto(symbol)
            AssetClass.FOREX -> fetchForex(symbol)
        }

    private suspend fun fetchCrypto(symbol: String): List<Candle> {
        val binanceError: Throwable
        try {
            return fetchBinance(symbol)
        } catch (t: Throwable) {
            binanceError = t
        }
        try {
            return fetchKraken(symbol)
        } catch (t: Throwable) {
            throw IllegalStateException("All data sources failed for $symbol", binanceError)
        }
    }

    private suspend fun fetchBinance(symbol: String): List<Candle> {
        val arr = Network.getArray("https://api.binance.com/api/v3/klines?symbol=$symbol&interval=1d&limit=365")
        return arr.map { k ->
            val c = k.asJsonArray
            Candle(
                timeMs = c[0].asLong,
                open = c[1].asString.toDouble(),
                high = c[2].asString.toDouble(),
                low = c[3].asString.toDouble(),
                close = c[4].asString.toDouble(),
                volume = c[5].asString.toDouble(),
            )
        }
    }

    private suspend fun fetchKraken(symbol: String): List<Candle> {
        val pair = symbol.uppercase().replace("USDT", "USD").replace("BTC", "XBT")
        val root = Network.getJson("https://api.kraken.com/0/public/OHLC?pair=$pair&interval=1440")
        if (root.getAsJsonArray("error").size() > 0) {
            throw IllegalStateException("Kraken rejected $pair")
        }
        val result = root.getAsJsonObject("result")
        val key = result.keySet().firstOrNull { it != "last" }
            ?: throw IllegalStateException("Kraken returned no series for $pair")
        return result.getAsJsonArray(key).map { e ->
            val c = e.asJsonArray
            Candle(
                timeMs = c[0].asLong * 1000L,
                open = c[1].asString.toDouble(),
                high = c[2].asString.toDouble(),
                low = c[3].asString.toDouble(),
                close = c[4].asString.toDouble(),
                volume = c[6].asString.toDouble(),
            )
        }
    }

    private suspend fun fetchForex(pair: String): List<Candle> {
        try {
            return fetchFrankfurter(pair)
        } catch (_: Throwable) { /* fall through to Yahoo */ }
        return fetchYahoo(pair)
    }

    private suspend fun fetchFrankfurter(pair: String): List<Candle> {
        val base = pair.substring(0, 3).uppercase()
        val quote = pair.substring(3).uppercase()
        val start = java.time.LocalDate.now(java.time.ZoneOffset.UTC).minusDays(365).toString()
        val root = Network.getJson("https://api.frankfurter.dev/v1/$start..?from=$base&to=$quote")
        val rates = root.getAsJsonObject("rates") ?: throw IllegalStateException("No rates object")
        val candles = mutableListOf<Candle>()
        for ((day, quoteRates) in rates.entrySet().sortedBy { it.key }) {
            val value = quoteRates.asJsonObject.get(quote)?.asDouble ?: continue
            val ms = java.time.LocalDate.parse(day)
                .atStartOfDay(java.time.ZoneOffset.UTC).toInstant().toEpochMilli()
            candles.add(Candle(ms, value, value, value, value, 0.0))
        }
        if (candles.size < 25) throw IllegalStateException("ECB series too short for $pair")
        return candles
    }

    private suspend fun fetchYahoo(pair: String): List<Candle> {
        val ticker = "${pair.uppercase()}=X"
        val url = "https://query1.finance.yahoo.com/v8/finance/chart/$ticker?interval=1d&range=1y"
        val root = Network.getJson(url, userAgent = "Mozilla/5.0 (Android) NexoraMarkets/1.0")
        val result = root.getAsJsonObject("chart")?.getAsJsonArray("result")?.get(0)?.asJsonObject
            ?: throw IllegalStateException("Yahoo returned no data for $ticker")
        val timestamps = result.getAsJsonArray("timestamp")
        val quote = result.getAsJsonObject("indicators").getAsJsonArray("quote").get(0).asJsonObject
        val closes = quote.getAsJsonArray("close")
        val candles = mutableListOf<Candle>()
        for (i in 0 until timestamps.size()) {
            val close = closes.get(i)?.takeIf { !it.isJsonNull }?.asDouble ?: continue
            val open = quote.getAsJsonArray("open").get(i)?.takeIf { !it.isJsonNull }?.asDouble ?: close
            val high = quote.getAsJsonArray("high").get(i)?.takeIf { !it.isJsonNull }?.asDouble ?: close
            val low = quote.getAsJsonArray("low").get(i)?.takeIf { !it.isJsonNull }?.asDouble ?: close
            candles.add(Candle(timestamps.get(i).asLong * 1000L, open, high, low, close, 0.0))
        }
        if (candles.size < 25) throw IllegalStateException("Yahoo series too short for $ticker")
        return candles
    }

    /** Fetch + score the full watchlist in parallel. */
    suspend fun analyzeWatchlist(crypto: List<String>, forex: List<String>): List<Result<com.nexoratech.markets.data.model.AssetSignal>> =
        coroutineScope {
            val jobs = crypto.map { sym ->
                async { runCatching { scoreAsset(sym, AssetClass.CRYPTO) } }
            } + forex.map { sym ->
                async { runCatching { scoreAsset(sym, AssetClass.FOREX) } }
            }
            jobs.awaitAll()
        }

    private suspend fun scoreAsset(symbol: String, assetClass: AssetClass): com.nexoratech.markets.data.model.AssetSignal {
        val candles = fetchCandles(symbol, assetClass)
        return SignalEngine.analyze(symbol, AssetNames.displayName(symbol), assetClass, candles)
    }
}
