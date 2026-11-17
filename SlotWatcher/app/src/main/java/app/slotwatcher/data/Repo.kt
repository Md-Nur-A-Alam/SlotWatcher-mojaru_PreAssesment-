package app.slotwatcher.data

import android.content.Context
import android.content.SharedPreferences
import app.slotwatcher.check.WatchSession
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

data class SessionState(
    val session: WatchSession,
    val active: Boolean,
    val nextCheck: LocalDateTime?,
    val lastSummary: String?,
)

data class HistoryEntry(
    val at: LocalDateTime,
    val ok: Boolean,
    val booked: Int,
    val slots: List<String>,
    val message: String,
)

/**
 * The saved session (SharedPreferences) and check history (a JSON file), exposed as flows so
 * the screens update when a background check finishes. Everything runs in one process.
 */
object Repo {

    private const val MAX_HISTORY = 300

    private lateinit var prefs: SharedPreferences
    private lateinit var historyFile: File

    private val _session = MutableStateFlow<SessionState?>(null)
    val session: StateFlow<SessionState?> = _session.asStateFlow()

    private val _history = MutableStateFlow<List<HistoryEntry>>(emptyList())
    val history: StateFlow<List<HistoryEntry>> = _history.asStateFlow()

    fun init(ctx: Context) {
        prefs = ctx.getSharedPreferences("session", Context.MODE_PRIVATE)
        historyFile = File(ctx.filesDir, "history.json")
        _session.value = loadSession()
        _history.value = loadHistory()
    }

    // ---- session ----

    @Synchronized
    fun startSession(s: WatchSession) {
        prefs.edit().clear()
            .putString("date", s.date.toString())
            .putString("start", s.start.toString())
            .putString("end", s.end.toString())
            .putInt("gap", s.gapMinutes)
            .putBoolean("active", true)
            .apply()
        _session.value = loadSession()
    }

    @Synchronized
    fun stopSession() = edit { putBoolean("active", false).remove("next") }

    @Synchronized
    fun setNext(next: LocalDateTime?) = edit {
        if (next == null) remove("next") else putString("next", next.toString())
    }

    @Synchronized
    fun setLastSummary(summary: String) = edit { putString("last", summary) }

    private inline fun edit(block: SharedPreferences.Editor.() -> SharedPreferences.Editor) {
        prefs.edit().block().apply()
        _session.value = loadSession()
    }

    private fun loadSession(): SessionState? = runCatching {
        val date = prefs.getString("date", null) ?: return null
        SessionState(
            session = WatchSession(
                date = LocalDate.parse(date),
                start = LocalTime.parse(prefs.getString("start", null)),
                end = LocalTime.parse(prefs.getString("end", null)),
                gapMinutes = prefs.getInt("gap", 10),
            ),
            active = prefs.getBoolean("active", false),
            nextCheck = prefs.getString("next", null)?.let(LocalDateTime::parse),
            lastSummary = prefs.getString("last", null),
        )
    }.getOrNull()

    // ---- history ----

    @Synchronized
    fun addHistory(e: HistoryEntry) {
        val list = (listOf(e) + _history.value).take(MAX_HISTORY)
        _history.value = list
        saveHistory(list)
    }

    @Synchronized
    fun clearHistory() {
        _history.value = emptyList()
        historyFile.delete()
    }

    private fun saveHistory(list: List<HistoryEntry>) {
        val arr = JSONArray()
        list.forEach { e ->
            arr.put(
                JSONObject()
                    .put("at", e.at.toString())
                    .put("ok", e.ok)
                    .put("booked", e.booked)
                    .put("slots", JSONArray(e.slots))
                    .put("message", e.message),
            )
        }
        historyFile.writeText(arr.toString())
    }

    private fun loadHistory(): List<HistoryEntry> = runCatching {
        if (!historyFile.exists()) return emptyList()
        val arr = JSONArray(historyFile.readText())
        (0 until arr.length()).map { i ->
            val o = arr.getJSONObject(i)
            val slots = o.getJSONArray("slots")
            HistoryEntry(
                at = LocalDateTime.parse(o.getString("at")),
                ok = o.getBoolean("ok"),
                booked = o.getInt("booked"),
                slots = (0 until slots.length()).map { slots.getString(it) },
                message = o.getString("message"),
            )
        }
    }.getOrDefault(emptyList())
}
