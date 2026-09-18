package com.example

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.data.local.SettingsDataStore
import com.example.repository.HardwareRepository
import com.example.ui.chat.ChatScreen
import com.example.ui.chat.ChatViewModel
import com.example.ui.library.LibraryScreen
import com.example.ui.library.LibraryViewModel
import com.example.ui.onboarding.OnboardingScreen
import com.example.ui.plugins.PluginsScreen
import com.example.ui.plugins.PluginsViewModel
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

            // Download progress is shown in a notification, which needs consent on Android 13+.
            RequestNotificationPermissionOnce()

            VipoTheme(pureBlack = pureBlack) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    if (isOnboardingCompleted != null) {
                        VipoNavHost(
                            startDestination = if (isOnboardingCompleted == true) Routes.CHAT else Routes.ONBOARDING,
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
private fun RequestNotificationPermissionOnce() {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return

    val context = LocalContext.current
    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { /* Downloads keep working either way, only the progress notification is affected. */ }

    LaunchedEffect(Unit) {
        val granted = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.POST_NOTIFICATIONS
        ) == PackageManager.PERMISSION_GRANTED
        if (!granted) {
            launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }
}

object Routes {
    const val ONBOARDING = "onboarding"
    const val CHAT = "chat"
    const val LIBRARY = "library"
    const val PLUGINS = "plugins"
    const val SETTINGS = "settings"
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
    val libraryViewModel: LibraryViewModel = viewModel()
    val pluginsViewModel: PluginsViewModel = viewModel()
    val settingsViewModel: SettingsViewModel = viewModel()

    NavHost(
        navController = navController,
        startDestination = startDestination
    ) {
        composable(Routes.ONBOARDING) {
            OnboardingScreen(
                hardwareInfo = hardwareRepository.getDeviceHardwareInfo(),
                onComplete = { startDownloadStarter ->
                    scope.launch {
                        settingsDataStore.setOnboardingCompleted(true)
                    }
                    if (startDownloadStarter) {
                        val starterModel = libraryViewModel.catalog.value
                            .firstOrNull { it.id == "llama-3.2-1b-instruct" }
                        if (starterModel != null) {
                            val variant = starterModel.variants.firstOrNull { it.name.contains("Q4") }
                                ?: starterModel.variants.first()
                            libraryViewModel.startDownload(starterModel, variant)
                        }
                    }
                    navController.navigate(Routes.CHAT) {
                        popUpTo(Routes.ONBOARDING) { inclusive = true }
                    }
                }
            )
        }

        composable(Routes.CHAT) {
            ChatScreen(
                viewModel = chatViewModel,
                onNavigateToLibrary = { navController.navigate(Routes.LIBRARY) },
                onNavigateToPlugins = { navController.navigate(Routes.PLUGINS) },
                onNavigateToSettings = { navController.navigate(Routes.SETTINGS) }
            )
        }

        composable(Routes.LIBRARY) {
            LibraryScreen(
                viewModel = libraryViewModel,
                onNavigateBack = { navController.popBackStack() },
                onOpenChatWithModel = { path, name ->
                    chatViewModel.switchModel(path, name)
                    navController.popBackStack(Routes.CHAT, inclusive = false)
                }
            )
        }

        composable(Routes.PLUGINS) {
            PluginsScreen(
                viewModel = pluginsViewModel,
                onNavigateBack = { navController.popBackStack() }
            )
        }

        composable(Routes.SETTINGS) {
            SettingsScreen(
                viewModel = settingsViewModel,
                onNavigateBack = { navController.popBackStack() }
            )
        }
    }
}
