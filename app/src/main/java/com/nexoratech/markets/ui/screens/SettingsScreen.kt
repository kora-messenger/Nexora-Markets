package com.nexoratech.markets.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.Switch
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.nexoratech.markets.NexoraApp
import com.nexoratech.markets.ui.MarketsViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    app: NexoraApp,
    viewModel: MarketsViewModel,
    account: com.nexoratech.markets.auth.AccountViewModel,
    user: com.nexoratech.markets.auth.AccountUser,
    onEditProfile: () -> Unit,
    onBack: () -> Unit,
) {
    var apiKey by remember { mutableStateOf(app.settings.aiApiKey) }
    var baseUrl by remember { mutableStateOf(app.settings.aiBaseUrl) }
    var model by remember { mutableStateOf(app.settings.aiModel) }
    var newCrypto by remember { mutableStateOf("") }
    var newForex by remember { mutableStateOf("") }
    var crypto by remember { mutableStateOf(app.settings.cryptoWatchlist) }
    var forex by remember { mutableStateOf(app.settings.forexWatchlist) }
    var savedTick by remember { mutableStateOf(false) }
    var useCloud by remember { mutableStateOf(app.settings.useCloud) }

    var confirmSignOut by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings", fontWeight = FontWeight.Bold) },
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
            // ── Account ──────────────────────────────────────────────
            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("Account", style = MaterialTheme.typography.titleMedium)
                    Column {
                        Text(user.displayName, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
                        Text(user.email, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    TextButton(onClick = onEditProfile) {
                        Text("Edit trading profile", color = com.nexoratech.markets.ui.theme.Teal400)
                    }
                    if (confirmSignOut) {
                        Text("Sign out on this device?", style = MaterialTheme.typography.bodyMedium)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            TextButton(onClick = { confirmSignOut = false }) { Text("Cancel") }
                            androidx.compose.material3.Button(
                                onClick = { account.signOut { confirmSignOut = false } },
                                colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                                    containerColor = com.nexoratech.markets.ui.theme.Sell400,
                                    contentColor = androidx.compose.ui.graphics.Color(0xFF2A0D0D),
                                ),
                            ) { Text("Sign out") }
                        }
                    } else {
                        TextButton(onClick = { confirmSignOut = true }) {
                            Text("Sign out", color = com.nexoratech.markets.ui.theme.Sell400)
                        }
                    }
                }
            }

            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("AI analysis engine", style = MaterialTheme.typography.titleMedium)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text("Nexora Cloud (recommended)", style = MaterialTheme.typography.bodyLarge)
                            Text(
                                "Runs on the Nexora backend — no key needed on this device",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Switch(
                            checked = useCloud,
                            onCheckedChange = { value ->
                                useCloud = value
                                app.settings.useCloud = value
                            },
                        )
                    }
                    Text("AI provider", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "Bring your own key — OpenAI, Groq, OpenRouter or any OpenAI-compatible API. " +
                            "The key is stored encrypted on this device only.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    OutlinedTextField(
                        value = apiKey,
                        onValueChange = { apiKey = it; savedTick = false },
                        label = { Text("API key") },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    OutlinedTextField(
                        value = baseUrl,
                        onValueChange = { baseUrl = it; savedTick = false },
                        label = { Text("Base URL") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    OutlinedTextField(
                        value = model,
                        onValueChange = { model = it; savedTick = false },
                        label = { Text("Model (must support images, e.g. gpt-4o-mini)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Row {
                        Button(onClick = {
                            app.settings.aiApiKey = apiKey.trim()
                            app.settings.aiBaseUrl = baseUrl.trim()
                            app.settings.aiModel = model.trim()
                            savedTick = true
                        }) { Text("Save AI settings") }
                        Spacer(Modifier.padding(6.dp))
                        if (savedTick) {
                            Text(
                                "Saved ✓",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(top = 10.dp),
                            )
                        }
                    }
                }
            }

            WatchlistCard(
                title = "Crypto watchlist",
                hint = "Any Binance pair, e.g. BTCUSDT",
                items = crypto,
                newValue = newCrypto,
                onNewValueChange = { newCrypto = it },
                onAdd = {
                    val sym = newCrypto.trim().uppercase()
                    if (sym.isNotEmpty() && crypto.none { it.equals(sym, ignoreCase = true) }) {
                        crypto = crypto + sym
                        app.settings.cryptoWatchlist = crypto
                        newCrypto = ""
                    }
                },
                onRemove = { sym ->
                    crypto = crypto.filterNot { it.equals(sym, ignoreCase = true) }
                    app.settings.cryptoWatchlist = crypto
                },
            )

            WatchlistCard(
                title = "Forex watchlist",
                hint = "6-letter pair, e.g. EURUSD or USDNGN",
                items = forex,
                newValue = newForex,
                onNewValueChange = { newForex = it },
                onAdd = {
                    val sym = newForex.trim().uppercase()
                    if (sym.length == 6 && forex.none { it.equals(sym, ignoreCase = true) }) {
                        forex = forex + sym
                        app.settings.forexWatchlist = forex
                        newForex = ""
                    }
                },
                onRemove = { sym ->
                    forex = forex.filterNot { it.equals(sym, ignoreCase = true) }
                    app.settings.forexWatchlist = forex
                },
            )

            Button(
                onClick = { viewModel.refresh() },
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Apply watchlist & refresh markets") }

            Text(
                "Nexora Markets 1.0 · Nexora Technologies",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun WatchlistCard(
    title: String,
    hint: String,
    items: List<String>,
    newValue: String,
    onNewValueChange: (String) -> Unit,
    onAdd: () -> Unit,
    onRemove: (String) -> Unit,
) {
    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            items.forEach { sym ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(sym, style = MaterialTheme.typography.bodyLarge)
                    IconButton(onClick = { onRemove(sym) }) {
                        Icon(
                            Icons.Outlined.Delete,
                            contentDescription = "Remove $sym",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
            OutlinedTextField(
                value = newValue,
                onValueChange = onNewValueChange,
                label = { Text(hint) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            TextButton(onClick = onAdd) {
                Icon(Icons.Filled.Add, contentDescription = null)
                Spacer(Modifier.padding(4.dp))
                Text("Add to watchlist")
            }
        }
    }
}
