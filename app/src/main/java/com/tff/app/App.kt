package com.tff.app

import android.app.Application
import com.tff.app.di.AppContainer
import com.tff.app.notify.SystemNotifier

class App : Application() {
    override fun onCreate() {
        super.onCreate()
        AppContainer.init(this)
        SystemNotifier.start(this)
    }
}
