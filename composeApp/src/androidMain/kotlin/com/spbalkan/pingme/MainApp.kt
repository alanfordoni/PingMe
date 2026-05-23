package com.spbalkan.pingme

import android.app.Application
import com.spbalkan.pingme.di.commonModule
import com.spbalkan.pingme.di.platformModule
import org.koin.android.ext.koin.androidContext
import org.koin.android.ext.koin.androidLogger
import org.koin.core.context.GlobalContext

class MainApp : Application() {
    override fun onCreate() {
        super.onCreate()

        GlobalContext.startKoin {
            androidLogger()
            androidContext(this@MainApp)
            modules(commonModule, platformModule)
        }
    }
}