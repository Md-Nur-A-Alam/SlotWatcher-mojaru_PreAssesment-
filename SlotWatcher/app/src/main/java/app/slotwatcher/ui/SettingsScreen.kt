@file:OptIn(ExperimentalMaterial3Api::class)

package app.slotwatcher.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Logout
import androidx.compose.material.icons.rounded.AccountCircle
import androidx.compose.material.icons.rounded.BatteryChargingFull
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Code
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.RecordVoiceOver
import androidx.compose.material.icons.rounded.Vibration
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.unit.dp
import app.slotwatcher.Notifications
import app.slotwatcher.alarm.AlarmInfo
import app.slotwatcher.alarm.AlarmKind
import app.slotwatcher.alarm.AlarmRingService
import app.slotwatcher.check.CheckScheduler
import app.slotwatcher.data.CredentialStore
import app.slotwatcher.data.Format
import app.slotwatcher.data.Repo
import app.slotwatcher.data.SettingsStore
import app.slotwatcher.data.VoiceLanguage

const val REPO_URL = "https://github.com/Md-Nur-A-Alam/SlotWatcher-mojaru_PreAssesment-"

@Composable
fun SettingsScreen(onAccount: () -> Unit, onSignedOut: () -> Unit) {
    val ctx = LocalContext.current
    val uri = LocalUriHandler.current
    val settings by SettingsStore.settings.collectAsState()
    val tick = rememberResumeTick()
    val reqs = remember(tick) { Permissions.all(ctx) }
    val login = remember(tick) { CredentialStore.get(ctx)?.login }
    val permLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    var confirmSignOut by remember { mutableStateOf(false) }
    val version = remember {
        runCatching { ctx.packageManager.getPackageInfo(ctx.packageName, 0).versionName }.getOrNull() ?: "1.0"
    }

    Column(Modifier.fillMaxSize()) {
        ScreenHeader("Settings")
        Column(Modifier.verticalScroll(rememberScrollState()).padding(horizontal = Space.lg)) {

            SectionLabel("Alerts")
            AppCard {
                SwitchRow(
                    Icons.Rounded.NotificationsActive,
                    "Announce every check",
                    "Short alert with the result even when nothing is booked. Booked slots always ring until dismissed.",
                    settings.alertEveryCheck,
                ) { v -> SettingsStore.update { it.copy(alertEveryCheck = v) } }
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                SwitchRow(Icons.Rounded.Vibration, "Vibrate", "Vibrate with every alert.", settings.vibrate) { v ->
                    SettingsStore.update { it.copy(vibrate = v) }
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                Row(Modifier.padding(vertical = Space.md, horizontal = Space.xs), verticalAlignment = Alignment.CenterVertically) {
                    IconBadge(Icons.Rounded.RecordVoiceOver)
                    Spacer(Modifier.width(Space.md))
                    Column(Modifier.weight(1f)) {
                        Text("Voice language", style = MaterialTheme.typography.titleSmall)
                        Text(
                            "Bangla needs the Bangla voice in Google Text-to-speech; otherwise English is used.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                    VoiceLanguage.entries.forEachIndexed { i, lang ->
                        SegmentedButton(
                            selected = settings.voice == lang,
                            onClick = { SettingsStore.update { it.copy(voice = lang) } },
                            shape = SegmentedButtonDefaults.itemShape(i, VoiceLanguage.entries.size),
                        ) { Text(if (lang == VoiceLanguage.Bangla) "বাংলা" else "English") }
                    }
                }
                Spacer(Modifier.height(Space.md))
                OutlinedButton(
                    onClick = {
                        AlarmRingService.ring(
                            ctx,
                            AlarmInfo(
                                kind = AlarmKind.Test,
                                title = "Test alarm",
                                lines = listOf("6:30 PM – 6:40 PM", "7:10 PM – 7:20 PM"),
                                speech = Format.speechBooked(settings.voice, 2, listOf("18:30", "19:10")),
                                dateText = "This is how a booking sounds",
                                lang = settings.voice,
                            ),
                        )
                    },
                    modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                ) { Text("Play test alarm") }
            }

            SectionLabel("Mojaru account")
            AppCard {
                NavRow(
                    Icons.Rounded.AccountCircle,
                    title = login?.let(::maskLogin) ?: "Not signed in",
                    subtitle = "Change email/mobile or password",
                    onClick = onAccount,
                )
                if (login != null) {
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    NavRow(
                        Icons.AutoMirrored.Rounded.Logout,
                        title = "Remove login from this phone",
                        subtitle = "Stops watching and deletes the saved password",
                        onClick = { confirmSignOut = true },
                    )
                }
            }

            SectionLabel("Reliability")
            AppCard {
                reqs.forEachIndexed { i, r ->
                    if (i > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    Row(Modifier.heightIn(min = 56.dp).padding(vertical = Space.sm, horizontal = Space.xs), verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            if (r.ok) Icons.Rounded.CheckCircle else Icons.Rounded.WarningAmber,
                            contentDescription = if (r.ok) "Allowed" else "Not allowed",
                            tint = if (r.ok) AppTheme.status.success else MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(22.dp),
                        )
                        Spacer(Modifier.width(Space.md))
                        Column(Modifier.weight(1f)) {
                            Text(r.title, style = MaterialTheme.typography.titleSmall)
                            Text(r.why, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        if (!r.ok) {
                            TextButton(onClick = {
                                r.permission?.let { permLauncher.launch(it) }
                                r.settingsIntent?.let { runCatching { ctx.startActivity(it) } }
                            }) { Text("Allow") }
                        }
                    }
                }
                if (reqs.isEmpty()) {
                    NavRow(Icons.Rounded.BatteryChargingFull, "All set", "Nothing to allow on this Android version.", onClick = null)
                }
            }

            SectionLabel("About")
            AppCard {
                NavRow(Icons.Rounded.Info, "Slot Watcher $version", "Watches Mojaru pre-assessment slot bookings", onClick = null)
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                NavRow(Icons.Rounded.Code, "Source code", "GitHub repository", onClick = { uri.openUri(REPO_URL) })
            }
            Spacer(Modifier.height(Space.xl))
        }
    }

    if (confirmSignOut) {
        AlertDialog(
            onDismissRequest = { confirmSignOut = false },
            title = { Text("Remove saved login?") },
            text = { Text("Watching stops and your Mojaru password is deleted from this phone.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmSignOut = false
                    CheckScheduler.cancel(ctx)
                    Repo.stopSession()
                    Notifications.cancelStatus(ctx)
                    CredentialStore.clear(ctx)
                    onSignedOut()
                }) { Text("Remove", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { confirmSignOut = false }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun SwitchRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String,
    checked: Boolean,
    onChange: (Boolean) -> Unit,
) {
    Row(
        Modifier.heightIn(min = 64.dp).padding(vertical = Space.sm, horizontal = Space.xs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconBadge(icon)
        Spacer(Modifier.width(Space.md))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleSmall)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(Modifier.width(Space.sm))
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

/** "someone@gmail.com" -> "so•••@gmail.com", "01712345678" -> "017•••••678". */
fun maskLogin(s: String): String {
    val at = s.indexOf('@')
    return if (at > 0) {
        s.take(2.coerceAtMost(at)) + "•••" + s.substring(at)
    } else if (s.length > 6) {
        s.take(3) + "•".repeat(s.length - 6) + s.takeLast(3)
    } else s
}
