package app.slotwatcher.ui

import android.Manifest
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import app.slotwatcher.Notifications
import app.slotwatcher.check.CheckScheduler

/** A system setting the app needs to work reliably, whether it's on, and how to fix it. */
data class Requirement(
    val title: String,
    val why: String,
    val ok: Boolean,
    /** Either a runtime permission to request, or a settings screen to open. */
    val permission: String? = null,
    val settingsIntent: Intent? = null,
)

object Permissions {

    fun all(ctx: Context): List<Requirement> {
        val out = mutableListOf<Requirement>()
        val pkg = Uri.parse("package:${ctx.packageName}")

        if (Build.VERSION.SDK_INT >= 33) {
            out += Requirement(
                "Notifications",
                "Needed for the alarm and the status line.",
                ok = Notifications.canPost(ctx),
                permission = Manifest.permission.POST_NOTIFICATIONS,
            )
        }
        if (Build.VERSION.SDK_INT >= 31) {
            out += Requirement(
                "Alarms & reminders",
                "Runs each check exactly on time.",
                ok = CheckScheduler.canScheduleExact(ctx),
                settingsIntent = Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, pkg),
            )
        }
        out += Requirement(
            "Unrestricted battery",
            "Stops Android pausing checks while the screen is off.",
            ok = ctx.getSystemService(PowerManager::class.java).isIgnoringBatteryOptimizations(ctx.packageName),
            settingsIntent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, pkg),
        )
        if (Build.VERSION.SDK_INT >= 34) {
            out += Requirement(
                "Full-screen alarm",
                "Shows the alarm over the lock screen.",
                ok = ctx.getSystemService(NotificationManager::class.java).canUseFullScreenIntent(),
                settingsIntent = Intent(Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT, pkg),
            )
        }
        return out
    }

    fun missing(ctx: Context): List<Requirement> = all(ctx).filterNot { it.ok }
}
