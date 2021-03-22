package com.antrov.timesrapse.utils.xlog

import com.elvishew.xlog.LogLevel
import com.elvishew.xlog.printer.Printer
import com.google.firebase.crashlytics.FirebaseCrashlytics

class Crashlytics: Printer {

    override fun println(logLevel: Int, tag: String?, msg: String?) {
        FirebaseCrashlytics.getInstance().apply {
            log("$tag: $msg")

            if (logLevel == LogLevel.ERROR) {
                recordException(Throwable(msg))
            }
        }
    }

}