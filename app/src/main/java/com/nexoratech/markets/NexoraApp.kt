package com.nexoratech.markets

import android.app.Application
import com.nexoratech.markets.ai.ChartAiClient
import com.nexoratech.markets.data.MarketRepository
import com.nexoratech.markets.data.SettingsStore

class NexoraApp : Application() {
    lateinit var settings: SettingsStore
        private set
    lateinit var repository: MarketRepository
        private set
    lateinit var aiClient: ChartAiClient
        private set

    override fun onCreate() {
        super.onCreate()
        settings = SettingsStore(this)
        repository = MarketRepository()
        aiClient = ChartAiClient(settings)
    }
}
