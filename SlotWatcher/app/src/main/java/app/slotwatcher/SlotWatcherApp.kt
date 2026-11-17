package app.slotwatcher

import android.app.Application
import app.slotwatcher.data.Repo
import app.slotwatcher.data.SettingsStore

class SlotWatcherApp : Application() {
    override fun onCreate() {
        super.onCreate()
        Notifications.createChannels(this)
        SettingsStore.init(this)
        Repo.init(this)
    }
}
