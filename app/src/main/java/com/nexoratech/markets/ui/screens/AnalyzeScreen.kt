package com.nexoratech.markets.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.material.icons.filled.AddAPhoto
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.nexoratech.markets.NexoraApp
import com.nexoratech.markets.ai.AiAnalysisResult
import com.nexoratech.markets.ai.ChartAiClient
import com.nexoratech.markets.ai.ChartAnalysisRequest
import com.nexoratech.markets.ui.components.SignalChip
import com.nexoratech.markets.ui.components.signalColor
import com.nexoratech.markets.data.model.SignalType
import kotlinx.coroutines.launch

private val TIMEFRAMES = listOf("15M", "1H", "4H", "1D")
private val STYLES = listOf("Scalp", "Swing")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AnalyzeScreen(app: NexoraApp, onBack: () -> Unit) {
    val scope = rememberCoroutineScope()

    var images by remember { mutableStateOf<List<Uri>>(emptyList()) }
    var timeframe by remember { mutableStateOf("4H") }
    var style by remember { mutableStateOf("Scalp") }
    var instrument by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var result by remember { mutableStateOf<AiAnalysisResult?>(null) }

    val picker = rememberLauncherForActivityResult(
        ActivityResultContracts.PickMultipleVisualMedia(maxItems = 2)
    ) { uris -> if (uris.isNotEmpty()) images = uris.take(2) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Analyze Chart", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    androidx.compose.material3.IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                        )
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
            Text(
                "Attach up to two screenshots (e.g. 4H for trend + 15M for entry). " +
                    "The AI reads them like an analyst: structure, patterns, indicators — then returns a trade plan.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                repeat(2) { index ->
                    val uri = images.getOrNull(index)
                    ChartSlot(
                        uri = uri,
                        label = if (index == 0) "Chart 1 (higher TF)" else "Chart 2 (entry TF)",
                        onClick = {
                            picker.launch(
                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                            )
                        },
                        modifier = Modifier.weight(1f),
                    )
                }
            }
            if (images.isNotEmpty()) {
                OutlinedButton(onClick = { images = emptyList(); result = null }) {
                    Text("Clear images")
                }
            }

            Text("Timeframe", style = MaterialTheme.typography.titleSmall)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TIMEFRAMES.forEach { tf ->
                    FilterChip(
                        selected = timeframe == tf,
                        onClick = { timeframe = tf },
                        label = { Text(tf) },
                    )
                }
            }

            Text("Trading style", style = MaterialTheme.typography.titleSmall)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                STYLES.forEach { s ->
                    FilterChip(
                        selected = style == s,
                        onClick = { style = s },
                        label = { Text(s) },
                    )
                }
            }

            OutlinedTextField(
                value = instrument,
                onValueChange = { instrument = it },
                label = { Text("Instrument (optional, e.g. XAUUSD)") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )

            Button(
                onClick = {
                    scope.launch {
                        busy = true
                        result = app.aiClient.analyze(
                            ChartAnalysisRequest(images, timeframe, style, instrument)
                        ) { uri -> ChartAiClient.decodeUri(app, uri) }
                        busy = false
                    }
                },
                enabled = images.isNotEmpty() && !busy,
                modifier = Modifier.fillMaxWidth().height(52.dp),
            ) {
                if (busy) {
                    CircularProgressIndicator(
                        modifier = Modifier.height(22.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary,
                    )
                    Spacer(Modifier.padding(6.dp))
                    Text("Analyzing…")
                } else {
                    Text("Analyze with AI", fontWeight = FontWeight.SemiBold)
                }
            }

            result?.let { res ->
                when (res) {
                    is AiAnalysisResult.Error -> ErrorCard(res.message)
                    is AiAnalysisResult.Success -> AnalysisCard(res)
                }
            }
        }
    }
}

@Composable
private fun ChartSlot(uri: Uri?, label: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Column(modifier = modifier) {
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 6.dp),
        )
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(150.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .border(
                    1.dp,
                    MaterialTheme.colorScheme.outline,
                    RoundedCornerShape(16.dp),
                )
                .clickable(onClick = onClick),
            contentAlignment = Alignment.Center,
        ) {
            if (uri == null) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        Icons.Filled.AddAPhoto,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "Add screenshot",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else {
                AsyncImage(
                    model = uri,
                    contentDescription = label,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
    }
}

@Composable
private fun ErrorCard(message: String) {
    androidx.compose.material3.Card(
        shape = RoundedCornerShape(16.dp),
        colors = androidx.compose.material3.CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer,
        ),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(
                "Analysis failed",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onErrorContainer,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                message,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onErrorContainer,
            )
        }
    }
}

@Composable
private fun AnalysisCard(res: AiAnalysisResult.Success) {
    val a = res.analysis
    val signalType = when (a.signal.uppercase()) {
        "BUY" -> SignalType.BUY
        "SELL" -> SignalType.SELL
        else -> SignalType.NEUTRAL
    }
    androidx.compose.material3.Card(
        shape = RoundedCornerShape(20.dp),
        colors = androidx.compose.material3.CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface,
        ),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    a.signal.uppercase(),
                    style = MaterialTheme.typography.headlineMedium,
                    color = signalColor(signalType),
                    fontWeight = FontWeight.Bold,
                )
                Spacer(Modifier.padding(4.dp))
                SignalChip(signalType)
                a.confidence?.let {
                    Text(
                        "  ·  $it confidence",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            a.entry?.let { PlanRow("Entry zone", it) }
            a.stopLoss?.let { PlanRow("Stop loss", it) }
            a.takeProfits.forEachIndexed { i, tp -> PlanRow("TP${i + 1}", tp) }
            if (a.keyLevels.isNotEmpty()) {
                PlanRow("Key levels", a.keyLevels.joinToString(" · "))
            }

            Text("Reasoning", style = MaterialTheme.typography.titleSmall)
            Text(a.reasoning, style = MaterialTheme.typography.bodyMedium)

            Text(
                "AI-generated technical read of your screenshot. Not financial advice.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun PlanRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold)
    }
}
