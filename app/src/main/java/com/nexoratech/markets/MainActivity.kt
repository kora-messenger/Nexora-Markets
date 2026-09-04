package com.nexoratech.markets

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.nexoratech.markets.auth.AccountViewModel
import com.nexoratech.markets.data.model.AssetClass
import com.nexoratech.markets.ui.MarketsViewModel
import com.nexoratech.markets.ui.screens.AnalyzeScreen
import com.nexoratech.markets.ui.screens.CreateAccountScreen
import com.nexoratech.markets.ui.screens.LoginScreen
import com.nexoratech.markets.ui.screens.ProfileOnboardingScreen
import com.nexoratech.markets.ui.screens.MarketsScreen
import com.nexoratech.markets.ui.screens.PayoffScreen
import com.nexoratech.markets.ui.screens.PaywallScreen
import com.nexoratech.markets.ui.screens.SettingsScreen
import com.nexoratech.markets.ui.screens.SignalDetailScreen
import com.nexoratech.markets.ui.screens.WelcomeScreen
import com.nexoratech.markets.ui.theme.NexoraMarketsTheme
import com.nexoratech.markets.ui.theme.Teal400

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
    val account: AccountViewModel = viewModel(factory = AccountViewModel.Factory(app))
    val viewModel: MarketsViewModel = viewModel(factory = MarketsViewModel.Factory(app))
    val authState by account.state.collectAsState()
    val profileComplete by account.profileComplete.collectAsState()
    val access by account.access.collectAsState()

    when (val s = authState) {
        is AccountViewModel.AuthState.CheckingSession -> {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = Teal400, strokeWidth = 2.5.dp)
            }
        }
        is AccountViewModel.AuthState.SignedOut -> {
            NavHost(navController = navController, startDestination = "welcome") {
                composable("welcome") {
                    WelcomeScreen(
                        onCreateAccount = { navController.navigate("createAccount") },
                        onSignIn = { navController.navigate("login") },
                    )
                }
                composable("createAccount") {
                    CreateAccountScreen(
                        viewModel = account,
                        onBack = { navController.popBackStack() },
                    )
                }
                composable("login") {
                    LoginScreen(
                        viewModel = account,
                        onBack = { navController.popBackStack() },
                        onCreateAccount = {
                            navController.navigate("createAccount") {
                                popUpTo("welcome") { inclusive = true }
                            }
                        },
                    )
                }
            }
        }
        is AccountViewModel.AuthState.SignedIn -> when {
            // Still resolving the profile — keep the boot spinner up.
            profileComplete == null -> {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = Teal400, strokeWidth = 2.5.dp)
                }
            }
            // Questionnaire pending — the only way forward is through it.
            profileComplete == false -> {
                NavHost(navController = navController, startDestination = "onboarding") {
                    composable("onboarding") {
                        ProfileOnboardingScreen(
                            viewModel = account,
                            onDone = {
                                navController.navigate("payoff") {
                                    popUpTo("onboarding") { inclusive = true }
                                }
                            },
                        )
                    }
                    composable("payoff") {
                        PayoffScreen(
                            viewModel = account,
                            onEnterMarkets = {
                                navController.navigate("markets") {
                                    popUpTo("payoff") { inclusive = true }
                                }
                            },
                        )
                    }
                }
            }
            // Trial ended and no active subscription — the app is the paywall.
            profileComplete == true && access?.isExpired == true -> {
                PaywallScreen(
                    viewModel = account,
                    onSignOut = {
                        account.signOut { navController.navigate("welcome") { popUpTo(0) } }
                    },
                )
            }
            else -> NavHost(navController = navController, startDestination = "markets") {
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
                    SettingsScreen(
                        app = app,
                        viewModel = viewModel,
                        account = account,
                        user = s.user,
                        onEditProfile = {
                            navController.navigate("onboarding") {
                                popUpTo("onboarding") { inclusive = true }
                            }
                        },
                        onBack = { navController.popBackStack() },
                    )
                }
                composable("onboarding") {
                    ProfileOnboardingScreen(
                        viewModel = account,
                        onDone = { navController.popBackStack() },
                    )
                }
            }
        }
    }
}
