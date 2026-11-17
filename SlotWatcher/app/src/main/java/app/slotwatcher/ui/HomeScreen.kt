@file:OptIn(ExperimentalLayoutApi::class)

package app.slotwatcher.ui

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.PauseCircle
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material.icons.rounded.VolumeUp
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import app.slotwatcher.Notifications
import app.slotwatcher.R
import app.slotwatcher.check.CheckScheduler
import app.slotwatcher.check.CheckService
import app.slotwatcher.check.CheckTimes
import app.slotwatcher.data.Format
import app.slotwatcher.data.HistoryEntry
import app.slotwatcher.data.Repo
import app.slotwatcher.data.SessionState
import java.time.Duration

@Composable
fun HomeScreen(onNewSession: () -> Unit, onAccount: () -> Unit) {
    val ctx = LocalContext.current
    val state by Repo.session.collectAsState()
    val history by Repo.history.collectAsState()
    val tick = rememberResumeTick()
    val missing = remember(tick) { Permissions.missing(ctx) }
    val permLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    val active = state?.active == true
    val now = rememberNow(ticking = active)

    val checkNow = {
        CheckService.start(ctx, manual = true)
        Toast.makeText(ctx, "Checking Mojaru now…", Toast.LENGTH_SHORT).show()
    }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        HeroHeader {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Image(
                    painterResource(R.drawable.app_logo),
                    contentDescription = null,
                    modifier = Modifier.size(44.dp).clip(RoundedCornerShape(12.dp)),
                )
                Spacer(Modifier.width(Space.md))
                Column(Modifier.weight(1f)) {
                    Text("Slot Watcher", style = MaterialTheme.typography.titleLarge, color = Color.White)
                    Text(
                        "Mojaru pre-assessment monitor",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.White.copy(alpha = 0.85f),
                    )
                }
                if (active) {
                    StatusPill("Watching", Icons.Rounded.NotificationsActive, Brand.Yellow, Brand.Navy)
                } else {
                    StatusPill("Idle", Icons.Rounded.PauseCircle, Color.White.copy(alpha = 0.18f), Color.White)
                }
            }
            Spacer(Modifier.height(Space.xl))
            val s = state
            if (active && s != null) ActiveHero(s, now) else IdleHero(onNewSession)
            Spacer(Modifier.height(Space.sm))
        }

        Column(Modifier.padding(horizontal = Space.lg)) {
            if (missing.isNotEmpty()) {
                Spacer(Modifier.height(Space.lg))
                AppCard(color = MaterialTheme.colorScheme.errorContainer) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Rounded.WarningAmber, null, tint = MaterialTheme.colorScheme.onErrorContainer)
                        Spacer(Modifier.width(Space.sm))
                        Text(
                            "Allow these so checks run on time",
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                        )
                    }
                    missing.forEach { req ->
                        Row(Modifier.padding(top = Space.sm), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(req.title, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onErrorContainer)
                                Text(req.why, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onErrorContainer)
                            }
                            TextButton(onClick = {
                                req.permission?.let { permLauncher.launch(it) }
                                req.settingsIntent?.let { runCatching { ctx.startActivity(it) } }
                            }) { Text("Allow") }
                        }
                    }
                }
            }

            val s = state
            if (active && s != null) {
                SectionLabel("Current session")
                AppCard {
                    InfoLine(Icons.Rounded.CalendarMonth, "Date", Format.day(s.session.date))
                    InfoLine(Icons.Rounded.Schedule, "Window", "${Format.time(s.session.start)} – ${Format.time(s.session.end)}")
                    InfoLine(Icons.Rounded.Timer, "Every", "${s.session.gapMinutes} minutes")
                    Spacer(Modifier.height(Space.md))
                    Row(horizontalArrangement = Arrangement.spacedBy(Space.sm)) {
                        FilledTonalButton(onClick = checkNow, modifier = Modifier.weight(1f).heightIn(min = 48.dp)) {
                            Icon(Icons.Rounded.Refresh, null, Modifier.size(18.dp))
                            Spacer(Modifier.width(Space.sm))
                            Text("Check now")
                        }
                        OutlinedButton(
                            onClick = {
                                CheckScheduler.cancel(ctx)
                                Repo.stopSession()
                                Notifications.cancelStatus(ctx)
                            },
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                            modifier = Modifier.weight(1f).heightIn(min = 48.dp),
                        ) {
                            Icon(Icons.Rounded.Stop, null, Modifier.size(18.dp))
                            Spacer(Modifier.width(Space.sm))
                            Text("Stop")
                        }
                    }
                    TextButton(
                        onClick = onNewSession,
                        modifier = Modifier.fillMaxWidth().padding(top = Space.xs).heightIn(min = 48.dp),
                    ) {
                        Icon(Icons.Rounded.Add, null, Modifier.size(18.dp))
                        Spacer(Modifier.width(Space.sm))
                        Text("Start a different session")
                    }
                }
            }

            SectionLabel("Last check")
            LastResultCard(history.firstOrNull())

            if (!active) {
                Spacer(Modifier.height(Space.md))
                OutlinedButton(onClick = checkNow, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                    Icon(Icons.Rounded.Refresh, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(Space.sm))
                    Text("Check once now")
                }
            }

            Spacer(Modifier.height(Space.lg))
            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(MaterialTheme.shapes.medium)
                    .padding(vertical = Space.sm),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Rounded.VolumeUp, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(Space.sm))
                Text(
                    "Alerts use the alarm volume, so they sound even on silent. Keep it turned up.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.height(Space.xl))
        }
    }
}

