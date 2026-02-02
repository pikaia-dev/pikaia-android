package dev.pikaia.android

import android.app.Application
import android.content.pm.ApplicationInfo
import dev.pikaia.android.activity.di.activityModule
import dev.pikaia.android.di.appModule
import dev.pikaia.android.feature.di.featureModules
import org.koin.android.ext.koin.androidContext
import org.koin.core.context.GlobalContext.startKoin
import timber.log.Timber

class PikaiaApp : Application() {

    override fun onCreate() {
        super.onCreate()

        initializeDI()

        if (isDebug()) {
            Timber.plant(Timber.DebugTree())
        }
    }

    private fun isDebug() = (applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0

    private fun initializeDI() {
        startKoin {
            androidContext(this@PikaiaApp)

            modules(appModule)
            modules(activityModule)

            modules(featureModules)
        }
    }
}