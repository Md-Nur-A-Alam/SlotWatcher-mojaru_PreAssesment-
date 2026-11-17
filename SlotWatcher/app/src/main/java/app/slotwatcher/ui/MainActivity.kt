package app.slotwatcher.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import app.slotwatcher.data.CredentialStore

enum class Screen { Account, Home, NewSession, History }

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent { SlotWatcherTheme { App() } }
    }
}

@Composable
private fun App() {
    val ctx = LocalContext.current
    var screen by rememberSaveable {
        mutableStateOf(if (CredentialStore.get(ctx) == null) Screen.Account else Screen.Home)
    }
    val goHome = { screen = Screen.Home }

    BackHandler(enabled = screen != Screen.Home && CredentialStore.get(ctx) != null) { goHome() }

    when (screen) {
        Screen.Account -> AccountScreen(
            onSaved = goHome,
            onBack = if (CredentialStore.get(ctx) != null) goHome else null,
        )
        Screen.Home -> HomeScreen(onNavigate = { screen = it })
        Screen.NewSession -> NewSessionScreen(onDone = goHome, onBack = goHome)
        Screen.History -> HistoryScreen(onBack = goHome)
    }
}