@Composable
private fun ActiveHero(s: SessionState, now: java.time.LocalDateTime) {
    val times = CheckTimes.of(s.session)
    val done = times.count { !it.isAfter(now) }
    val next = s.nextCheck
    val remaining = next?.let { Duration.between(now, it).coerceAtLeast(Duration.ZERO) }
    val countdown = remaining?.let {
        val m = it.toMinutes()
        val sec = it.seconds % 60
        if (m >= 60) "%d:%02d:%02d".format(m / 60, m % 60, sec) else "%02d:%02d".format(m, sec)
    } ?: "—"

    Text("Next check in", style = MaterialTheme.typography.labelLarge, color = Color.White.copy(alpha = 0.85f))
    Text(
        countdown,
        style = MaterialTheme.typography.displaySmall,
        color = Color.White,
        modifier = Modifier.semantics {
            contentDescription = "Next check at ${next?.let { Format.time(it) } ?: "unknown"}"
            liveRegion = LiveRegionMode.Polite
        },
    )
    Text(
        (next?.let { "at ${Format.time(it)} · " } ?: "") + "check ${(done + 1).coerceAtMost(times.size)} of ${times.size}",
        style = MaterialTheme.typography.bodyMedium,
        color = Color.White.copy(alpha = 0.9f),
    )
    Spacer(Modifier.height(Space.md))
    LinearProgressIndicator(
        progress = { if (times.isEmpty()) 0f else done / times.size.toFloat() },
        color = Brand.Yellow,
        trackColor = Color.White.copy(alpha = 0.22f),
        strokeCap = StrokeCap.Round,
        gapSize = 0.dp,
        drawStopIndicator = {},
        modifier = Modifier.fillMaxWidth().height(8.dp),
    )
}

@Composable
private fun IdleHero(onNewSession: () -> Unit) {
    Text("Not watching yet", style = MaterialTheme.typography.headlineMedium, color = Color.White)
    Spacer(Modifier.height(Space.xs))
    Text(
        "Pick a date and time window. Slot Watcher checks the panel for you and announces every result.",
        style = MaterialTheme.typography.bodyMedium,
        color = Color.White.copy(alpha = 0.9f),
    )
    Spacer(Modifier.height(Space.lg))
    Button(
        onClick = onNewSession,
        colors = ButtonDefaults.buttonColors(containerColor = Brand.Yellow, contentColor = Brand.Navy),
        modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
    ) {
        Icon(Icons.Rounded.Add, null, Modifier.size(20.dp))
        Spacer(Modifier.width(Space.sm))
        Text("Start a session", style = MaterialTheme.typography.titleSmall)
    }
}

@Composable
private fun LastResultCard(e: HistoryEntry?) {
    if (e == null) {
        AppCard {
            Text("No checks yet", style = MaterialTheme.typography.titleSmall)
            Text(
                "Results appear here after the first check.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        return
    }
    val status = AppTheme.status
    val (icon, container, tint, headline) = when {
        !e.ok -> Quad(Icons.Rounded.ErrorOutline, MaterialTheme.colorScheme.errorContainer, MaterialTheme.colorScheme.onErrorContainer, "Check problem")
        e.booked > 0 -> Quad(Icons.Rounded.NotificationsActive, status.alertContainer, status.onAlertContainer, Format.slotsBooked(e.booked))
        else -> Quad(Icons.Rounded.CheckCircle, status.successContainer, status.onSuccessContainer, "No slot booked")
    }
    AppCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconBadge(icon, container = container, tint = tint, size = 48)
            Spacer(Modifier.width(Space.md))
            Column(Modifier.weight(1f)) {
                Text(headline, style = MaterialTheme.typography.titleMedium)
                Text(
                    Format.stamp(e.at),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        if (e.slots.isNotEmpty()) {
            Spacer(Modifier.height(Space.md))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(Space.sm), verticalArrangement = Arrangement.spacedBy(Space.sm)) {
                e.slots.forEach { TimeChip(slotLabel(it), emphasized = true) }
            }
        } else if (!e.ok) {
            Spacer(Modifier.height(Space.sm))
            Text(e.message, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/** "18:30 - 18:40" -> "6:30 – 6:40 PM" style label. */
fun slotLabel(raw: String): String {
    val parts = raw.split("-").map { it.trim() }
    return if (parts.size == 2) "${Format.time(parts[0])} – ${Format.time(parts[1])}" else raw
}

private data class Quad<A, B, C, D>(val a: A, val b: B, val c: C, val d: D)
