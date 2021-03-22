package com.antrov.timesrapse

import android.app.Application
import com.antrov.timesrapse.modules.AlarmHelper
import com.antrov.timesrapse.modules.StorageManager
import com.antrov.timesrapse.modules.StorageManagerImpl
import com.antrov.timesrapse.service.ForegroundService
import com.antrov.timesrapse.utils.xlog.Crashlytics
import com.antrov.timesrapse.utils.xlog.Promtail
import com.antrov.timesrapse.utils.xlog.PromtailPrinter
import com.elvishew.xlog.LogLevel
import com.elvishew.xlog.XLog
import com.elvishew.xlog.printer.AndroidPrinter
import org.koin.android.ext.koin.androidContext
import org.koin.android.ext.koin.androidLogger
import org.koin.core.component.KoinApiExtension
import org.koin.core.context.startKoin
import org.koin.dsl.module

class TimesrapseApp : Application() {

    @KoinApiExtension
    override fun onCreate() {
        super.onCreate()

        val promtailPrinter = PromtailPrinter(PromtailPrinter.defaultLogLevel, cacheDir)

        val appModule = module {
            single<StorageManager> { StorageManagerImpl() }
            single<Promtail> { promtailPrinter }
            factory { AlarmHelper(get()) }
        }

        startKoin {
            androidLogger()
            androidContext(this@TimesrapseApp)
            modules(appModule)
        }

        XLog.init(
            LogLevel.ALL,
            AndroidPrinter(true),
            promtailPrinter,
            Crashlytics()
        )

        ForegroundService.request(this, ForegroundService.Command.Start)
    }

    override fun onLowMemory() {
        super.onLowMemory()
        XLog.w("onLowMemory")
    }
}