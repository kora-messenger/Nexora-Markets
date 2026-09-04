package com.nexoratech.markets.ui.screens

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.nexoratech.markets.auth.AccountViewModel
import com.nexoratech.markets.ui.components.NexoraPrimaryButton
import com.nexoratech.markets.ui.theme.NexoraMono
import com.nexoratech.markets.ui.theme.Sell400
import com.nexoratech.markets.ui.theme.Teal400
import com.nexoratech.markets.ui.theme.TextSecondary
import java.util.Locale

private data class Step(val prompt: String, val hint: String?, val options: List<String>)

/**
 * Post-signup personalization — FxLens asks experience/goal/routine to
 * sweeten a paywall. Our set is engine-facing: sessions, hold duration,
 * frequency, risk %, market, and account size. Every answer is a number
 * or a schedule the signal engine can size plans against —
 * risk % x account = dollar risk on every trade plan.
 */
@Composable
fun ProfileOnboardingScreen(
    viewModel: AccountViewModel,
    onDone: () -> Unit,
) {
    val steps = listOf(
        Step(
            "Which sessions do you trade?",
            "Signals are timed to the markets you're actually awake for.",
            listOf("Asia", "London", "New York", "All sessions"),
        ),
        Step(
            "How long do you usually hold a trade?",
            "Sets the default analysis mode on every chart you submit.",
            listOf("Minutes", "Hours", "Days", "Weeks"),
        ),
        Step(
            "How many setups do you take a week?",
            "Keeps the feed at your pace — no flood, no drought.",
            listOf("1-3 a week", "4-10 a week", "10+ a week"),
        ),
        Step(
            "Max risk on a single trade?",
            "We frame every plan around this. Pros risk 1% or less.",
            listOf("0.5%", "1%", "2%", "3%+"),
        ),
        Step(
            "Where do you want to start?",
            "You can change your watchlist anytime.",
            listOf("Forex", "Crypto", "Both"),
        ),
    )

    var step by rememberSaveable { mutableIntStateOf(0) }
    var sessions by rememberSaveable { mutableStateOf<String?>(null) }
    var holdDuration by rememberSaveable { mutableStateOf<String?>(null) }
    var frequency by rememberSaveable { mutableStateOf<String?>(null) }
    var riskChoice by rememberSaveable { mutableStateOf<String?>(null) }
    var instruments by rememberSaveable { mutableStateOf<String?>(null) }
    var capital by rememberSaveable { mutableStateOf("") }
    var busy by rememberSaveable { mutableStateOf(false) }
    var error by rememberSaveable { mutableStateOf<String?>(null) }

    val selected = when (step) {
        0 -> sessions
        1 -> holdDuration
        2 -> frequency
        3 -> riskChoice
        4 -> instruments
        else -> null
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .systemBarsPadding()
            .imePadding(),
    ) {
        // Progress rail: mono step counter + segmented bar
        Column(Modifier.padding(horizontal = 24.dp)) {
            Spacer(Modifier.height(20.dp))
            Text(
                text = String.format(Locale.US, "SETUP  %d/6", step + 1),
                style = MaterialTheme.typography.labelSmall,
                fontFamily = NexoraMono,
                color = Teal400,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.height(10.dp))
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                repeat(6) { i ->
                    Box(
                        Modifier
                            .weight(1f)
                            .height(3.dp)
                            .background(
                                if (i <= step) Teal400
                                else MaterialTheme.colorScheme.surfaceVariant
                            )
                    )
                }
            }
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp),
        ) {
            Spacer(Modifier.height(32.dp))
            Text(
                text = steps[step].prompt,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
            )
            steps[step].hint?.let {
                Spacer(Modifier.height(8.dp))
                Text(it, style = MaterialTheme.typography.bodyMedium, color = TextSecondary)
            }
            Spacer(Modifier.height(24.dp))

            if (step == 5) {
                CapitalField(capital) { capital = it.filter { c -> c.isDigit() } }
                error?.let {
                    Spacer(Modifier.height(12.dp))
                    Text(it, color = Sell400, style = MaterialTheme.typography.bodySmall)
                }
            } else {
                steps[step].options.forEach { option ->
                    OptionRow(
                        label = option,
                        selected = selected == option,
                        onClick = {
                            when (step) {
                                0 -> sessions = option
                                1 -> holdDuration = option
                                2 -> frequency = option
                                3 -> riskChoice = option
                                4 -> instruments = option
                            }
                        },
                    )
                    Spacer(Modifier.height(10.dp))
                }
            }
            Spacer(Modifier.weight(1f))
            Spacer(Modifier.height(24.dp))
        }

        // Footer nav
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .animateContentSize(),
        ) {
            if (step > 0) {
                TextButton(onClick = { step-- }) { Text("Back", color = TextSecondary) }
            }
            Spacer(Modifier.weight(1f))
            if (busy) {
                CircularProgressIndicator(
                    modifier = Modifier
                        .height(28.dp)
                        .width(28.dp),
                    color = Teal400,
                    strokeWidth = 2.5.dp,
                )
            } else {
                NexoraPrimaryButton(
                    text = if (step == 5) "Build my feed" else "Continue",
                    onClick = {
                        val ready = if (step == 5) {
                            (capital.toDoubleOrNull() ?: 0.0) > 0
                        } else {
                            selected != null
                        }
                        if (!ready) {
                            error = if (step == 5) "Enter your account size to continue" else null
                        } else {
                            error = null
                            if (step < 5) {
                                step++
                            } else {
                                busy = true
                                // "3%+" is display-only; the engine stores 3.
                                val riskPercent = when (riskChoice) {
                                    "0.5%" -> 0.5
                                    "1%" -> 1.0
                                    "2%" -> 2.0
                                    else -> 3.0
                                }
                                viewModel.saveProfile(
                                    tradingSessions = sessions!!,
                                    tradeFrequency = frequency!!,
                                    holdDuration = holdDuration!!,
                                    riskPercent = riskPercent,
                                    instruments = instruments!!,
                                    capitalUsd = capital.toDouble(),
                                ) { message ->
                                    busy = false
                                    if (message == null) {
                                        viewModel.onProfileSaved()
                                        onDone()
                                    } else {
                                        error = message
                                    }
                                }
                            }
                        }
                    },
                    modifier = Modifier.width(180.dp),
                )
            }
        }
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun OptionRow(label: String, selected: Boolean, onClick: () -> Unit) {
    val borderColor = if (selected) Teal400 else MaterialTheme.colorScheme.surfaceVariant
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(12.dp),
        color = if (selected) Teal400.copy(alpha = 0.10f) else MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, borderColor),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier
                    .width(18.dp)
                    .height(18.dp)
                    .background(if (selected) Teal400 else androidx.compose.ui.graphics.Color.Transparent)
            )
            Spacer(Modifier.width(14.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                color = if (selected) MaterialTheme.colorScheme.onBackground else TextSecondary,
            )
        }
    }
}

@Composable
private fun CapitalField(value: String, onValueChange: (String) -> Unit) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        singleLine = true,
        prefix = {
            Text(
                "$",
                fontFamily = NexoraMono,
                fontWeight = FontWeight.SemiBold,
                color = Teal400,
            )
        },
        suffix = { Text("USD", style = MaterialTheme.typography.labelMedium, color = TextSecondary) },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        textStyle = MaterialTheme.typography.headlineSmall.copy(fontFamily = NexoraMono),
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = Teal400,
            unfocusedBorderColor = MaterialTheme.colorScheme.surfaceVariant,
            cursorColor = Teal400,
        ),
        supportingText = {
            Text(
                "Combined with your risk %, this sizes every trade plan.",
                style = MaterialTheme.typography.labelSmall,
            )
        },
        modifier = Modifier.fillMaxWidth(),
    )
}
