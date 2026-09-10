package com.wayfare.app

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.wayfare.app.feature.AuthViewModel
import com.wayfare.app.feature.TripDetailViewModel
import com.wayfare.app.feature.TripsViewModel
import com.wayfare.app.ui.AuthScreen
import com.wayfare.app.ui.Clay
import com.wayfare.app.ui.Paper
import com.wayfare.app.ui.RecoveryScreen
import com.wayfare.app.ui.TripDetailScreen
import com.wayfare.app.ui.TripsScreen
import com.wayfare.app.ui.WayfareTheme
import io.github.jan.supabase.auth.handleDeeplinks
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    private val container get() = (application as WayfareApplication).container
    private lateinit var authViewModel: AuthViewModel

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        authViewModel = ViewModelProvider(this, factory { AuthViewModel(container) })[AuthViewModel::class.java]
        handleIncoming(intent)
        setContent {
            WayfareTheme { WayfareApp(authViewModel, container) }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIncoming(intent)
    }

    private fun handleIncoming(intent: Intent) {
        val uri = intent.data ?: return
        if (uri.scheme == "wayfare") {
            container.supabase.handleDeeplinks(intent)
            if (uri.toString().contains("recovery", ignoreCase = true)) authViewModel.beginRecovery()
        }
        if (uri.scheme == "https" && uri.host == "getwayfare.netlify.app") {
            val code = uri.pathSegments.takeIf { it.size == 2 && it[0] == "join" }?.get(1)
            if (!code.isNullOrBlank()) lifecycleScope.launch {
                container.preferences.setPendingInvite(code.uppercase())
            }
        }
    }
}

@Composable
private fun WayfareApp(authViewModel: AuthViewModel, container: AppContainer) {
    val auth by authViewModel.state.collectAsStateWithLifecycle()
    when {
        auth.initializing -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(color = Clay)
        }
        auth.recovery -> RecoveryScreen(auth, authViewModel)
        auth.userId == null -> AuthScreen(auth, authViewModel)
        else -> {
            val accountId = requireNotNull(auth.userId)
            val navController = rememberNavController()
            NavHost(navController = navController, startDestination = "trips") {
                composable("trips") {
                    val tripsViewModel: TripsViewModel = viewModel(
                        key = "trips-$accountId",
                        factory = factory { TripsViewModel(accountId, container) },
                    )
                    TripsScreen(
                        tripsViewModel,
                        container,
                        onOpenTrip = { navController.navigate("trip/$it") },
                        onSignOut = authViewModel::signOut,
                    )
                }
                composable(
                    route = "trip/{tripId}",
                    arguments = listOf(navArgument("tripId") { type = NavType.StringType }),
                ) { entry ->
                    val tripId = requireNotNull(entry.arguments?.getString("tripId"))
                    val detailViewModel: TripDetailViewModel = viewModel(
                        key = "trip-$tripId-$accountId",
                        factory = factory { TripDetailViewModel(accountId, tripId, container) },
                    )
                    TripDetailScreen(accountId, detailViewModel, container) { navController.popBackStack() }
                }
            }
        }
    }
}

private fun <T : ViewModel> factory(create: () -> T): ViewModelProvider.Factory =
    object : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <VM : ViewModel> create(modelClass: Class<VM>): VM = create() as VM
    }
