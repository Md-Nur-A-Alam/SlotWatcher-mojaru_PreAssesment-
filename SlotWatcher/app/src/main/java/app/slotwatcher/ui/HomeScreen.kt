package app.slotwatcher.ui

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.slotwatcher.Notifications
import app.slotwatcher.check.CheckScheduler
import app.slotwatcher.check.CheckService
import app.slotwatcher.check.CheckTimes
import app.slotwatcher.data.Format
import app.slotwatcher.data.Repo

@Composable
fun HomeScreen(onNavigate: (Screen) -> Unit) {
    val ctx = LocalContext.current
    val state by Repo.session.collectAsState()
    val tick = rememberResumeTick()
    val missing = remember(tick) { Permissions.missing(ctx) }
    val permLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }

    Page(
        "Slot Watcher",
        actions = { TextButton(onClick = { onNavigate(Screen.Account) }) { Text("Account") } },
    ) {
        if (missing.isNotEmpty()) {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(Modifier.padding(16.dp)) {
                    Text("Fix these so checks run on time", fontWeight = FontWeight.SemiBold)
                    missing.forEach { req ->
                        Spacer(Modifier.height(8.dp))
                        Text(req.title, fontWeight = FontWeight.Medium)
                        Text(req.why, style = MaterialTheme.typography.bodySmall)
                        TextButton(onClick = {
                            req.permission?.let { permLauncher.launch(it) }
                            req.settingsIntent?.let { runCatching { ctx.startActivity(it) } }
                        }) { Text("Fix") }
                    }
                }
            }
            Spacer(Modifier.height(16.dp))
        }

        val s = state
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp)) {
                if (s == null || !s.active) {
                    Text("Not watching", style = MaterialTheme.typography.titleLarge)
                    Spacer(Modifier.height(4.dp))
                    Text("Start a session to check for booked slots automatically.")
                } else {
                    val sess = s.session
                    val count = CheckTimes.of(sess).size
                    Text("Watching", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.height(8.dp))
                    Text(Format.day(sess.date), fontWeight = FontWeight.Medium)
                    Text("${Format.time(sess.start)} to ${Format.time(sess.end)}, every ${sess.gapMinutes} min ($count checks)")
                    Spacer(Modifier.height(8.dp))
                    Text("Next check: " + (s.nextCheck?.let { Format.time(it) } ?: "—"))
                }
                s?.lastSummary?.let {
                    Spacer(Modifier.height(4.dp))
                    Text("Last result: $it", style = MaterialTheme.typography.bodyMedium)
                }
                if (s?.active == true) {
                    Spacer(Modifier.height(12.dp))
                    OutlinedButton(onClick = {
                        CheckScheduler.cancel(ctx)
                        Repo.stopSession()
                        Notifications.cancelStatus(ctx)
                    }) { Text("Stop") }
                }
            }
        }

        Spacer(Modifier.height(16.dp))
        Button(onClick = { onNavigate(Screen.NewSession) }, modifier = Modifier.fillMaxWidth()) {
            Text("New session")
        }
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            OutlinedButton(
                onClick = {
                    CheckService.start(ctx, manual = true)
                    Toast.makeText(ctx, "Checking now…", Toast.LENGTH_SHORT).show()
                },
                modifier = Modifier.weight(1f),
            ) { Text("Check now") }
            OutlinedButton(onClick = { onNavigate(Screen.History) }, modifier = Modifier.weight(1f)) {
                Text("History")
            }
        }
        Spacer(Modifier.height(16.dp))
        Text(
            "Tip: keep the alarm volume up. The alarm uses the alarm volume, so it rings even on silent.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(16.dp))
    }
}
