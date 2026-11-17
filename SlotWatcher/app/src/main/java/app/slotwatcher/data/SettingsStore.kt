package app.slotwatcher.data

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class VoiceLanguage { English, Bangla }

data class Settings(
    /** Announce every check, including "no slot booked" (short alert that stops by itself). */
    val alertEveryCheck: Boolean = true,
    val voice: VoiceLanguage = VoiceLanguage.English,
    val vibrate: Boolean = true,
)

object SettingsStore {
    private lateinit var prefs: SharedPreferences
    private val _settings = MutableStateFlow(Settings())
    val settings: StateFlow<Settings> = _settings.asStateFlow()

    fun init(ctx: Context) {
        prefs = ctx.getSharedPreferences("settings", Context.MODE_PRIVATE)
        _settings.value = Settings(
            alertEveryCheck = prefs.getBoolean("alertEveryCheck", true),
            voice = runCatching { VoiceLanguage.valueOf(prefs.getString("voice", null)!!) }
                .getOrDefault(VoiceLanguage.English),
            vibrate = prefs.getBoolean("vibrate", true),
        )
    }

    fun update(transform: (Settings) -> Settings) {
        val s = transform(_settings.value)
        _settings.value = s
        prefs.edit()
            .putBoolean("alertEveryCheck", s.alertEveryCheck)
            .putString("voice", s.voice.name)
            .putBoolean("vibrate", s.vibrate)
            .apply()
    }
}
