package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.data.local.SettingsDataStore
import com.example.repository.HardwareRepository
import com.example.ui.chat.ChatScreen
import com.example.ui.chat.ChatViewModel
import com.example.ui.hub.ModelHubScreen
import com.example.ui.hub.ModelHubViewModel
import com.example.ui.onboarding.OnboardingScreen
import com.example.ui.settings.SettingsScreen
import com.example.ui.settings.SettingsViewModel
import com.example.ui.theme.VipoTheme
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val settingsDataStore = SettingsDataStore(applicationContext)
        val hardwareRepository = HardwareRepository(applicationContext)

        setContent {
            val pureBlack by settingsDataStore.pureBlackTheme.collectAsState(initial = true)
            val isOnboardingCompleted by settingsDataStore.isOnboardingCompleted.collectAsState(initial = null)

            VipoTheme(pureBlack = pureBlack) {
                Surface(modifier = Modifier.fillMaxSize()) {
                    if (isOnboardingCompleted != null) {
                        VipoNavHost(
                            startDestination = if (isOnboardingCompleted == true) "chat" else "onboarding",
                            settingsDataStore = settingsDataStore,
                            hardwareRepository = hardwareRepository
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun VipoNavHost(
    startDestination: String,
    settingsDataStore: SettingsDataStore,
    hardwareRepository: HardwareRepository
) {
    val navController = rememberNavController()
    val scope = rememberCoroutineScope()

    val chatViewModel: ChatViewModel = viewModel()
    val hubViewModel: ModelHubViewModel = viewModel()
    val settingsViewModel: SettingsViewModel = viewModel()

    NavHost(
        navController = navController,
        startDestination = startDestination
    ) {
        composable("onboarding") {
            OnboardingScreen(
                hardwareInfo = hardwareRepository.getDeviceHardwareInfo(),
                onComplete = { startDownloadStarter ->
                    scope.launch {
                        settingsDataStore.setOnboardingCompleted(true)
                    }
                    if (startDownloadStarter) {
                        // Start downloading the starter model and navigate to hub or chat
                        val starterModel = hubViewModel.catalog.value.firstOrNull { it.id == "llama-3.2-1b-instruct" }
                        if (starterModel != null) {
                            val v = starterModel.variants.firstOrNull { it.name.contains("Q4") } ?: starterModel.variants.first()
                            hubViewModel.startDownload(starterModel, v)
                        }
                    }
                    navController.navigate("chat") {
                        popUpTo("onboarding") { inclusive = true }
                    }
                }
            )
        }

        composable("chat") {
            ChatScreen(
                viewModel = chatViewModel,
                onNavigateToHub = { navController.navigate("hub") },
                onNavigateToSettings = { navController.navigate("settings") }
            )
        }

        composable("hub") {
            ModelHubScreen(
                viewModel = hubViewModel,
                onNavigateBack = { navController.popBackStack() },
                onOpenChatWithModel = { path, name ->
                    chatViewModel.switchModel(path, name)
                    navController.navigate("chat") {
                        popUpTo("chat") { inclusive = false }
                    }
                }
            )
        }

        composable("settings") {
            SettingsScreen(
                viewModel = settingsViewModel,
                onNavigateBack = { navController.popBackStack() }
            )
        }
    }
}
