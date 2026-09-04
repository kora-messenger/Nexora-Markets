@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package com.nexoratech.markets.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
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

/**
 * Post-signup personalization — rebuilt as 3 progressive pages (matching
 * the cadence traders already expect from this category), but every
 * question is engine-facing. No psychology harvesting, no "quit your job"
 * goal picker, no illustrative gains graph — just the numbers and
 * schedule the signal engine sizes plans and times signals against.
 *
 * The one field this category doesn't have anywhere: confluence pairing
 * (bias timeframe + trigger timeframe) — it directly seeds the
 * multi-timeframe confluence view, not just a display preference.
 */
@Composable
fun ProfileOnboardingScreen(
    viewModel: AccountViewModel,
    onDone: () -> Unit,
) {
    val name = viewModel.displayName.split(" ").firstOrNull()?.ifBlank { "trader" } ?: "trader"

    var page by rememberSaveable { mutableIntStateOf(0) }

    // Page 1 — basics
    var experience by rememberSaveable { mutableStateOf<String?>(null) }
    var instruments by rememberSaveable { mutableStateOf<String?>(null) }
    var capital by rememberSaveable { mutableStateOf("") }

    // Page 2 — approach
    var style by rememberSaveable { mutableStateOf<String?>(null) } // display label -> holdDuration
    var frequency by rememberSaveable { mutableStateOf<String?>(null) }
    var biasTf by rememberSaveable { mutableStateOf<String?>(null) }
    var triggerTf by rememberSaveable { mutableStateOf<String?>(null) }
    var entryNotes by rememberSaveable { mutableStateOf("") }

    // Page 3 — risk & sessions
    var sessions by rememberSaveable { mutableStateOf<String?>(null) }
    var riskChoice by rememberSaveable { mutableStateOf<String?>(null) }

    var busy by rememberSaveable { mutableStateOf(false) }
    var error by rememberSaveable { mutableStateOf<String?>(null) }

    val page1Ready = experience != null && instruments != null && (capital.toDoubleOrNull() ?: 0.0) > 0
    val page2Ready = style != null && frequency != null && biasTf != null && triggerTf != null
    val page3Ready = sessions != null && riskChoice != null

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
                text = String.format(Locale.US, "SETUP  %d/3", page + 1),
                style = MaterialTheme.typography.labelSmall,
                fontFamily = NexoraMono,
                color = Teal400,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.height(10.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                repeat(3) { i ->
                    Box(
                        Modifier
                            .weight(1f)
                            .height(3.dp)
                            .background(if (i <= page) Teal400 else MaterialTheme.colorScheme.surfaceVariant)
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
            Spacer(Modifier.height(28.dp))
            when (page) {
                0 -> {
                    Text(
                        "Welcome, $name",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "Three quick steps to build your feed and run your first analysis.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextSecondary,
                    )
                    Spacer(Modifier.height(28.dp))

                    QuestionLabel("Experience level")
                    ChipGroup(
                        options = listOf("Beginner", "Intermediate", "Advanced"),
                        selected = experience,
                        onSelect = { experience = it },
                    )
                    Spacer(Modifier.height(24.dp))

                    QuestionLabel("Markets you trade")
                    ChipGroup(
                        options = listOf("Forex", "Crypto", "Both"),
                        selected = instruments,
                        onSelect = { instruments = it },
                    )
                    Spacer(Modifier.height(24.dp))

                    QuestionLabel("Account size")
                    CapitalField(capital) { capital = it.filter { c -> c.isDigit() } }
                }

                1 -> {
                    Text(
                        "Nice, $name",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "Now the way you trade — this drives the signal engine directly.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextSecondary,
                    )
                    Spacer(Modifier.height(28.dp))

                    QuestionLabel("Trading style")
                    ChipGroup(
                        options = listOf("Scalping", "Day Trading", "Swing Trading", "Position Trading"),
                        selected = style,
                        onSelect = { style = it },
                    )
                    Spacer(Modifier.height(24.dp))

                    QuestionLabel("Setups per week")
                    ChipGroup(
                        options = listOf("1-3 a week", "4-10 a week", "10+ a week"),
                        selected = frequency,
                        onSelect = { frequency = it },
                    )
                    Spacer(Modifier.height(24.dp))

                    QuestionLabel("Confluence pairing")
                    Text(
                        "Bias timeframe reads the trend. Trigger timeframe times the entry. This is what powers multi-timeframe confluence — not just a display preference.",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary,
                    )
                    Spacer(Modifier.height(12.dp))
                    Text(
                        "Bias (trend)",
                        style = MaterialTheme.typography.labelMedium,
                        fontFamily = NexoraMono,
                        color = Teal400,
                    )
                    Spacer(Modifier.height(8.dp))
                    ChipGroup(
                        options = listOf("1D", "4H", "1H"),
                        selected = biasTf,
                        onSelect = { biasTf = it },
                    )
                    Spacer(Modifier.height(16.dp))
                    Text(
                        "Trigger (entry)",
                        style = MaterialTheme.typography.labelMedium,
                        fontFamily = NexoraMono,
                        color = Teal400,
                    )
                    Spacer(Modifier.height(8.dp))
                    ChipGroup(
                        options = listOf("1H", "15M", "5M", "1M"),
                        selected = triggerTf,
                        onSelect = { triggerTf = it },
                    )
                    Spacer(Modifier.height(24.dp))

                    QuestionLabel("Entry criteria (optional)")
                    OutlinedTextField(
                        value = entryNotes,
                        onValueChange = { if (it.length <= 500) entryNotes = it },
                        placeholder = { Text("Describe how you enter trades", color = TextSecondary) },
                        minLines = 3,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Teal400,
                            unfocusedBorderColor = MaterialTheme.colorScheme.surfaceVariant,
                            cursorColor = Teal400,
                        ),
                        supportingText = {
                            Text(
                                "Feeds your AI chart analysis prompt as context. ${entryNotes.length}/500",
                                style = MaterialTheme.typography.labelSmall,
                            )
                        },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }

                else -> {
                    Text(
                        "Last one, $name",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "Lock in your risk — every trade plan is sized around this.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextSecondary,
                    )
                    Spacer(Modifier.height(28.dp))

                    QuestionLabel("Sessions you trade")
                    ChipGroup(
                        options = listOf("Asia", "London", "New York", "All sessions"),
                        selected = sessions,
                        onSelect = { sessions = it },
                    )
                    Spacer(Modifier.height(24.dp))

                    QuestionLabel("Max risk per trade")
                    ChipGroup(
                        options = listOf("0.5%", "1%", "2%", "3%+"),
                        selected = riskChoice,
                        onSelect = { riskChoice = it },
                    )
                    error?.let {
                        Spacer(Modifier.height(16.dp))
                        Text(it, color = Sell400, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
            Spacer(Modifier.height(24.dp))
        }

        // Footer nav
        Row(Modifier.fillMaxWidth().padding(horizontal = 24.dp)) {
            if (page > 0) {
                TextButton(onClick = { page-- }) { Text("Back", color = TextSecondary) }
            }
            Spacer(Modifier.weight(1f))
            if (busy) {
                CircularProgressIndicator(
                    modifier = Modifier.height(28.dp).width(28.dp),
                    color = Teal400,
                    strokeWidth = 2.5.dp,
                )
            } else {
                val ready = when (page) {
                    0 -> page1Ready
                    1 -> page2Ready
                    else -> page3Ready
                }
                NexoraPrimaryButton(
                    text = if (page == 2) "Build my feed" else "Next",
                    onClick = {
                        if (page < 2) {
                            page++
                        } else {
                            busy = true
                            error = null
                            // "3%+" is display-only; the engine stores 3.
                            val riskPercent = when (riskChoice) {
                                "0.5%" -> 0.5
                                "1%" -> 1.0
                                "2%" -> 2.0
                                else -> 3.0
                            }
                            // Trading style label maps to the engine's hold-duration enum.
                            val holdDuration = when (style) {
                                "Scalping" -> "Minutes"
                                "Day Trading" -> "Hours"
                                "Swing Trading" -> "Days"
                                else -> "Weeks"
                            }
                            viewModel.saveProfile(
                                experienceLevel = experience!!,
                                tradingSessions = sessions!!,
                                tradeFrequency = frequency!!,
                                holdDuration = holdDuration,
                                riskPercent = riskPercent,
                                instruments = instruments!!,
                                capitalUsd = capital.toDouble(),
                                confluenceBiasTf = biasTf!!,
                                confluenceTriggerTf = triggerTf!!,
                                entryNotes = entryNotes.trim(),
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
                    },
                    enabled = ready,
                    modifier = Modifier.width(180.dp),
                )
            }
        }
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun QuestionLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.SemiBold,
    )
    Spacer(Modifier.height(12.dp))
}

@Composable
private fun ChipGroup(options: List<String>, selected: String?, onSelect: (String) -> Unit) {
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        options.forEach { option ->
            Chip(label = option, selected = selected == option, onClick = { onSelect(option) })
        }
    }
}

@Composable
private fun Chip(label: String, selected: Boolean, onClick: () -> Unit) {
    val borderColor = if (selected) Teal400 else MaterialTheme.colorScheme.surfaceVariant
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(20.dp),
        color = if (selected) Teal400.copy(alpha = 0.14f) else MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, borderColor),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            color = if (selected) Teal400 else TextSecondary,
            modifier = Modifier.padding(horizontal = 18.dp, vertical = 10.dp),
        )
    }
}

@Composable
private fun CapitalField(value: String, onValueChange: (String) -> Unit) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        singleLine = true,
        placeholder = { Text("e.g. 500", color = TextSecondary) },
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
