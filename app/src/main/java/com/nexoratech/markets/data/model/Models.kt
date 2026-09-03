package com.nexoratech.markets.data.model

enum class AssetClass { CRYPTO, FOREX }

enum class SignalType(val label: String) {
    STRONG_BUY("Strong Buy"),
    BUY("Buy"),
    NEUTRAL("Neutral"),
    SELL("Sell"),
    STRONG_SELL("Strong Sell");

    val isBullish: Boolean get() = this == STRONG_BUY || this == BUY
    val isBearish: Boolean get() = this == STRONG_SELL || this == SELL
}

data class Candle(
    val timeMs: Long,
    val open: Double,
    val high: Double,
    val low: Double,
    val close: Double,
    val volume: Double,
)

data class AssetSignal(
    val symbol: String,
    val name: String,
    val assetClass: AssetClass,
    val price: Double,
    val change1d: Double,
    val rsi: Double,
    val macdLine: Double,
    val macdSignal: Double,
    val macdHist: Double,
    val bbMid: Double,
    val bbUpper: Double,
    val bbLower: Double,
    val sma50: Double?,
    val sma200: Double?,
    val atr: Double?,
    val score: Double,
    val signal: SignalType,
    val reasons: List<String>,
    val lastDate: String,
) {
    val scorePercent: Int get() = (((score + 10.0) / 20.0 * 100).toInt()).coerceIn(0, 100)
}

data class AiAnalysis(
    val signal: String,
    val confidence: String?,
    val entry: String?,
    val stopLoss: String?,
    val takeProfits: List<String>,
    val keyLevels: List<String>,
    val reasoning: String,
    val raw: String?,
)

object AssetNames {
    private val NAMES = mapOf(
        "BTCUSDT" to "Bitcoin", "ETHUSDT" to "Ethereum", "SOLUSDT" to "Solana",
        "BNBUSDT" to "BNB", "XRPUSDT" to "XRP", "ADAUSDT" to "Cardano",
        "DOGEUSDT" to "Dogecoin", "LINKUSDT" to "Chainlink", "AVAXUSDT" to "Avalanche",
        "MATICUSDT" to "Polygon", "DOTUSDT" to "Polkadot", "LTCUSDT" to "Litecoin",
        "EURUSD" to "Euro / US Dollar", "GBPUSD" to "British Pound / US Dollar",
        "USDJPY" to "US Dollar / Japanese Yen", "USDNGN" to "US Dollar / Nigerian Naira",
        "AUDUSD" to "Australian Dollar / US Dollar", "USDCAD" to "US Dollar / Canadian Dollar",
        "USDCHF" to "US Dollar / Swiss Franc", "NZDUSD" to "New Zealand Dollar / US Dollar",
        "EURGBP" to "Euro / British Pound", "EURJPY" to "Euro / Japanese Yen",
        "GBPJPY" to "British Pound / Japanese Yen",
    )

    fun displayName(symbol: String): String =
        NAMES[symbol.uppercase()] ?: symbol.uppercase()
}
