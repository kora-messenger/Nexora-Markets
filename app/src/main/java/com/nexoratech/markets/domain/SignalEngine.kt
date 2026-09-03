package com.nexoratech.markets.domain

import com.nexoratech.markets.data.model.AssetSignal
import com.nexoratech.markets.data.model.Candle
import com.nexoratech.markets.data.model.SignalType
import java.time.Instant
import java.time.format.DateTimeFormatter
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/**
 * Quantitative signal engine — the same composite scoring model as the
 * Nexora GitHub Actions pipeline: trend (SMA50/200), momentum (MACD, RSI)
 * and volatility extremes (Bollinger) blended into a -10..+10 score.
 */
object SignalEngine {

    fun sma(values: DoubleArray, period: Int): DoubleArray {
        val out = DoubleArray(values.size) { Double.NaN }
        var sum = 0.0
        for (i in values.indices) {
            sum += values[i]
            if (i >= period) sum -= values[i - period]
            if (i >= period - 1) out[i] = sum / period
        }
        return out
    }

    fun ema(values: DoubleArray, period: Int): DoubleArray =
        emaAlpha(values, 2.0 / (period + 1))

    /** Wilder-style exponential smoothing with an explicit alpha. */
    fun emaAlpha(values: DoubleArray, alpha: Double): DoubleArray {
        val out = DoubleArray(values.size)
        if (values.isEmpty()) return out
        var prev = values[0]
        out[0] = prev
        for (i in 1 until values.size) {
            prev = alpha * values[i] + (1 - alpha) * prev
            out[i] = prev
        }
        return out
    }

    fun rsi(closes: DoubleArray, period: Int = 14): DoubleArray {
        val out = DoubleArray(closes.size) { 50.0 }
        if (closes.size < 2) return out
        val gains = DoubleArray(closes.size)
        val losses = DoubleArray(closes.size)
        for (i in 1 until closes.size) {
            val delta = closes[i] - closes[i - 1]
            gains[i] = max(0.0, delta)
            losses[i] = max(0.0, -delta)
        }
        val avgGain = emaAlpha(gains, 1.0 / period)
        val avgLoss = emaAlpha(losses, 1.0 / period)
        for (i in closes.indices) {
            out[i] = if (avgLoss[i] == 0.0) 100.0 else 100 - 100 / (1 + avgGain[i] / avgLoss[i])
        }
        return out
    }

    /** Rolling sample standard deviation (matches pandas rolling().std()). */
    private fun rollingStd(values: DoubleArray, period: Int): DoubleArray {
        val out = DoubleArray(values.size) { Double.NaN }
        for (i in period - 1 until values.size) {
            val window = values.copyOfRange(i - period + 1, i + 1)
            val mean = window.average()
            val variance = window.sumOf { (it - mean) * (it - mean) } / (period - 1)
            out[i] = sqrt(variance)
        }
        return out
    }

    private fun lastOrNaN(a: DoubleArray): Double =
        if (a.isEmpty()) Double.NaN else a[a.size - 1]

