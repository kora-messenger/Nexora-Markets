package com.nexoratech.markets.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.nexoratech.markets.data.model.AssetSignal
import com.nexoratech.markets.data.model.SignalType
import com.nexoratech.markets.ui.theme.Buy400
import com.nexoratech.markets.ui.theme.Neutral400
import com.nexoratech.markets.ui.theme.NexoraNumeric
import com.nexoratech.markets.ui.theme.Sell400
import java.util.Locale

fun formatPrice(price: Double): String = when {
    price >= 1000 -> String.format(Locale.US, "%,.2f", price)
    price >= 1 -> String.format(Locale.US, "%.4f", price)
    else -> String.format(Locale.US, "%.6f", price)
}

fun formatChange(change: Double): String =
    String.format(Locale.US, "%+.2f%%", change)

fun signalColor(signal: SignalType): Color = when {
    signal.isBullish -> Buy400
    signal.isBearish -> Sell400
    else -> Neutral400
}

@Composable
fun SignalChip(signal: SignalType, compact: Boolean = false) {
    val color = signalColor(signal)
    Surface(
        color = color.copy(alpha = 0.16f),
        contentColor = color,
        shape = RoundedCornerShape(10.dp),
    ) {
        Text(
            text = if (compact) compactLabel(signal) else signal.label,
            style = if (compact) MaterialTheme.typography.labelSmall
            else MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
        )
    }
}

private fun compactLabel(signal: SignalType): String = when (signal) {
    SignalType.STRONG_BUY -> "S-BUY"
    SignalType.BUY -> "BUY"
    SignalType.NEUTRAL -> "FLAT"
    SignalType.SELL -> "SELL"
    SignalType.STRONG_SELL -> "S-SELL"
}

@Composable
fun SymbolBadge(symbol: String, modifier: Modifier = Modifier) {
    val initials = when {
        symbol.length == 6 -> symbol.take(3)
        symbol.endsWith("USDT") -> symbol.dropLast(4).take(3)
        else -> symbol.take(3)
    }
    Box(
        modifier = modifier
            .size(42.dp)
            .background(MaterialTheme.colorScheme.surfaceVariant, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = initials,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
        )
    }
}

@Composable
fun ScoreBar(signal: AssetSignal, modifier: Modifier = Modifier) {
    Column(modifier = modifier) {
        val color = signalColor(signal.signal)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(6.dp)
                .background(color.copy(alpha = 0.15f), RoundedCornerShape(3.dp)),
        ) {
            val fraction = ((signal.score + 10.0) / 20.0).toFloat().coerceIn(0f, 1f)
            Box(
                modifier = Modifier
                    .fillMaxWidth(fraction)
                    .height(6.dp)
                    .background(color, RoundedCornerShape(3.dp)),
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 2.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text("-10", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(
                "score %+.1f".format(signal.score),
                style = NexoraNumeric.score,
                color = color,
            )
            Text("+10", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
