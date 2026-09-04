package com.nexoratech.markets.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.nexoratech.markets.auth.AccountViewModel
import com.nexoratech.markets.ui.theme.Ink700
import com.nexoratech.markets.ui.theme.Ink800
import com.nexoratech.markets.ui.theme.Teal400
import com.nexoratech.markets.ui.theme.TextPrimary
import com.nexoratech.markets.ui.theme.TextSecondary

/**
 * PaywallScreen — shown when the 7-day Pro trial has ended. Blocking by
 * design: analysis is the product, so it rides behind the subscription.
 * The CTA files a Pro request with the backend; until automated checkout
 * goes live, activation happens on the account server-side.
 */
@Composable
fun PaywallScreen(
    viewModel: AccountViewModel,
    onSignOut: () -> Unit,
) {
    val requested by viewModel.subscriptionRequested.collectAsState()
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .systemBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.height(48.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.width(14.dp).height(34.dp).background(Teal400))
            Spacer(Modifier.width(12.dp))
            Text(
                text = "TRIAL COMPLETE",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = TextPrimary,
            )
        }
        Spacer(Modifier.height(12.dp))
        Text(
            text = "Your 7-day Pro trial has ended. Your account, profile and history are untouched — Pro switches them all back on.",
            style = MaterialTheme.typography.bodyMedium,
            color = TextSecondary,
        )

        Spacer(Modifier.height(32.dp))

        Text(
            text = "WHAT PRO UNLOCKS",
            style = MaterialTheme.typography.labelLarge,
            color = Teal400,
        )
        Spacer(Modifier.height(12.dp))
        listOf(
            "Unlimited AI chart analysis — 4H + 15M confluence reads",
            "The full signal engine across every instrument on your watchlist",
            "Your risk profile driving every entry, stop and target",
        ).forEach { line ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    Modifier
                        .width(6.dp)
                        .height(6.dp)
                        .background(Teal400, RoundedCornerShape(3.dp))
                )
                Spacer(Modifier.width(12.dp))
                Text(
                    text = line,
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextPrimary,
                )
            }
        }

        Spacer(Modifier.height(36.dp))

        when {
            requested -> {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Ink700, RoundedCornerShape(10.dp))
                        .border(1.dp, Teal400.copy(alpha = 0.45f), RoundedCornerShape(10.dp))
                        .padding(16.dp),
                ) {
                    Column {
                        Text(
                            text = "PRO ACTIVATION REQUESTED",
                            style = MaterialTheme.typography.labelLarge,
                            color = Teal400,
                        )
                        Spacer(Modifier.height(6.dp))
                        Text(
                            text = "Your request is on file. Pro goes live on this account as soon as checkout opens — no re-setup needed.",
                            style = MaterialTheme.typography.bodySmall,
                            color = TextSecondary,
                        )
                    }
                }
            }
            else -> {
                if (error != null) {
                    Text(
                        text = error!!,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                    Spacer(Modifier.height(10.dp))
                }
                Button(
                    onClick = {
                        busy = true
                        error = null
                        viewModel.requestSubscription { msg ->
                            busy = false
                            if (msg != null) error = msg
                        }
                    },
                    enabled = !busy,
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Teal400),
                    shape = RoundedCornerShape(8.dp),
                ) {
                    if (busy) {
                        CircularProgressIndicator(
                            strokeWidth = 2.dp,
                            color = Ink800,
                            modifier = Modifier.height(22.dp).width(22.dp),
                        )
                    } else {
                        Text(
                            "Request Pro access",
                            color = Ink800,
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.titleMedium,
                        )
                    }
                }
            }
        }

        Spacer(Modifier.height(16.dp))
        TextButton(onClick = onSignOut) {
            Text("Sign out", color = TextSecondary)
        }
        Spacer(Modifier.height(32.dp))
    }
}
