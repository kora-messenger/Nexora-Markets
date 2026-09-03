package com.nexoratech.markets.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.nexoratech.markets.NexoraApp
import com.nexoratech.markets.data.model.AssetClass
import com.nexoratech.markets.data.model.AssetSignal
import kotlinx.coroutines.launch

class MarketsViewModel(private val app: NexoraApp) : ViewModel() {

    var signals by mutableStateOf<List<AssetSignal>>(emptyList())
        private set
    var loading by mutableStateOf(false)
        private set
    var error by mutableStateOf<String?>(null)
        private set
    var lastRefreshMs by mutableStateOf(0L)
        private set

    init { refresh() }

    fun refresh() {
        if (loading) return
        loading = true
        error = null
        viewModelScope.launch {
            try {
                val results = app.repository.analyzeWatchlist(
                    app.settings.cryptoWatchlist,
                    app.settings.forexWatchlist,
                )
                val ok = results.mapNotNull { it.getOrNull() }
                    .sortedByDescending { it.score }
                val failed = results.count { it.isFailure }
                signals = ok
                error = if (failed > 0) "$failed asset(s) could not be refreshed" else null
            } catch (t: Throwable) {
                error = t.message ?: "Failed to load market data"
            } finally {
                loading = false
                lastRefreshMs = System.currentTimeMillis()
            }
        }
    }

    fun signalFor(symbol: String, assetClass: AssetClass): AssetSignal? =
        signals.firstOrNull { it.symbol == symbol && it.assetClass == assetClass }

    class Factory(private val app: NexoraApp) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T = MarketsViewModel(app) as T
    }
}
