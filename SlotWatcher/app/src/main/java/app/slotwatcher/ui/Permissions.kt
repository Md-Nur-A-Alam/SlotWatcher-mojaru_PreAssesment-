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

/** A setting the app needs to work reliably, and how to fix it. */
data class Requirement(
    val title: String,
    val why: String,
    /** Either a runtime permission to request, or a settings screen to open. */
    val permission: String? = null,
    val settingsIntent: Intent? = null,
)

object Permissions {

    fun missing(ctx: Context): List<Requirement> {
        val out = mutableListOf<Requirement>()
        val pkg = Uri.parse("package:${ctx.packageName}")

        if (!Notifications.canPost(ctx)) {
            out += Requirement(
                "Allow notifications",
                "Needed for the alarm and the status line.",
                permission = Manifest.permission.POST_NOTIFICATIONS,
            )
        }
        if (!CheckScheduler.canScheduleExact(ctx) && Build.VERSION.SDK_INT >= 31) {
            out += Requirement(
                "Allow alarms & reminders",
                "Without it, checks can run several minutes late.",
                settingsIntent = Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, pkg),
            )
        }
        val pm = ctx.getSystemService(PowerManager::class.java)
        if (!pm.isIgnoringBatteryOptimizations(ctx.packageName)) {
            out += Requirement(
                "Turn off battery optimisation",
                "Stops Android from pausing checks while the screen is off.",
                settingsIntent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, pkg),
            )
        }
        if (Build.VERSION.SDK_INT >= 34 &&
            !ctx.getSystemService(NotificationManager::class.java).canUseFullScreenIntent()
        ) {
            out += Requirement(
                "Allow full-screen alarm",
                "Lets the alarm appear over the lock screen.",
                settingsIntent = Intent(Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT, pkg),
            )
        }
        return out
    }
}
