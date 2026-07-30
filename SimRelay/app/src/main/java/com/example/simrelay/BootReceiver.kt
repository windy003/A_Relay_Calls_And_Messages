package com.example.simrelay

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat

/** 开机, 或覆盖安装/更新后, 不管当前是主机还是从机角色, 都自动拉起 NetService,
 *  不需要用户手动打开 App 再点启动 */
class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            Intent.ACTION_BOOT_COMPLETED,
            "android.intent.action.QUICKBOOT_POWERON",
            Intent.ACTION_MY_PACKAGE_REPLACED -> {
                val svc = Intent(context, NetService::class.java)
                ContextCompat.startForegroundService(context, svc)
            }
        }
    }
}
