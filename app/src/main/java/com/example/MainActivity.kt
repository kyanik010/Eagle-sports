package com.example

import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.data.model.ChannelEntity
import com.example.ui.auth.AuthViewModel
import com.example.ui.auth.LoginScreen
import com.example.ui.channels.ChannelsScreen
import com.example.ui.channels.ChannelsViewModel
import com.example.ui.home.HomeScreen
import com.example.ui.player.PlayerScreen
import com.example.ui.player.PlayerViewModel
import com.example.ui.settings.SettingsScreen
import com.example.ui.settings.SettingsViewModel
import com.example.ui.theme.MyApplicationTheme
import com.example.ui.theme.NavyDeep

sealed class AppDestination {
    object Login : AppDestination()
    object Home : AppDestination()
    data class Channels(val isFavorites: Boolean = false) : AppDestination()
    data class Player(val channel: ChannelEntity) : AppDestination()
    object Settings : AppDestination()
}

@ExperimentalMaterial3Api
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Keep screen awake for streaming IPTV
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        setContent {
            MyApplicationTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = NavyDeep
                ) {
                    EagleSportsAppContent()
                }
            }
        }
    }
}

@ExperimentalMaterial3Api
@Composable
fun EagleSportsAppContent() {
    val app = EagleSportsApp.instance
    val preferencesManager = app.preferencesManager
    val repository = app.channelRepository

    val isLoggedIn by preferencesManager.isLoggedIn.collectAsState(initial = false)
    val userSession by preferencesManager.userSession.collectAsState(initial = null)
    val totalChannels by repository.totalCount.collectAsState(initial = 0)
    val favoritesList by repository.favoriteChannels.collectAsState(initial = emptyList())
    val featuredChannels by repository.featuredChannels.collectAsState(initial = emptyList())

    // Backstack navigation management
    val backStack = remember { mutableStateListOf<AppDestination>(AppDestination.Home) }
    val currentDestination = backStack.lastOrNull() ?: AppDestination.Home

    // Synchronize initial login status
    LaunchedEffect(isLoggedIn) {
        if (!isLoggedIn) {
            backStack.clear()
            backStack.add(AppDestination.Login)
        } else if (backStack.contains(AppDestination.Login)) {
            backStack.clear()
            backStack.add(AppDestination.Home)
        }
    }

    // Custom back handling to maintain stack integrity
    BackHandler(enabled = backStack.size > 1) {
        backStack.removeAt(backStack.lastIndex)
    }

    AnimatedContent(
        targetState = currentDestination,
        transitionSpec = { fadeIn() togetherWith fadeOut() },
        label = "screen_transition"
    ) { destination ->
        when (destination) {
            is AppDestination.Login -> {
                val authViewModel = remember {
                    AuthViewModel(preferencesManager, repository)
                }
                LoginScreen(
                    viewModel = authViewModel,
                    onLoginSuccess = {
                        backStack.clear()
                        backStack.add(AppDestination.Home)
                    }
                )
            }

            is AppDestination.Home -> {
                HomeScreen(
                    channelCount = totalChannels,
                    favoriteCount = favoritesList.size,
                    username = userSession?.username ?: "مشترك رياضي",
                    featuredChannels = featuredChannels,
                    onFeaturedChannelSelected = { channel -> backStack.add(AppDestination.Player(channel)) },
                    onNavigateChannels = {
                        backStack.add(AppDestination.Channels(isFavorites = false))
                    },
                    onNavigateFavorites = {
                        backStack.add(AppDestination.Channels(isFavorites = true))
                    },
                    onNavigateSettings = {
                        backStack.add(AppDestination.Settings)
                    }
                )
            }

            is AppDestination.Channels -> {
                val channelsViewModel = remember {
                    ChannelsViewModel(repository, preferencesManager)
                }
                ChannelsScreen(
                    viewModel = channelsViewModel,
                    isFavoritesOnly = destination.isFavorites,
                    onChannelSelected = { channel ->
                        backStack.add(AppDestination.Player(channel))
                    },
                    onBack = {
                        if (backStack.size > 1) backStack.removeAt(backStack.lastIndex)
                    }
                )
            }

            is AppDestination.Player -> {
                val playerViewModel = viewModel<PlayerViewModel>()
                PlayerScreen(
                    channel = destination.channel,
                    viewModel = playerViewModel,
                    onBack = {
                        if (backStack.size > 1) backStack.removeAt(backStack.lastIndex)
                    }
                )
            }

            is AppDestination.Settings -> {
                val settingsViewModel = remember {
                    SettingsViewModel(preferencesManager, repository)
                }
                SettingsScreen(
                    viewModel = settingsViewModel,
                    onBack = {
                        if (backStack.size > 1) backStack.removeAt(backStack.lastIndex)
                    },
                    onLogout = {
                        backStack.clear()
                        backStack.add(AppDestination.Login)
                    }
                )
            }
        }
    }
}
