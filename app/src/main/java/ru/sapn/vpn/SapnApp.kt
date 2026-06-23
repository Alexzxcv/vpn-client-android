package ru.sapn.vpn

import android.app.Application
import ru.sapn.vpn.di.AppContainer

class SapnApp : Application() {

    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}
