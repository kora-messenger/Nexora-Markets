package com.nexoratech.markets

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.nexoratech.markets.data.model.AssetClass
import com.nexoratech.markets.ui.MarketsViewModel
import com.nexoratech.markets.ui.screens.AnalyzeScreen
import com.nexoratech.markets.ui.screens.MarketsScreen
import com.nexoratech.markets.ui.screens.SettingsScreen
import com.nexoratech.markets.ui.screens.SignalDetailScreen
import com.nexoratech.markets.ui.theme.NexoraMarketsTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        val app = application as NexoraApp
        setContent {
            NexoraMarketsTheme {
                NexoraApp(app)
            }
        }
    }
}

@Composable
private fun NexoraApp(app: NexoraApp) {
    val navController = rememberNavController()
    val viewModel: MarketsViewModel = viewModel(factory = MarketsViewModel.Factory(app))

    NavHost(navController = navController, startDestination = "markets") {
        composable("markets") {
            MarketsScreen(
                viewModel = viewModel,
                onOpenSignal = { signal ->
                    navController.navigate("signal/${signal.symbol}/${signal.assetClass.name}")
                },
                onOpenAnalyze = { navController.navigate("analyze") },
                onOpenSettings = { navController.navigate("settings") },
            )
        }
        composable(
            route = "signal/{symbol}/{assetClass}",
            arguments = listOf(
                navArgument("symbol") { type = NavType.StringType },
                navArgument("assetClass") { type = NavType.StringType },
            ),
        ) { entry ->
            val symbol = entry.arguments?.getString("symbol").orEmpty()
            val assetClass = AssetClass.valueOf(
                entry.arguments?.getString("assetClass") ?: AssetClass.CRYPTO.name
            )
            val signal = viewModel.signalFor(symbol, assetClass)
            if (signal == null) {
                navController.popBackStack()
            } else {
                SignalDetailScreen(signal = signal, onBack = { navController.popBackStack() })
            }
        }
        composable("analyze") {
            AnalyzeScreen(app = app, onBack = { navController.popBackStack() })
        }
        composable("settings") {
            SettingsScreen(app = app, viewModel = viewModel, onBack = { navController.popBackStack() })
        }
    }
}
