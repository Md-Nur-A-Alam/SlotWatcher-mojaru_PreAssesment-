@file:OptIn(ExperimentalFoundationApi::class, ExperimentalLayoutApi::class)

package app.slotwatcher.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.DeleteSweep
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import app.slotwatcher.data.Format
import app.slotwatcher.data.HistoryEntry
import app.slotwatcher.data.Repo
import java.time.LocalDate

/** FR-25: past checks, newest first, grouped by day. */
@Composable
fun HistoryScreen() {
    val history by Repo.history.collectAsState()
    var confirmClear by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxSize()) {
        ScreenHeader(
            "History",
            subtitle = "Every check Slot Watcher has run",
            actions = {
                if (history.isNotEmpty()) {
                    IconButton(onClick = { confirmClear = true }) {
                        Icon(Icons.Outlined.DeleteSweep, contentDescription = "Clear history")
                    }
                }
            },
        )

        if (history.isEmpty()) {
            EmptyState(
                Icons.Rounded.History,
                "No checks yet",
                "Start a session or tap Check now on Home. Each result is listed here.",
            )
            return@Column
        }

        val today = LocalDate.now()
        val todays = history.filter { it.at.toLocalDate() == today }
        val groups = history.groupBy { it.at.toLocalDate() }

        LazyColumn(
            contentPadding = PaddingValues(start = Space.lg, end = Space.lg, bottom = Space.xl),
            verticalArrangement = Arrangement.spacedBy(Space.sm),
        ) {
            item {
                Text(
                    "TODAY AT A GLANCE",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = Space.xs, bottom = Space.sm),
                )
                Row(
                    horizontalArrangement = Arrangement.spacedBy(Space.sm),
                    modifier = Modifier.padding(bottom = Space.sm).height(IntrinsicSize.Min),
                ) {
                    StatTile("Checks", todays.size.toString(), Modifier.weight(1f).fillMaxHeight())
                    StatTile("Booked", todays.count { it.booked > 0 }.toString(), Modifier.weight(1f).fillMaxHeight())
                    StatTile("Problems", todays.count { !it.ok }.toString(), Modifier.weight(1f).fillMaxHeight())
                }
            }
            groups.forEach { (day, entries) ->
                stickyHeader(key = day.toString()) {
                    Text(
                        when (day) {
                            today -> "Today"
                            today.minusDays(1) -> "Yesterday"
                            else -> Format.day(day)
                        },
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(MaterialTheme.colorScheme.background)
                            .padding(vertical = Space.sm)
                            .semantics { heading() },
                    )
                }
                items(entries, key = { it.at.toString() + it.message }) { HistoryRow(it) }
            }
        }
    }

    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            title = { Text("Clear history?") },
            text = { Text("All ${history.size} check results will be removed from this phone.") },
            confirmButton = {
                TextButton(onClick = { Repo.clearHistory(); confirmClear = false }) {
                    Text("Clear", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = { TextButton(onClick = { confirmClear = false }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun StatTile(label: String, value: String, modifier: Modifier = Modifier) {
    AppCard(modifier) {
        Text(value, style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.primary)
        Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun HistoryRow(e: HistoryEntry) {
    val status = AppTheme.status
    val icon = when { !e.ok -> Icons.Rounded.ErrorOutline; e.booked > 0 -> Icons.Rounded.NotificationsActive; else -> Icons.Rounded.CheckCircle }
    val container: Color
    val tint: Color
    when {
        !e.ok -> { container = MaterialTheme.colorScheme.errorContainer; tint = MaterialTheme.colorScheme.onErrorContainer }
        e.booked > 0 -> { container = status.alertContainer; tint = status.onAlertContainer }
        else -> { container = status.successContainer; tint = status.onSuccessContainer }
    }
    val headline = when { !e.ok -> "Check problem"; e.booked > 0 -> Format.slotsBooked(e.booked); else -> "No slot booked" }

    AppCard {
        Row(verticalAlignment = Alignment.Top) {
            IconBadge(icon, container = container, tint = tint)
            Spacer(Modifier.width(Space.md))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(headline, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                    Text(Format.time(e.at), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                if (e.slots.isNotEmpty()) {
                    Spacer(Modifier.height(Space.sm))
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        e.slots.forEach { TimeChip(slotLabel(it), emphasized = true) }
                    }
                } else if (!e.ok || e.message.contains("page showed")) {
                    Text(e.message, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}
