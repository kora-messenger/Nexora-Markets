package com.nexoratech.markets.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.nexoratech.markets.auth.AccountViewModel
import com.nexoratech.markets.ui.theme.Amber400
import com.nexoratech.markets.ui.theme.Ink700
import com.nexoratech.markets.ui.theme.Ink800
import com.nexoratech.markets.ui.theme.Neutral400
import com.nexoratech.markets.ui.theme.Teal400
import com.nexoratech.markets.ui.theme.TextPrimary
import com.nexoratech.markets.ui.theme.TextSecondary
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.Locale

/**
 * PayoffScreen — the moment after the questionnaire where the trader sees
 * their own numbers computed live. No fantasy equity curves: only the
 * concrete risk arithmetic their profile implies, plus the trial clock.
 */
@Composable
fun PayoffScreen(
    viewModel: AccountViewModel,
    onEnterMarkets: () -> Unit,
) {
    val profile by viewModel.profile.collectAsState()
    val access by viewModel.access.collectAsState()

    val p = profile
    val dollarRisk = p?.dollarRiskPerTrade
    val capital = p?.capitalUsd
    val lossesToHalve = if (dollarRisk != null && dollarRisk > 0 && capital != null) {
        (capital * 0.5 / dollarRisk).toInt()
    } else null

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .systemBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp),
    ) {
        Spacer(Modifier.height(40.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.width(14.dp).height(34.dp).background(Teal400))
            Spacer(Modifier.width(12.dp))
            Text(
                text = "PROFILE LOCKED IN",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = TextPrimary,
            )
        }
        Spacer(Modifier.height(10.dp))
        Text(
            text = "Your numbers, computed from what you told us. Real risk math — not projections.",
            style = MaterialTheme.typography.bodyMedium,
            color = TextSecondary,
        )

        Spacer(Modifier.height(28.dp))

        if (dollarRisk != null) {
            PayoffStat(
                label = "AT RISK PER TRADE",
                value = "$" + if (dollarRisk >= 1000) {
                    String.format(Locale.US, "%,.0f", dollarRisk)
                } else {
                    String.format(Locale.US, "%,.2f", dollarRisk)
                },
                caption = "From your capital and risk percent — the number every signal plan is sized against.",
            )
            Spacer(Modifier.height(14.dp))
        }
        if (lossesToHalve != null) {
            PayoffStat(
                label = "FULL-RISK LOSSES TO HALVE YOUR ACCOUNT",
                value = lossesToHalve.toString(),
                caption = "How many consecutive maximum-risk losses it would take to cut your equity in half.",
            )
            Spacer(Modifier.height(14.dp))
        }

        // What their answers just configured
        val defaults = listOfNotNull(
            p?.tradingSessions?.let { "SESSIONS  $it" },
            p?.holdDuration?.let { "MODE  $it" },
            p?.instruments?.takeIf { it.isNotBlank() }?.let { "WATCHLIST  $it" },
        )
        if (defaults.isNotEmpty()) {
            Text(
                text = "FEED DEFAULTS SET",
                style = MaterialTheme.typography.labelLarge,
                color = Teal400,
            )
            Spacer(Modifier.height(8.dp))
            defaults.forEach { line ->
                Text(
                    text = line,
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextPrimary,
                    modifier = Modifier.padding(vertical = 3.dp),
                )
            }
        }

        Spacer(Modifier.height(28.dp))

        // Trial / subscription banner
        val banner = when {
            access == null -> null
            access!!.isActive -> {
                val days = access!!.endsAt?.let {
                    ChronoUnit.DAYS.between(Instant.now(), Instant.parse(it)).coerceAtLeast(0)
                }
                Triple("PRO ACTIVE", if (days != null && days > 0) "${days} days remaining" else "Active", Teal400)
            }
            access!!.isExpired -> null
            else -> Triple(
                "PRO TRIAL",
                "${access!!.daysLeft ?: 7} of 7 days left — full access, no limits",
                Amber400,
            )
        }
        if (banner != null) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Ink700, RoundedCornerShape(10.dp))
                    .border(1.dp, banner.third.copy(alpha = 0.45f), RoundedCornerShape(10.dp))
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(banner.first, style = MaterialTheme.typography.labelLarge, color = banner.third)
                Text(banner.second, style = MaterialTheme.typography.bodySmall, color = TextSecondary)
            }
            Spacer(Modifier.height(28.dp))
        }

        Button(
            onClick = onEnterMarkets,
            modifier = Modifier.fillMaxWidth().height(52.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Teal400),
            shape = RoundedCornerShape(8.dp),
        ) {
            Text(
                "Enter the markets",
                color = Ink800,
                fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.titleMedium,
            )
        }
        Spacer(Modifier.height(32.dp))
    }
}

@Composable
private fun PayoffStat(label: String, value: String, caption: String) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Ink800, RoundedCornerShape(12.dp))
            .border(1.dp, Neutral400.copy(alpha = 0.18f), RoundedCornerShape(12.dp))
            .padding(18.dp),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = TextSecondary,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = value,
            style = MaterialTheme.typography.displayMedium,
            fontWeight = FontWeight.Bold,
            color = Teal400,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = caption,
            style = MaterialTheme.typography.bodySmall,
            color = TextSecondary,
        )
    }
}
