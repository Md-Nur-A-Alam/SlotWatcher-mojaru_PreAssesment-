package app.slotwatcher

import android.app.Application
import app.slotwatcher.data.Repo

class SlotWatcherApp : Application() {
    override fun onCreate() {
        super.onCreate()
        Notifications.createChannels(this)
        Repo.init(this)
    }
}
