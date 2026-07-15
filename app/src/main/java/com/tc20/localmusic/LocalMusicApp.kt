package com.tc20.localmusic

import android.app.Application
import com.tc20.localmusic.data.AppContainer

class LocalMusicApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}
