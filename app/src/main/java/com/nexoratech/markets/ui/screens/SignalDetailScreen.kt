package com.nexoratech.markets.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.nexoratech.markets.data.model.AssetSignal
import com.nexoratech.markets.ui.components.ScoreBar
import com.nexoratech.markets.ui.components.SignalChip
import com.nexoratech.markets.ui.components.formatPrice
import com.nexoratech.markets.ui.components.signalColor

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SignalDetailScreen(signal: AssetSignal, onBack: () -> Unit) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(signal.symbol.uppercase(), fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                ),
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(Modifier.padding(20.dp)) {
                    Text(signal.name, style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(4.dp))
                    Text(formatPrice(signal.price), style = MaterialTheme.typography.headlineLarge)
                    Spacer(Modifier.height(8.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        SignalChip(signal.signal)
                        Spacer(Modifier.padding(4.dp))
                        Text(
                            "data as of ${signal.lastDate}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Spacer(Modifier.height(16.dp))
                    ScoreBar(signal)
                }
            }

            MetricsGrid(signal)

            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Why this signal", style = MaterialTheme.typography.titleMedium)
                    signal.reasons.forEach { reason ->
                        Row {
                            Text("•  ", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                            Text(reason, style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
            }

            Text(
                "Composite score: trend (SMA50/200), momentum (MACD, RSI), volatility (Bollinger). " +
                    "Educational analysis — not financial advice.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun MetricsGrid(signal: AssetSignal) {
    val metrics: List<Pair<String, String>> = listOfNotNull(
        "RSI (14)" to String.format("%.1f", signal.rsi),
        "MACD hist" to String.format("%+.4f", signal.macdHist),
        "SMA 50" to (signal.sma50?.let { formatPrice(it) } ?: "n/a"),
        "SMA 200" to (signal.sma200?.let { formatPrice(it) } ?: "n/a"),
        "Boll upper" to formatPrice(signal.bbUpper),
        "Boll lower" to formatPrice(signal.bbLower),
        "ATR (14)" to (signal.atr?.let { formatPrice(it) } ?: "n/a"),
        "1D change" to String.format("%+.2f%%", signal.change1d),
    )
    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(20.dp)) {
            Text("Indicators", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(12.dp))
            metrics.chunked(2).forEach { row ->
                Row(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                    row.forEach { item ->
                        val label = item.first
                        val value = item.second
                        Column(Modifier.weight(1f)) {
                            Text(label, style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(value, style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.SemiBold)
                        }
                    }
                    if (row.size == 1) Spacer(Modifier.weight(1f))
                }
            }
        }
    }
}