    fun analyze(symbol: String, name: String, assetClass: com.nexoratech.markets.data.model.AssetClass, candles: List<Candle>): AssetSignal {
        require(candles.size >= 25) { "Not enough candles for $symbol (${candles.size})" }
        val closes = DoubleArray(candles.size) { candles[it].close }
        val price = closes[closes.size - 1]

        val rsiSeries = rsi(closes)
        val r = rsiSeries[rsiSeries.size - 1]

        val macdLine = DoubleArray(closes.size)
        val emaFast = ema(closes, 12)
        val emaSlow = ema(closes, 26)
        for (i in closes.indices) macdLine[i] = emaFast[i] - emaSlow[i]
        val macdSig = ema(macdLine, 9)
        val macdHist = DoubleArray(closes.size) { macdLine[it] - macdSig[it] }

        val bbMidSeries = sma(closes, 20)
        val bbStdSeries = rollingStd(closes, 20)
        val bbMid = lastOrNaN(bbMidSeries)
        val bbUpper = bbMid + 2 * bbStdSeries[bbStdSeries.size - 1]
        val bbLower = bbMid - 2 * bbStdSeries[bbStdSeries.size - 1]

        val sma50Series = sma(closes, 50)
        val sma200Series = sma(closes, 200)
        val sma50 = lastOrNaN(sma50Series)
        val sma200 = lastOrNaN(sma200Series)

        val hasOhlc = candles.all { it.high > 0 && it.low > 0 }
        val atr = if (hasOhlc) lastOrNaN(atrSeries(candles)) else Double.NaN

        var score = 0.0
        val reasons = mutableListOf<String>()

        // --- Trend ---
        if (price > sma50) { score += 2; reasons.add("Price above SMA50 (short-term uptrend)") }
        else { score -= 2; reasons.add("Price below SMA50 (short-term downtrend)") }

        if (!sma200.isNaN()) {
            if (sma50 > sma200) { score += 2; reasons.add("SMA50 above SMA200 (bullish structure)") }
            else { score -= 2; reasons.add("SMA50 below SMA200 (bearish structure)") }
        }

        // --- Momentum ---
        val n = closes.size - 1
        if (macdLine[n] > macdSig[n]) { score += 1; reasons.add("MACD above signal line (bullish momentum)") }
        else { score -= 1; reasons.add("MACD below signal line (bearish momentum)") }

        if (macdHist[n] > macdHist[n - 1]) { score += 1; reasons.add("MACD histogram rising (momentum building)") }
        else { score -= 1; reasons.add("MACD histogram falling (momentum fading)") }

        // --- RSI ---
        when {
            r < 30 -> { score += 1.5; reasons.add(String.format("RSI %.1f — oversold, potential bounce", r)) }
            r > 70 -> { score -= 1.5; reasons.add(String.format("RSI %.1f — overbought, potential pullback", r)) }
            r >= 55 -> score += 0.5
            r <= 45 -> score -= 0.5
        }

        // --- Bollinger extremes ---
        if (price < bbLower) { score += 1; reasons.add("Price below lower Bollinger band (stretched down)") }
        else if (price > bbUpper) { score -= 1; reasons.add("Price above upper Bollinger band (stretched up)") }

        score = max(-10.0, min(10.0, score))

        val change1d = if (closes.size > 1) (price / closes[n - 1] - 1) * 100 else 0.0
        val lastDate = Instant.ofEpochMilli(candles[candles.size - 1].timeMs)
            .atZone(java.time.ZoneOffset.UTC).toLocalDate().toString()

        return AssetSignal(
            symbol = symbol,
            name = name,
            assetClass = assetClass,
            price = price,
            change1d = change1d,
            rsi = r,
            macdLine = macdLine[n],
            macdSignal = macdSig[n],
            macdHist = macdHist[n],
            bbMid = bbMid,
            bbUpper = bbUpper,
            bbLower = bbLower,
            sma50 = if (sma50.isNaN()) null else sma50,
            sma200 = if (sma200.isNaN()) null else sma200,
            atr = if (atr.isNaN()) null else atr,
            score = score,
            signal = signalFor(score),
            reasons = reasons,
            lastDate = lastDate,
        )
    }

    fun signalFor(score: Double): SignalType = when {
        score >= 5 -> SignalType.STRONG_BUY
        score >= 2 -> SignalType.BUY
        score <= -5 -> SignalType.STRONG_SELL
        score <= -2 -> SignalType.SELL
        else -> SignalType.NEUTRAL
    }

    /** Average True Range (Wilder), needs OHLC candles. */
    private fun atrSeries(candles: List<Candle>): DoubleArray {
        val tr = DoubleArray(candles.size)
        for (i in candles.indices) {
            if (i == 0) { tr[i] = candles[i].high - candles[i].low; continue }
            val hc = abs(candles[i].high - candles[i - 1].close)
            val lc = abs(candles[i].low - candles[i - 1].close)
            tr[i] = max(max(candles[i].high - candles[i].low, hc), lc)
        }
        return emaAlpha(tr, 1.0 / 14)
    }
}
