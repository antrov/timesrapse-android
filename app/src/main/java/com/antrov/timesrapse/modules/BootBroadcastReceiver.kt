package com.antrov.timesrapse.modules

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.elvishew.xlog.XLog

class BootBroadcastReceiver : BroadcastReceiver() {

    private val logger = XLog.tag("BootBroadcastReceiver").build()

    override fun onReceive(pContext: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        logger.w("Device booted")
    }
}