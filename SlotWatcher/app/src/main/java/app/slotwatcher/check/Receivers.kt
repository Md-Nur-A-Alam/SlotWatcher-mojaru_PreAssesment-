package app.slotwatcher.check

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import app.slotwatcher.Notifications
import app.slotwatcher.data.Format
import app.slotwatcher.data.Repo
import java.time.LocalDateTime

/** Fired by the alarm at each check time. */
class CheckAlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        CheckService.start(context, manual = false)
    }
}

/** Puts a running session back on schedule after a restart or app update (NFR-3). */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val state = Repo.session.value ?: return
        if (!state.active) return
        val next = CheckScheduler.scheduleNext(context, state.session, LocalDateTime.now())
        if (next == null) {
            Repo.stopSession()
            Notifications.cancelStatus(context)
        } else {
            Notifications.showStatus(
                context,
                "Watching ${Format.shortDay(state.session.date)} · next check ${Format.time(next)}",
                ongoing = true,
            )
        }
    }
}
