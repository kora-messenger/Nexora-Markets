package com.nexoratech.markets.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.nexoratech.markets.ui.components.NexoraPrimaryButton
import com.nexoratech.markets.ui.theme.Teal400
import com.nexoratech.markets.ui.theme.TextSecondary

/**
 * Welcome — the first screen anyone sees. Deliberately nothing like the
 * competitor's soft-gradient welcome: a terminal boot sequence, brand
 * wordmark in mono, and exactly two clear paths in.
 */
@Composable
fun WelcomeScreen(
    onCreateAccount: () -> Unit,
    onSignIn: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .systemBarsPadding()
            .imePadding()
            .padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.weight(1f))

        // Brand mark
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .width(14.dp)
                    .height(34.dp)
                    .background(Teal400)
            )
            Spacer(Modifier.width(12.dp))
            Text(
                text = "NEXORA",
                style = MaterialTheme.typography.displaySmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground,
            )
        }
        Spacer(Modifier.height(10.dp))
        Text(
            text = "MARKETS",
            style = MaterialTheme.typography.labelLarge,
            color = Teal400,
        )

        Spacer(Modifier.height(28.dp))
        Text(
            text = "Institutional-grade signal intelligence.\nIn your pocket.",
            style = MaterialTheme.typography.bodyLarge,
            color = TextSecondary,
            textAlign = TextAlign.Center,
        )

        Spacer(Modifier.weight(1f))

        // Boot-sequence feature lines — the terminal signature
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 24.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            BootLine("multi-timeframe confluence engine")
            BootLine("AI chart analysis with trade plans")
            BootLine("risk / reward mapped to real levels")
        }

        NexoraPrimaryButton(
            text = "Create account",
            onClick = onCreateAccount,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(12.dp))
        TextButton(onClick = onSignIn, modifier = Modifier.fillMaxWidth()) {
            Text(
                "I already have an account",
                style = MaterialTheme.typography.bodyMedium,
                color = Teal400,
            )
        }
        Spacer(Modifier.height(16.dp))
    }
}

@Composable
private fun BootLine(text: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            "> ",
            style = MaterialTheme.typography.bodyMedium,
            color = Teal400,
            fontFamily = com.nexoratech.markets.ui.theme.NexoraMono,
        )
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            color = TextSecondary,
        )
    }
}
