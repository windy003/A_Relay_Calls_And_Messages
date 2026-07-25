package com.example.simrelay

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat

/** 开机后, 若之前是从机且处于运行状态, 自动拉起 NetService */
class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action
        if (action != Intent.ACTION_BOOT_COMPLETED && action != "android.intent.action.QUICKBOOT_POWERON") return

        if (Prefs.getRole(context) == Prefs.SLAVE && Prefs.isRunning(context)) {
            val svc = Intent(context, NetService::class.java)
            ContextCompat.startForegroundService(context, svc)
        }
    }
}
