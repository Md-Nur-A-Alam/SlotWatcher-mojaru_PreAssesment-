package app.slotwatcher.check

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import app.slotwatcher.Notifications
import app.slotwatcher.data.Repo
import java.time.LocalDateTime
import java.time.ZoneId

/**
 * Wakes the app at each check time with an alarm-clock alarm: the most reliable kind, which
 * Doze and battery saver don't delay (NFR-1, NFR-2).
 */
object CheckScheduler {

    private const val REQUEST_CHECK = 100

    private fun checkIntent(ctx: Context) = PendingIntent.getBroadcast(
        ctx, REQUEST_CHECK,
        Intent(ctx, CheckAlarmReceiver::class.java),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    fun canScheduleExact(ctx: Context): Boolean =
        Build.VERSION.SDK_INT < 31 || ctx.getSystemService(AlarmManager::class.java).canScheduleExactAlarms()

    /** Schedules the first check after [after]; returns it, or null if the session is over. */
    fun scheduleNext(ctx: Context, s: WatchSession, after: LocalDateTime): LocalDateTime? {
        val next = CheckTimes.nextAfter(s, after)
        Repo.setNext(next)
        val am = ctx.getSystemService(AlarmManager::class.java)
        if (next == null) {
            am.cancel(checkIntent(ctx))
            return null
        }
        val millis = next.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        if (canScheduleExact(ctx)) {
            am.setAlarmClock(AlarmManager.AlarmClockInfo(millis, Notifications.openApp(ctx)), checkIntent(ctx))
        } else {
            // Without the exact-alarm permission the check may run a few minutes late.
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, millis, checkIntent(ctx))
        }
        return next
    }

    fun cancel(ctx: Context) {
        ctx.getSystemService(AlarmManager::class.java).cancel(checkIntent(ctx))
        Repo.setNext(null)
    }
}
