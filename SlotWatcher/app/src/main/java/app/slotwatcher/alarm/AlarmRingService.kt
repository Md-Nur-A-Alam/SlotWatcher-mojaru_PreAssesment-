package app.slotwatcher.alarm

import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.os.PowerManager
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import app.slotwatcher.Notifications
import app.slotwatcher.R
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import java.util.Locale

data class AlarmInfo(
    val title: String,       // "2 slots booked"
    val lines: List<String>, // "6:30 PM – 6:40 PM", ...
    val speech: String,      // "2 slots booked: 6:30 PM and 7:10 PM."
    val dateText: String?,
)

/**
 * Rings until dismissed (FR-17 to FR-19): a few seconds of the alarm tone, then the spoken
 * message, over and over. Uses the alarm audio stream so it sounds on silent (NFR-4).
 */
class AlarmRingService : Service() {

    companion object {
        private val _current = MutableStateFlow<AlarmInfo?>(null)
        val current: StateFlow<AlarmInfo?> = _current.asStateFlow()

        fun ring(ctx: Context, info: AlarmInfo) {
            _current.value = info
            ctx.startForegroundService(Intent(ctx, AlarmRingService::class.java))
        }

        /** Dismissing stops this alarm only; the session's next check still runs (FR-20). */
        fun dismiss() {
            _current.value = null
        }

        private const val TONE_MS = 4_000L
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var loop: Job? = null
    private var player: MediaPlayer? = null
    private var tts: TextToSpeech? = null
    private val ttsReady = CompletableDeferred<Boolean>()
    private var spoken: CompletableDeferred<Unit>? = null
    private var wakeLock: PowerManager.WakeLock? = null

    private val alarmAudio = AudioAttributes.Builder()
        .setUsage(AudioAttributes.USAGE_ALARM)
        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
        .build()

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        tts = TextToSpeech(this) { status ->
            val ok = status == TextToSpeech.SUCCESS
            if (ok) {
                tts?.language = Locale.US
                tts?.setAudioAttributes(alarmAudio)
                tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                    override fun onStart(utteranceId: String?) {}
                    override fun onDone(utteranceId: String?) { spoken?.complete(Unit) }
                    @Deprecated("Deprecated in Java")
                    override fun onError(utteranceId: String?) { spoken?.complete(Unit) }
                })
            }
            ttsReady.complete(ok)
        }
        scope.launch {
            current.collect { if (it == null) stopRinging() }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val info = current.value
        ServiceCompat.startForeground(
            this, Notifications.ID_ALARM, buildNotification(info),
            if (Build.VERSION.SDK_INT >= 29) ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK else 0,
        )
        if (info == null) {
            stopRinging()
            return START_NOT_STICKY
        }
        if (wakeLock == null) {
            wakeLock = getSystemService(PowerManager::class.java)
                .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "SlotWatcher:alarm")
                .apply { acquire(60 * 60 * 1000L) }
        }
        if (loop?.isActive != true) loop = scope.launch { ringLoop() }
        return START_NOT_STICKY
    }

    private suspend fun ringLoop() {
        startVibration()
        while (scope.isActive) {
            if (current.value == null) break
            playTone()
            val info = current.value ?: break
            speak(info.speech)
            delay(700)
        }
    }

    private suspend fun playTone() {
        val p = player ?: runCatching {
            val uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
                ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)
            MediaPlayer().apply {
                setAudioAttributes(alarmAudio)
                setDataSource(this@AlarmRingService, uri)
                isLooping = true
                prepare()
            }
        }.getOrNull().also { player = it }
        if (p == null) {
            delay(TONE_MS)
            return
        }
        p.start()
        delay(TONE_MS)
        if (p.isPlaying) p.pause()
        p.seekTo(0)
    }

    private suspend fun speak(text: String) {
        val ok = withTimeoutOrNull(3_000) { ttsReady.await() } ?: false
        val engine = tts
        if (!ok || engine == null) return
        val done = CompletableDeferred<Unit>().also { spoken = it }
        engine.speak(text, TextToSpeech.QUEUE_FLUSH, Bundle(), "booked")
        withTimeoutOrNull(20_000) { done.await() }
    }

    private fun vibrator(): Vibrator =
        if (Build.VERSION.SDK_INT >= 31) {
            getSystemService(VibratorManager::class.java).defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            getSystemService(Vibrator::class.java)
        }

    private fun startVibration() {
        runCatching {
            vibrator().vibrate(VibrationEffect.createWaveform(longArrayOf(0, 800, 600), 0), alarmAudio)
        }
    }

    private fun stopRinging() {
        loop?.cancel()
        loop = null
        runCatching { player?.stop() }
        player?.release()
        player = null
        tts?.stop()
        runCatching { vibrator().cancel() }
        wakeLock?.let { if (it.isHeld) it.release() }
        wakeLock = null
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        scope.cancel()
        player?.release()
        tts?.shutdown()
        runCatching { vibrator().cancel() }
        wakeLock?.let { if (it.isHeld) it.release() }
        super.onDestroy()
    }

    private fun buildNotification(info: AlarmInfo?): Notification {
        val full = PendingIntent.getActivity(
            this, 1,
            Intent(this, AlarmActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val dismiss = PendingIntent.getBroadcast(
            this, 2,
            Intent(this, DismissReceiver::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val body = info?.lines?.joinToString("\n").orEmpty()
        return NotificationCompat.Builder(this, Notifications.CH_ALARM)
            .setSmallIcon(R.drawable.ic_stat_bell)
            .setContentTitle(info?.title ?: "Slot Watcher")
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setOngoing(true)
            .setFullScreenIntent(full, true)
            .setContentIntent(full)
            .addAction(0, "Dismiss", dismiss)
            .build()
    }
}

/** The notification's Dismiss button. */
class DismissReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        AlarmRingService.dismiss()
    }
}
