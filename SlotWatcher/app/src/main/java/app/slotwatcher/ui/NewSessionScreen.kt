@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)

package app.slotwatcher.ui

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
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.SportsScore
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import app.slotwatcher.Notifications
import app.slotwatcher.check.CheckScheduler
import app.slotwatcher.check.CheckTimes
import app.slotwatcher.check.WatchSession
import app.slotwatcher.data.Format
import app.slotwatcher.data.Repo
import app.slotwatcher.data.SettingsStore
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneOffset

private val GAP_PRESETS = listOf(5, 10, 15, 20, 30)

/** FR-4 to FR-10: date, start, end, gap, then Start. */
@Composable
fun NewSessionScreen(onDone: () -> Unit, onBack: () -> Unit) {
    val ctx = LocalContext.current
    val previous = remember { Repo.session.value?.session }
    var date by remember { mutableStateOf(LocalDate.now()) }
    var start by remember { mutableStateOf(previous?.start ?: LocalTime.of(18, 25)) }
    var end by remember { mutableStateOf(previous?.end ?: LocalTime.of(21, 15)) }
    var gap by remember { mutableStateOf(previous?.gapMinutes ?: 10) }
    var customGap by remember { mutableStateOf(if (gap in GAP_PRESETS) "" else gap.toString()) }
    var useCustom by remember { mutableStateOf(gap !in GAP_PRESETS) }

    var pickDate by remember { mutableStateOf(false) }
    var pickStart by remember { mutableStateOf(false) }
    var pickEnd by remember { mutableStateOf(false) }
    var confirmReplace by remember { mutableStateOf(false) }

    val gapMinutes = if (useCustom) customGap.toIntOrNull() ?: 0 else gap
    val session = WatchSession(date, start, end, gapMinutes)
    val now = rememberNow(ticking = true)
    val error = CheckTimes.validate(session, now)
    val times = CheckTimes.of(session)
    val upcoming = times.filter { it.isAfter(now) }
    val announceAll = SettingsStore.settings.value.alertEveryCheck

    fun begin() {
        Repo.startSession(session)
        val next = CheckScheduler.scheduleNext(ctx, session, LocalDateTime.now())
        if (next != null) {
            Notifications.showStatus(
                ctx, "Watching ${Format.shortDay(session.date)} · next check ${Format.time(next)}", ongoing = true,
            )
        }
        onDone()
    }

    Column(Modifier.fillMaxSize()) {
        ScreenHeader("New session", subtitle = "Choose when Slot Watcher should check", onBack = onBack)

        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Space.lg),
        ) {
            SectionLabel("When")
            AppCard {
                PickerRow(Icons.Rounded.CalendarMonth, "Date", Format.day(date)) { pickDate = true }
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                PickerRow(Icons.Rounded.Schedule, "First check", Format.time(start)) { pickStart = true }
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                PickerRow(Icons.Rounded.SportsScore, "Last check", Format.time(end)) { pickEnd = true }
            }

            SectionLabel("Check every")
            AppCard {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(Space.sm), verticalArrangement = Arrangement.spacedBy(Space.sm)) {
                    GAP_PRESETS.forEach { m ->
                        GapChip("$m min", selected = !useCustom && gap == m) { useCustom = false; gap = m }
                    }
                    GapChip("Custom", selected = useCustom) { useCustom = true }
                }
                if (useCustom) {
                    Spacer(Modifier.height(Space.md))
                    OutlinedTextField(
                        value = customGap,
                        onValueChange = { v -> customGap = v.filter { it.isDigit() }.take(3) },
                        label = { Text("Minutes between checks") },
                        supportingText = { Text("${CheckTimes.MIN_GAP}–${CheckTimes.MAX_GAP} minutes") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }

            SectionLabel("Preview")
            AppCard {
                if (times.isEmpty()) {
                    Text("Set a valid window to see the check times.", style = MaterialTheme.typography.bodyMedium)
                } else {
                    Text(
                        "${times.size} checks · ${Format.time(times.first())} → ${Format.time(times.last())}",
                        style = MaterialTheme.typography.titleSmall,
                    )
                    if (upcoming.size < times.size && upcoming.isNotEmpty()) {
                        Text(
                            "${times.size - upcoming.size} already passed today and will be skipped.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Spacer(Modifier.height(Space.md))
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        val shown = times.take(30)
                        shown.forEach { t -> TimeChip(Format.time(t)) }
                        if (times.size > shown.size) TimeChip("+${times.size - shown.size} more")
                    }
                    Spacer(Modifier.height(Space.md))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Rounded.NotificationsActive, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(Space.sm))
                        Text(
                            if (announceAll) "Every check is announced. Booked slots ring until you dismiss."
                            else "Rings only when slots are booked, until you dismiss.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            if (error != null) {
                Spacer(Modifier.height(Space.md))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.ErrorOutline, null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(Space.sm))
                    Text(error, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
                }
            }
            Spacer(Modifier.height(Space.xl))
        }

        Surface(color = MaterialTheme.colorScheme.surfaceContainerLowest, shadowElevation = 8.dp) {
            Button(
                enabled = error == null,
                onClick = { if (Repo.session.value?.active == true) confirmReplace = true else begin() },
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .imePadding()
                    .padding(Space.lg)
                    .heightIn(min = 52.dp),
            ) {
                Icon(Icons.Rounded.PlayArrow, null, Modifier.size(20.dp))
                Spacer(Modifier.width(Space.sm))
                Text("Start watching", style = MaterialTheme.typography.titleSmall)
            }
        }
    }

    if (pickDate) {
        val today = LocalDate.now()
        val state = rememberDatePickerState(
            initialSelectedDateMillis = date.atStartOfDay().toInstant(ZoneOffset.UTC).toEpochMilli(),
            selectableDates = object : androidx.compose.material3.SelectableDates {
                override fun isSelectableDate(utcTimeMillis: Long): Boolean =
                    !Instant.ofEpochMilli(utcTimeMillis).atZone(ZoneOffset.UTC).toLocalDate().isBefore(today)
            },
        )
        DatePickerDialog(
            onDismissRequest = { pickDate = false },
            confirmButton = {
                TextButton(onClick = {
                    state.selectedDateMillis?.let { date = Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate() }
                    pickDate = false
                }) { Text("OK") }
            },
            dismissButton = { TextButton(onClick = { pickDate = false }) { Text("Cancel") } },
        ) { DatePicker(state) }
    }

    if (pickStart) TimeDialog("First check", start, { start = it }, { pickStart = false })
    if (pickEnd) TimeDialog("Last check", end, { end = it }, { pickEnd = false })

    if (confirmReplace) {
        AlertDialog(
            onDismissRequest = { confirmReplace = false },
            title = { Text("Replace the current session?") },
            text = { Text("Only one session runs at a time. The current one will stop.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmReplace = false
                    CheckScheduler.cancel(ctx)
                    begin()
                }) { Text("Replace") }
            },
            dismissButton = { TextButton(onClick = { confirmReplace = false }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun PickerRow(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, value: String, onClick: () -> Unit) {
    NavRow(icon = icon, title = value, subtitle = label, onClick = onClick)
}

@Composable
private fun GapChip(label: String, selected: Boolean, onClick: () -> Unit) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(label) },
        leadingIcon = if (selected) {
            { Icon(Icons.Rounded.Check, null, Modifier.size(18.dp)) }
        } else null,
        colors = FilterChipDefaults.filterChipColors(
            selectedContainerColor = MaterialTheme.colorScheme.primary,
            selectedLabelColor = MaterialTheme.colorScheme.onPrimary,
            selectedLeadingIconColor = MaterialTheme.colorScheme.onPrimary,
        ),
        modifier = Modifier.heightIn(min = 40.dp),
    )
}

@Composable
private fun TimeDialog(title: String, initial: LocalTime, onPicked: (LocalTime) -> Unit, onClose: () -> Unit) {
    val state = rememberTimePickerState(initial.hour, initial.minute, is24Hour = false)
    AlertDialog(
        onDismissRequest = onClose,
        title = { Text(title) },
        text = { TimePicker(state) },
        confirmButton = {
            TextButton(onClick = {
                onPicked(LocalTime.of(state.hour, state.minute))
                onClose()
            }) { Text("OK") }
        },
        dismissButton = { TextButton(onClick = onClose) { Text("Cancel") } },
    )
}
