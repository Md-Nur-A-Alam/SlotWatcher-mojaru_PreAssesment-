package app.slotwatcher.check

import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.ServiceCompat
import app.slotwatcher.Notifications
import app.slotwatcher.alarm.AlarmInfo
import app.slotwatcher.alarm.AlarmRingService
import app.slotwatcher.data.Credentials
import app.slotwatcher.data.CredentialStore
import app.slotwatcher.data.Format
import app.slotwatcher.data.HistoryEntry
import app.slotwatcher.data.Repo
import app.slotwatcher.net.LayoutException
import app.slotwatcher.net.LoginException
import app.slotwatcher.net.MojaruClient
import app.slotwatcher.net.ParseResult
import app.slotwatcher.net.SlotParser
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.IOException
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.concurrent.atomic.AtomicBoolean

sealed interface CheckOutcome {
    data class Ok(val result: ParseResult) : CheckOutcome
    data class LoginFailed(val message: String) : CheckOutcome
    data class Unreadable(val message: String) : CheckOutcome
    data class NetworkFailed(val message: String) : CheckOutcome
}

/** One check, with retries for network trouble (FR-22). */
object CheckRunner {
    suspend fun run(creds: Credentials, date: LocalDate, attempts: Int = 4, retryDelayMs: Long = 30_000): CheckOutcome {
        var lastError = "unknown error"
        repeat(attempts) { i ->
            try {
                val r = withContext(Dispatchers.IO) {
                    MojaruClient().check(creds.login, creds.password, creds.listUrl, date)
                }
                return if (r.recognized) {
                    CheckOutcome.Ok(r)
                } else {
                    CheckOutcome.Unreadable("No slot table or Booked Slots counter on the page")
                }
            } catch (e: LoginException) {
                return CheckOutcome.LoginFailed(e.message ?: "Login failed")
            } catch (e: LayoutException) {
                return CheckOutcome.Unreadable(e.message ?: "Page not recognised")
            } catch (e: IOException) {
                lastError = e.message ?: e.javaClass.simpleName
                if (i < attempts - 1) delay(retryDelayMs)
            }
        }
        return CheckOutcome.NetworkFailed(lastError)
    }
}

/**
 * Runs one check in the foreground (so the system lets it finish with the screen off),
 * records it, rings if needed, schedules the next one, then stops.
 */
class CheckService : Service() {

    companion object {
        private const val EXTRA_MANUAL = "manual"
        private val running = AtomicBoolean(false)

        fun start(ctx: Context, manual: Boolean) {
            ctx.startForegroundService(Intent(ctx, CheckService::class.java).putExtra(EXTRA_MANUAL, manual))
        }
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        ServiceCompat.startForeground(
            this, Notifications.ID_WORK, Notifications.working(this),
            if (Build.VERSION.SDK_INT >= 29) ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC else 0,
        )
        if (!running.compareAndSet(false, true)) return START_NOT_STICKY // a check is already running

        scope.launch {
            try {
                runCheck()
            } finally {
                running.set(false)
                ServiceCompat.stopForeground(this@CheckService, ServiceCompat.STOP_FOREGROUND_REMOVE)
                stopSelf()
            }
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    private suspend fun runCheck() {
        val ctx = applicationContext
        val state = Repo.session.value
        val date = state?.session?.date ?: LocalDate.now()

        val creds = CredentialStore.get(ctx)
        if (creds == null) {
            Notifications.showProblem(ctx, "Add your Mojaru login in Slot Watcher.")
            return
        }

        val outcome = CheckRunner.run(creds, date)
        val at = LocalDateTime.now()
        val summary = handle(ctx, outcome, date, at)
        Repo.setLastSummary("${Format.time(at)} — $summary")

        val st = Repo.session.value ?: return
        if (!st.active) {
            CheckScheduler.cancel(ctx)
            return
        }
        val next = CheckScheduler.scheduleNext(ctx, st.session, at)
        if (next == null) {
            Repo.stopSession()
            Notifications.showStatus(ctx, "Session finished · last: $summary", ongoing = false)
        } else {
            Notifications.showStatus(
                ctx,
                "Watching ${Format.shortDay(st.session.date)} · next check ${Format.time(next)} · last: $summary",
                ongoing = true,
            )
        }
    }

    /** Records the outcome, raises the alarm or a notification, and returns a one-line summary. */
    private fun handle(ctx: Context, outcome: CheckOutcome, date: LocalDate, at: LocalDateTime): String {
        when (outcome) {
            is CheckOutcome.Ok -> {
                val r = outcome.result
                val booked = r.bookedSlots.sortedBy { it.start }
                val n = r.totalBooked
                val times = booked.joinToString(", ") { Format.time(it.start) }
                var summary = if (n == 0) "0 booked" else "$n booked" + (if (times.isNotEmpty()) ": $times" else "")
                val wanted = date.format(SlotParser.DMY_FORMAT)
                if (r.shownDate != null && r.shownDate != wanted) summary += " (page showed ${r.shownDate})"

                Repo.addHistory(HistoryEntry(at, true, n, booked.map { it.label }, summary))
                if (n > 0) {
                    AlarmRingService.ring(
                        ctx,
                        AlarmInfo(
                            title = Format.slotsBooked(n),
                            lines = booked.map { "${Format.time(it.start)} – ${Format.time(it.end)}" },
                            speech = Format.spokenMessage(n, booked.map { it.start }),
                            dateText = Format.day(date),
                        ),
                    )
                }
                return summary
            }

            is CheckOutcome.LoginFailed -> {
                Repo.addHistory(HistoryEntry(at, false, 0, emptyList(), "Login failed: ${outcome.message}"))
                // FR-23: a distinct alarm, and the session pauses until the login is fixed.
                Repo.stopSession()
                CheckScheduler.cancel(ctx)
                AlarmRingService.ring(
                    ctx,
                    AlarmInfo(
                        title = "Login failed",
                        lines = listOf(outcome.message, "Watching is paused. Open Slot Watcher to fix the login."),
                        speech = "Login failed. Slot Watcher is paused.",
                        dateText = null,
                    ),
                )
                return "login failed"
            }

            is CheckOutcome.Unreadable -> {
                Repo.addHistory(HistoryEntry(at, false, 0, emptyList(), "Couldn't read slots: ${outcome.message}"))
                Notifications.showProblem(
                    ctx, "Couldn't read slots at ${Format.time(at)} (${outcome.message}). Please check the panel by hand.",
                )
                return "couldn't read slots"
            }

            is CheckOutcome.NetworkFailed -> {
                Repo.addHistory(HistoryEntry(at, false, 0, emptyList(), "Check failed: ${outcome.message}"))
                Notifications.showProblem(ctx, "Check at ${Format.time(at)} failed: ${outcome.message}")
                return "check failed"
            }
        }
    }
}
