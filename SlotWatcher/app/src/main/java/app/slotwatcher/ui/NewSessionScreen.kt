@file:OptIn(ExperimentalMaterial3Api::class)

package app.slotwatcher.ui

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SelectableDates
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import app.slotwatcher.Notifications
import app.slotwatcher.check.CheckScheduler
import app.slotwatcher.check.CheckTimes
import app.slotwatcher.check.WatchSession
import app.slotwatcher.data.Format
import app.slotwatcher.data.Repo
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneOffset

/** FR-4 to FR-10: date, start, end, gap, then Start. */
@Composable
fun NewSessionScreen(onDone: () -> Unit, onBack: () -> Unit) {
    val ctx = LocalContext.current
    val previous = remember { Repo.session.value?.session }
    var date by remember { mutableStateOf(LocalDate.now()) }
    var start by remember { mutableStateOf(previous?.start ?: LocalTime.of(18, 25)) }
    var end by remember { mutableStateOf(previous?.end ?: LocalTime.of(21, 15)) }
    var gapText by remember { mutableStateOf((previous?.gapMinutes ?: 10).toString()) }

    var pickDate by remember { mutableStateOf(false) }
    var pickStart by remember { mutableStateOf(false) }
    var pickEnd by remember { mutableStateOf(false) }
    var confirmReplace by remember { mutableStateOf(false) }

    val session = WatchSession(date, start, end, gapText.toIntOrNull() ?: 0)
    val error = CheckTimes.validate(session, LocalDateTime.now())
    val times = CheckTimes.of(session)

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

    Page("New session", onBack = onBack) {
        Text("Date", fontWeight = FontWeight.Medium)
        OutlinedButton(onClick = { pickDate = true }, modifier = Modifier.fillMaxWidth()) { Text(Format.day(date)) }
        Spacer(Modifier.height(12.dp))

        Text("Start time", fontWeight = FontWeight.Medium)
        OutlinedButton(onClick = { pickStart = true }, modifier = Modifier.fillMaxWidth()) { Text(Format.time(start)) }
        Spacer(Modifier.height(12.dp))

        Text("End time", fontWeight = FontWeight.Medium)
        OutlinedButton(onClick = { pickEnd = true }, modifier = Modifier.fillMaxWidth()) { Text(Format.time(end)) }
        Spacer(Modifier.height(12.dp))

        OutlinedTextField(
            value = gapText,
            onValueChange = { v -> gapText = v.filter { it.isDigit() }.take(3) },
            label = { Text("Gap (minutes)") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(16.dp))

        if (times.isNotEmpty()) {
            Text("${times.size} checks", fontWeight = FontWeight.SemiBold)
            Text(times.joinToString(", ") { Format.time(it) }, style = MaterialTheme.typography.bodyMedium)
            Spacer(Modifier.height(12.dp))
        }
        if (error != null) {
            Text(error, color = MaterialTheme.colorScheme.error)
            Spacer(Modifier.height(12.dp))
        }

        Button(
            enabled = error == null,
            onClick = { if (Repo.session.value?.active == true) confirmReplace = true else begin() },
            modifier = Modifier.fillMaxWidth(),
        ) { Text("Start") }
        Spacer(Modifier.height(16.dp))
    }

    if (pickDate) {
        val today = LocalDate.now()
        val state = rememberDatePickerState(
            initialSelectedDateMillis = date.atStartOfDay().toInstant(ZoneOffset.UTC).toEpochMilli(),
            selectableDates = object : SelectableDates {
                override fun isSelectableDate(utcTimeMillis: Long): Boolean =
                    !Instant.ofEpochMilli(utcTimeMillis).atZone(ZoneOffset.UTC).toLocalDate().isBefore(today)
            },
        )
        DatePickerDialog(
            onDismissRequest = { pickDate = false },
            confirmButton = {
                TextButton(onClick = {
                    state.selectedDateMillis?.let {
                        date = Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate()
                    }
                    pickDate = false
                }) { Text("OK") }
            },
            dismissButton = { TextButton(onClick = { pickDate = false }) { Text("Cancel") } },
        ) { DatePicker(state) }
    }

    if (pickStart) TimeDialog("Start time", start, { start = it }, { pickStart = false })
    if (pickEnd) TimeDialog("End time", end, { end = it }, { pickEnd = false })

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
