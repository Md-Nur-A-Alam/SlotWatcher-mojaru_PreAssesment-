package app.slotwatcher.ui

import android.app.Activity
import android.os.Bundle
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsTopHeight
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import app.slotwatcher.BuildConfig
import app.slotwatcher.data.CredentialStore

enum class Tab(val label: String, val icon: ImageVector, val selectedIcon: ImageVector) {
    Home("Home", Icons.Outlined.Home, Icons.Rounded.Home),
    History("History", Icons.Outlined.History, Icons.Rounded.History),
    Settings("Settings", Icons.Outlined.Settings, Icons.Rounded.Settings),
    Developer("Developer", Icons.Outlined.Person, Icons.Rounded.Person),
}

/** Full-screen pages opened on top of the tabs. */
enum class Overlay { None, NewSession, Account }

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val demo = BuildConfig.DEBUG && intent.getBooleanExtra("demo", false)
        if (demo && savedInstanceState == null) DemoData.seed()
        setContent { SlotWatcherTheme { App(demo) } }
    }
}

@Composable
private fun App(demo: Boolean) {
    val ctx = LocalContext.current
    var hasLogin by rememberSaveable { mutableStateOf(demo || CredentialStore.get(ctx) != null) }
    var tab by rememberSaveable { mutableStateOf(Tab.Home) }
    var overlay by rememberSaveable { mutableStateOf(if (hasLogin) Overlay.None else Overlay.Account) }

    BackHandler(enabled = overlay != Overlay.None && hasLogin) { overlay = Overlay.None }
    BackHandler(enabled = overlay == Overlay.None && tab != Tab.Home) { tab = Tab.Home }

    AnimatedContent(
        targetState = overlay,
        transitionSpec = {
            if (targetState != Overlay.None) {
                (slideInHorizontally(tween(260)) { it / 3 } + fadeIn(tween(200))) togetherWith fadeOut(tween(120))
            } else {
                fadeIn(tween(200)) togetherWith (slideOutHorizontally(tween(200)) { it / 3 } + fadeOut(tween(160)))
            }
        },
        label = "overlay",
    ) { current ->
        when (current) {
            Overlay.Account -> {
                StatusBarIcons(light = true)
                AccountScreen(
                    firstRun = !hasLogin,
                    onSaved = { hasLogin = true; overlay = Overlay.None },
                    onBack = if (hasLogin) ({ overlay = Overlay.None }) else null,
                )
            }
            Overlay.NewSession -> {
                StatusBarIcons(light = false)
                NewSessionScreen(onDone = { overlay = Overlay.None; tab = Tab.Home }, onBack = { overlay = Overlay.None })
            }
            Overlay.None -> Tabs(
                tab = tab,
                onTab = { tab = it },
                onNewSession = { overlay = Overlay.NewSession },
                onAccount = { overlay = Overlay.Account },
                onSignedOut = { hasLogin = false; overlay = Overlay.Account },
            )
        }
    }
}

@Composable
private fun Tabs(
    tab: Tab,
    onTab: (Tab) -> Unit,
    onNewSession: () -> Unit,
    onAccount: () -> Unit,
    onSignedOut: () -> Unit,
) {
    Scaffold(
        contentWindowInsets = WindowInsets(0),
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {
            NavigationBar(containerColor = MaterialTheme.colorScheme.surfaceContainerLowest) {
                Tab.entries.forEach { t ->
                    NavigationBarItem(
                        selected = t == tab,
                        onClick = { onTab(t) },
                        icon = { Icon(if (t == tab) t.selectedIcon else t.icon, contentDescription = null) },
                        label = { Text(t.label) },
                        colors = NavigationBarItemDefaults.colors(
                            indicatorColor = MaterialTheme.colorScheme.primaryContainer,
                            selectedIconColor = MaterialTheme.colorScheme.onPrimaryContainer,
                            selectedTextColor = MaterialTheme.colorScheme.primary,
                        ),
                    )
                }
            }
        },
    ) { pad ->
        val heroTab = tab == Tab.Home || tab == Tab.Developer
        StatusBarIcons(light = heroTab)
        Box(Modifier.fillMaxSize().padding(pad)) {
            AnimatedContent(
                targetState = tab,
                transitionSpec = { fadeIn(tween(180)) togetherWith fadeOut(tween(120)) },
                label = "tab",
            ) { t ->
                when (t) {
                    Tab.Home -> HomeScreen(onNewSession = onNewSession, onAccount = onAccount)
                    Tab.History -> HistoryScreen()
                    Tab.Settings -> SettingsScreen(onAccount = onAccount, onSignedOut = onSignedOut)
                    Tab.Developer -> DeveloperScreen()
                }
            }
            // Keeps scrolled content from running under the status bar.
            Box(
                Modifier
                    .fillMaxWidth()
                    .windowInsetsTopHeight(WindowInsets.statusBars)
                    .background(if (heroTab) Brand.DeepPurple else MaterialTheme.colorScheme.background),
            )
        }
    }
}

/** Light status-bar icons over the purple headers, dark ones over the light screens. */
@Composable
fun StatusBarIcons(light: Boolean) {
    val view = LocalView.current
    val dark = isSystemInDarkTheme()
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window ?: return@SideEffect
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !light && !dark
        }
    }
}
