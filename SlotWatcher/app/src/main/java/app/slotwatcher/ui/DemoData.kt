package app.slotwatcher.ui

import app.slotwatcher.BuildConfig
import app.slotwatcher.check.CheckTimes
import app.slotwatcher.check.WatchSession
import app.slotwatcher.data.HistoryEntry
import app.slotwatcher.data.Repo
import java.time.LocalDate
import java.time.LocalDateTime

/**
 * Debug builds only: `adb shell am start -n app.slotwatcher/.ui.MainActivity --ez demo true`
 * fills the screens with sample data (no login, no alarms scheduled) for UI review.
 */
object DemoData {
    fun seed() {
        if (!BuildConfig.DEBUG) return
        val now = LocalDateTime.now()
        val start = now.toLocalTime().minusMinutes(42).withSecond(0).withNano(0)
        val session = WatchSession(LocalDate.now(), start, start.plusMinutes(170), 10)
        Repo.startSession(session)
        Repo.setNext(CheckTimes.nextAfter(session, now))
        Repo.setLastSummary("${app.slotwatcher.data.Format.time(now.minusMinutes(2))} — 2 booked: 6:30 PM, 7:10 PM")
        Repo.clearHistory()
        val t = { m: Long -> now.minusMinutes(m).withSecond(0).withNano(0) }
        listOf(
            HistoryEntry(t(42), true, 0, emptyList(), "0 booked"),
            HistoryEntry(t(32), true, 0, emptyList(), "0 booked"),
            HistoryEntry(t(22), false, 0, emptyList(), "Check failed: timeout"),
            HistoryEntry(t(12), true, 1, listOf("18:30 - 18:40"), "1 booked: 6:30 PM"),
            HistoryEntry(t(2), true, 2, listOf("18:30 - 18:40", "19:10 - 19:20"), "2 booked: 6:30 PM, 7:10 PM"),
        ).forEach(Repo::addHistory)
    }
}
