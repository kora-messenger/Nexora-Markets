package com.nexoratech.markets.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.outlined.Insights
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.nexoratech.markets.data.model.AssetClass
import com.nexoratech.markets.data.model.AssetSignal
import com.nexoratech.markets.ui.MarketsViewModel
import com.nexoratech.markets.ui.components.ScoreBar
import com.nexoratech.markets.ui.components.SignalChip
import com.nexoratech.markets.ui.components.SymbolBadge
import com.nexoratech.markets.ui.components.formatChange
import com.nexoratech.markets.ui.components.formatPrice
import com.nexoratech.markets.ui.theme.NexoraNumeric
import com.nexoratech.markets.ui.theme.Buy400
import com.nexoratech.markets.ui.theme.Sell400

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MarketsScreen(
    viewModel: MarketsViewModel,
    onOpenSignal: (AssetSignal) -> Unit,
    onOpenAnalyze: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    var tab by remember { mutableIntStateOf(0) }
    val all = viewModel.signals

    Column(modifier = Modifier.fillMaxSize()) {
        Header(
            loading = viewModel.loading,
            onRefresh = viewModel::refresh,
            onAnalyze = onOpenAnalyze,
            onSettings = onOpenSettings,
        )

        viewModel.error?.let { err ->
            Surface(
                color = MaterialTheme.colorScheme.errorContainer,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
            ) {
                Text(
                    err,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onErrorContainer,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                )
            }
        }

        TabRow(selectedTabIndex = tab) {
            Tab(selected = tab == 0, onClick = { tab = 0 },
                text = { Text("Crypto", fontWeight = FontWeight.SemiBold) })
            Tab(selected = tab == 1, onClick = { tab = 1 },
                text = { Text("Forex", fontWeight = FontWeight.SemiBold) })
        }

        if (all.isEmpty() && viewModel.loading) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else {
            val shown = all.filter {
                if (tab == 0) it.assetClass == AssetClass.CRYPTO else it.assetClass == AssetClass.FOREX
            }
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                items(shown, key = { it.symbol + it.assetClass }) { signal ->
                    MarketRow(signal = signal, onClick = { onOpenSignal(signal) })
                }
                item {
                    Text(
                        stringResourceAttribution(),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun Header(
    loading: Boolean,
    onRefresh: () -> Unit,
    onAnalyze: () -> Unit,
    onSettings: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 6.dp, top = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text("Nexora Markets", style = MaterialTheme.typography.headlineMedium)
            Text(
                "AI signals · live market data",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        IconButton(onClick = onAnalyze) {
            Icon(Icons.Outlined.Insights, contentDescription = "Analyze chart",
                tint = MaterialTheme.colorScheme.primary)
        }
        IconButton(onClick = onRefresh, enabled = !loading) {
            if (loading) {
                CircularProgressIndicator(modifier = Modifier.height(22.dp), strokeWidth = 2.dp)
            } else {
                Icon(Icons.Filled.Refresh, contentDescription = "Refresh")
            }
        }
        IconButton(onClick = onSettings) {
            Icon(Icons.Outlined.Settings, contentDescription = "Settings")
        }
    }
}

@Composable
private fun MarketRow(signal: AssetSignal, onClick: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                SymbolBadge(signal.symbol)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(signal.name, style = MaterialTheme.typography.titleMedium)
                    Text(
                        signal.symbol.uppercase(),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        formatPrice(signal.price),
                        style = NexoraNumeric.priceMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        formatChange(signal.change1d),
                        style = NexoraNumeric.change,
                        color = if (signal.change1d >= 0) Buy400 else Sell400,
                    )
                }
                Spacer(Modifier.width(10.dp))
                SignalChip(signal.signal, compact = true)
            }
            Spacer(Modifier.height(10.dp))
            ScoreBar(signal)
        }
    }
}

private fun stringResourceAttribution(): String =
    "Signals computed on-device from live daily data. Binance · Kraken · ECB · Yahoo Finance. Not financial advice."
