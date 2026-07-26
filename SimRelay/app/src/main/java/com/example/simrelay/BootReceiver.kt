package com.example.simrelay

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat

/** 开机后, 若之前是从机且处于运行状态, 自动拉起 NetService;
 *  覆盖安装/更新后进程被杀掉, 服务不会自动恢复, 与其自动拉起(不可靠),
 *  不如直接把"运行中"状态重置为未启动, 让状态显示与实际相符, 提醒用户手动重新启动 */
class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            Intent.ACTION_BOOT_COMPLETED, "android.intent.action.QUICKBOOT_POWERON" -> {
                if (Prefs.getRole(context) == Prefs.SLAVE && Prefs.isRunning(context)) {
                    val svc = Intent(context, NetService::class.java)
                    ContextCompat.startForegroundService(context, svc)
                }
            }
            Intent.ACTION_MY_PACKAGE_REPLACED -> {
                Prefs.setRunning(context, false)
            }
        }
    }
}
