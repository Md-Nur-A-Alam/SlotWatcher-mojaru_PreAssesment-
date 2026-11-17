package app.slotwatcher.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.slotwatcher.data.Format
import app.slotwatcher.data.Repo

/** FR-25: past checks, newest first. */
@Composable
fun HistoryScreen(onBack: () -> Unit) {
    val history by Repo.history.collectAsState()
    Page(
        "History",
        onBack = onBack,
        scroll = false,
        actions = { if (history.isNotEmpty()) TextButton(onClick = { Repo.clearHistory() }) { Text("Clear") } },
    ) {
        if (history.isEmpty()) {
            Text("No checks yet.", modifier = Modifier.padding(top = 16.dp))
            return@Page
        }
        LazyColumn(Modifier.fillMaxSize()) {
            items(history) { e ->
                Column(Modifier.padding(vertical = 10.dp)) {
                    Text(Format.stamp(e.at), fontWeight = FontWeight.SemiBold)
                    Text(
                        e.message,
                        color = when {
                            !e.ok -> MaterialTheme.colorScheme.error
                            e.booked > 0 -> MaterialTheme.colorScheme.primary
                            else -> MaterialTheme.colorScheme.onSurface
                        },
                    )
                }
                HorizontalDivider()
            }
        }
    }
}
