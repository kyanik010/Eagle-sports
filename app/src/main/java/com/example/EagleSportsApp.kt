package com.example

import android.app.Application
import com.example.data.local.AppDatabase
import com.example.data.preferences.PreferencesManager
import com.example.data.repository.ChannelRepository

class EagleSportsApp : Application() {

    lateinit var database: AppDatabase
        private set

    lateinit var preferencesManager: PreferencesManager
        private set

    lateinit var channelRepository: ChannelRepository
        private set

    override fun onCreate() {
        super.onCreate()
        instance = this
        database = AppDatabase.getDatabase(this)
        preferencesManager = PreferencesManager(this)
        channelRepository = ChannelRepository(
            channelDao = database.channelDao(),
            audioDao = database.externalAudioDao(),
            preferencesManager = preferencesManager
        )
    }

    companion object {
        lateinit var instance: EagleSportsApp
            private set
    }
}
