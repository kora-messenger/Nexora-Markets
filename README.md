# Nexora Markets

**Nexora Markets** by **Nexora Technologies** — AI-powered forex & crypto trading analysis for Android.

## Features

- **Live watchlist** — crypto (Binance, Kraken fallback) and forex (ECB, Yahoo fallback) with real prices, 1-day change and composite signals
- **On-device signal engine** — RSI(14), MACD(12/26/9), Bollinger(20,2), SMA50/200, ATR(14) blended into a −10..+10 score → Strong Buy / Buy / Neutral / Sell / Strong Sell, with a transparent "why this signal" breakdown
- **AI chart analysis** — attach 1–2 chart screenshots (e.g. 4H trend + 15M entry), pick timeframe and style (scalp/swing), and a vision AI returns a structured trade plan: signal, confidence, entry zone, stop-loss, take-profits, key levels and reasoning
- **Bring your own AI key** — works with OpenAI, Groq, OpenRouter or any OpenAI-compatible API; stored with Android encrypted preferences, never leaves the device except to your chosen provider
- **Editable watchlists** — add any Binance pair or 6-letter FX pair (incl. USDNGN)
- **Dark, trading-desk design** — built with Jetpack Compose, Material 3

## Build

Requirements: JDK 17, Android SDK 35.

```bash
./gradlew assembleDebug
# APK: app/build/outputs/apk/debug/app-debug.apk
```

Or push to GitHub — the included workflow builds the APK and uploads it as an artifact (Actions → Android Build → artifacts).

For a release build, create a keystore and add `keystore.properties`:

```properties
storeFile=/path/to/your.keystore
storePassword=***
keyAlias=nexora
keyPassword=***
```

## Architecture

```
app/src/main/java/com/nexoratech/markets/
├── ai/ChartAiClient.kt        # vision AI — structured trade plans from screenshots
├── data/
│   ├── MarketRepository.kt     # self-healing data (Binance→Kraken, ECB→Yahoo)
│   ├── Network.kt             # shared OkHttp+Gson JSON layer
│   ├── SettingsStore.kt       # encrypted settings + watchlists
│   └── model/Models.kt
├── domain/SignalEngine.kt     # quant signal engine (RSI/MACD/BB/SMA/ATR scoring)
└── ui/
    ├── screens/               # Markets · SignalDetail · Analyze · Settings
    ├── components/            # shared design-system pieces
    └── theme/                 # Nexora dark-first design language
```

## Data sources

| Asset | Primary | Fallback |
|---|---|---|
| Crypto | Binance public API | Kraken public API |
| Forex | ECB (Frankfurter) | Yahoo Finance |

No API keys required for market data. The AI layer requires your own key.

---

⚠️ **Disclaimer:** educational analysis only. Not financial advice — markets carry risk.
