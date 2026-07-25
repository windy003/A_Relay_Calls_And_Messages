package com.example.simrelay

import android.app.Notification
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import org.json.JSONObject

/** 主机侧: 读取本机状态栏其它 App 的通知(如微信), 转发给从机 */
class NotiListener : NotificationListenerService() {

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        val ctx = applicationContext
        if (Prefs.getRole(ctx) != Prefs.MASTER) return

        val pkg = sbn.packageName ?: return
        if (pkg == packageName) return                     // 不转发自己
        val n = sbn.notification ?: return
        if (sbn.isOngoing) return                          // 跳过常驻/前台服务通知
        if (n.flags and Notification.FLAG_GROUP_SUMMARY != 0) return // 跳过分组汇总

        // 包名过滤: 留空=全部; 否则只转发包含关键词的
        val filter = Prefs.getPkgFilter(ctx)
        if (filter.isNotEmpty() && filter.none { pkg.contains(it) }) return

        val extras = n.extras
        val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString() ?: ""
        val text = (extras.getCharSequence(Notification.EXTRA_TEXT)
            ?: extras.getCharSequence(Notification.EXTRA_BIG_TEXT))?.toString() ?: ""
        if (title.isBlank() && text.isBlank()) return

        // 简单去重: 同一条内容 2 秒内只转发一次
        val key = "$pkg|$title|$text"
        if (key == lastKey && System.currentTimeMillis() - lastTime < 2000) return
        lastKey = key
        lastTime = System.currentTimeMillis()

        val appName = appLabel(pkg)
        val o = JSONObject()
        o.put("type", "noti")
        o.put("app", appName)
        o.put("title", title)
        o.put("body", text)
        Log.i(TAG, "转发通知: $appName / $title")
        Sender.broadcast(ctx, o.toString())
    }

    private fun appLabel(pkg: String): String {
        return try {
            val pm = packageManager
            pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString()
        } catch (_: Exception) {
            pkg
        }
    }

    companion object {
        private const val TAG = "NotiListener"
        private var lastKey: String? = null
        private var lastTime = 0L
    }
}
