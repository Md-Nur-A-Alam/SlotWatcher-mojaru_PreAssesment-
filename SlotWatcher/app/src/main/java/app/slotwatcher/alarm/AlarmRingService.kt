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
import app.slotwatcher.data.SettingsStore
import app.slotwatcher.data.VoiceLanguage
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
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import java.util.Locale

enum class AlarmKind { Booked, NoneBooked, Problem, LoginFailed, Test }

data class AlarmInfo(
    val kind: AlarmKind,
    val title: String,       // "2 slots booked"
    val lines: List<String>, // "6:30 PM – 6:40 PM", ...
    val speech: String,      // "2 slots booked: 6:30 PM and 7:10 PM."
    val dateText: String?,
    val lang: VoiceLanguage,
    /** true: ring until Dismiss. false: tone + voice once, then stop by itself. */
    val persistent: Boolean = kind != AlarmKind.NoneBooked && kind != AlarmKind.Problem,
    val id: Long = System.nanoTime(),
)

/**
 * The alert played at a check. Booked slots (and a failed login) ring until dismissed: a few
 * seconds of alarm tone, then the spoken message, over and over (FR-17 to FR-19). A check with
 * nothing booked gets one short tone and one spoken line. Everything uses the alarm audio stream
 * so it sounds on silent (NFR-4).
 */
class AlarmRingService : Service() {

    companion object {
        private val _current = MutableStateFlow<AlarmInfo?>(null)
        val current: StateFlow<AlarmInfo?> = _current.asStateFlow()

        fun ring(ctx: Context, info: AlarmInfo) {
            // A booked alarm already ringing is never replaced by a "nothing booked" notice.
            val now = _current.value
            if (now != null && now.persistent && !info.persistent) return
            _current.value = info
            ctx.startForegroundService(Intent(ctx, AlarmRingService::class.java))
        }

        /** Dismissing stops this alarm only; the session's next check still runs (FR-20). */
        fun dismiss() {
            _current.value = null
        }

        private const val TONE_MS = 4_000L
        private const val SHORT_TONE_MS = 1_800L
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var loop: Job? = null
    private var playing: AlarmInfo? = null
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
        if (playing?.id != info.id) {
            loop?.cancel()
            playing = info
            loop = scope.launch { play(info) }
        }
        return START_NOT_STICKY
    }

    private suspend fun play(info: AlarmInfo) {
        if (SettingsStore.settings.value.vibrate) startVibration(info.persistent)
        if (!info.persistent) {
            playTone(SHORT_TONE_MS)
            speak(info)
            delay(400)
            if (current.value?.id == info.id) dismiss()
            return
        }
        while (current.value?.id == info.id) {
            playTone(TONE_MS)
            if (current.value?.id != info.id) break
            speak(info)
            delay(700)
        }
    }

    private suspend fun playTone(ms: Long) {
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
            delay(ms)
            return
        }
        p.start()
        delay(ms)
        if (p.isPlaying) p.pause()
        p.seekTo(0)
    }

    private suspend fun speak(info: AlarmInfo) {
        val ok = withTimeoutOrNull(3_000) { ttsReady.await() } ?: false
        val engine = tts
        if (!ok || engine == null) return
        val locale = if (info.lang == VoiceLanguage.Bangla) Locale("bn", "BD") else Locale.US
        val res = engine.setLanguage(locale)
        if (res == TextToSpeech.LANG_MISSING_DATA || res == TextToSpeech.LANG_NOT_SUPPORTED) {
            engine.language = Locale.US // Bangla voice not installed: fall back to English.
        }
        val done = CompletableDeferred<Unit>().also { spoken = it }
        engine.speak(info.speech, TextToSpeech.QUEUE_FLUSH, Bundle(), "alert-${info.id}")
        withTimeoutOrNull(20_000) { done.await() }
    }

    private fun vibrator(): Vibrator =
        if (Build.VERSION.SDK_INT >= 31) {
            getSystemService(VibratorManager::class.java).defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            getSystemService(Vibrator::class.java)
        }

    @Suppress("DEPRECATION")
    private fun startVibration(repeat: Boolean) {
        runCatching {
            val effect = if (repeat) {
                VibrationEffect.createWaveform(longArrayOf(0, 800, 600), 0)
            } else {
                VibrationEffect.createWaveform(longArrayOf(0, 250, 150, 250), -1)
            }
            vibrator().vibrate(effect, alarmAudio)
        }
    }

    private fun stopRinging() {
        loop?.cancel()
        loop = null
        playing = null
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
            .setColor(0xFF532D80.toInt())
            .setContentTitle(info?.title ?: "Slot Watcher")
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setOngoing(true)
            .apply { if (info?.persistent != false) setFullScreenIntent(full, true) }
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
