package app.slotwatcher

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import app.slotwatcher.ui.MainActivity

object Notifications {
    const val CH_STATUS = "status"
    const val CH_WORK = "work"
    const val CH_ALARM = "alarm"
    const val CH_PROBLEM = "problem"

    const val ID_STATUS = 1
    const val ID_WORK = 2
    const val ID_ALARM = 3
    const val ID_PROBLEM = 4

    fun createChannels(ctx: Context) {
        val nm = ctx.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannels(
            listOf(
                NotificationChannel(CH_STATUS, "Watch status", NotificationManager.IMPORTANCE_LOW),
                NotificationChannel(CH_WORK, "Check in progress", NotificationManager.IMPORTANCE_MIN),
                NotificationChannel(CH_ALARM, "Booking alarm", NotificationManager.IMPORTANCE_HIGH).apply {
                    // The alarm service plays the sound and voice itself, on the alarm stream.
                    setSound(null, null)
                    enableVibration(false)
                    lockscreenVisibility = Notification.VISIBILITY_PUBLIC
                    setBypassDnd(true)
                },
                NotificationChannel(CH_PROBLEM, "Problems", NotificationManager.IMPORTANCE_DEFAULT),
            ),
        )
    }

    fun openApp(ctx: Context): PendingIntent = PendingIntent.getActivity(
        ctx, 0,
        Intent(ctx, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    fun canPost(ctx: Context): Boolean =
        Build.VERSION.SDK_INT < 33 ||
            ctx.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    fun working(ctx: Context): Notification =
        NotificationCompat.Builder(ctx, CH_WORK)
            .setSmallIcon(R.drawable.ic_stat_bell)
            .setContentTitle("Checking Mojaru slots…")
            .setContentIntent(openApp(ctx))
            .setOngoing(true)
            .build()

    /** "Watching 18 Nov · next check 6:45 PM · last: 0 booked" (ongoing while a session runs). */
    fun showStatus(ctx: Context, text: String, ongoing: Boolean) {
        if (!canPost(ctx)) return
        val n = NotificationCompat.Builder(ctx, CH_STATUS)
            .setSmallIcon(R.drawable.ic_stat_bell)
            .setContentTitle("Slot Watcher")
            .setContentText(text)
            .setContentIntent(openApp(ctx))
            .setOngoing(ongoing)
            .setOnlyAlertOnce(true)
            .build()
        ctx.getSystemService(NotificationManager::class.java).notify(ID_STATUS, n)
    }

    fun cancelStatus(ctx: Context) {
        ctx.getSystemService(NotificationManager::class.java).cancel(ID_STATUS)
    }

    fun showProblem(ctx: Context, text: String) {
        if (!canPost(ctx)) return
        val n = NotificationCompat.Builder(ctx, CH_PROBLEM)
            .setSmallIcon(R.drawable.ic_stat_bell)
            .setContentTitle("Slot Watcher problem")
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setContentIntent(openApp(ctx))
            .setAutoCancel(true)
            .build()
        ctx.getSystemService(NotificationManager::class.java).notify(ID_PROBLEM, n)
    }
}
