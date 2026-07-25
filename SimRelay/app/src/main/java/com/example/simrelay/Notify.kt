package com.example.simrelay

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.Ringtone
import android.media.RingtoneManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat

/** 从机侧: 通知与全屏来电 */
object Notify {
    const val CH_FG = "fg"
    const val CH_CALL = "call"
    // 换新 id: 渠道本身不出声, 声音改由代码主动播(走铃声声道, 更可靠), 换 id 让新设置生效
    const val CH_SMS = "sms_v3"
    const val CH_NOTI = "noti_v2"

    const val ID_FG = 1
    const val ID_CALL = 1001
    private var smsId = 2000
    private var notiId = 3000
    @Volatile private var alertRingtone: Ringtone? = null

    fun createChannels(c: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val nm = c.getSystemService(NotificationManager::class.java)

            nm.createNotificationChannel(
                NotificationChannel(CH_FG, "服务运行", NotificationManager.IMPORTANCE_LOW)
            )

            val call = NotificationChannel(
                CH_CALL, "来电提醒", NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "主机来电时的全屏提醒"
                setSound(null, null) // 铃声由 RingActivity 播放
                enableVibration(false)
            }
            nm.createNotificationChannel(call)

            // 渠道本身不出声(setSound null), 声音统一由 playAlert() 主动播放
            val sms = NotificationChannel(
                CH_SMS, "短信提醒", NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "主机短信提醒"
                setSound(null, null)
                enableVibration(true)
                vibrationPattern = longArrayOf(0, 400, 200, 400)
                enableLights(true)
            }
            nm.createNotificationChannel(sms)

            val noti = NotificationChannel(
                CH_NOTI, "微信/其它通知", NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "转发过来的其它 App 通知"
                setSound(null, null)
                enableVibration(true)
                vibrationPattern = longArrayOf(0, 400, 200, 400)
                enableLights(true)
            }
            nm.createNotificationChannel(noti)
        }
    }

    /**
     * 主动播放一次提示音. 走"铃声"声道(USAGE_NOTIFICATION_RINGTONE),
     * 和来电响铃相同的声道 —— 既然来电能出声, 这条路在本机一定能出声,
     * 不受"通知音量为 0 / 震动模式"影响.
     */
    fun playAlert(c: Context) {
        try {
            alertRingtone?.let { if (it.isPlaying) it.stop() }
            val uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
                ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)
                ?: return
            val rt = RingtoneManager.getRingtone(c.applicationContext, uri) ?: return
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                rt.audioAttributes = AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_NOTIFICATION_RINGTONE)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build()
            }
            alertRingtone = rt
            rt.play()
        } catch (_: Exception) {
        }
    }

    /** 收到转发的其它 App 通知(如微信) */
    fun incomingNoti(c: Context, app: String, title: String, body: String) {
        val contentTitle = if (title.isBlank()) app else "$app · $title"
        val n = NotificationCompat.Builder(c, CH_NOTI)
            .setSmallIcon(android.R.drawable.stat_notify_chat)
            .setContentTitle(contentTitle)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_MESSAGE)
            .setVibrate(longArrayOf(0, 400, 200, 400))
            .setLights(0xFF00FF00.toInt(), 500, 500)
            .setAutoCancel(true)
            .build()

        playAlert(c)
        try {
            NotificationManagerCompat.from(c).notify(notiId++, n)
        } catch (_: SecurityException) {
        }
    }

    /** 前台服务常驻通知 */
    fun foreground(c: Context): Notification {
        return NotificationCompat.Builder(c, CH_FG)
            .setSmallIcon(android.R.drawable.stat_sys_data_bluetooth)
            .setContentTitle("SimRelay 运行中")
            .setContentText("角色: " + if (Prefs.getRole(c) == Prefs.MASTER) "主机" else "从机(端口 ${Prefs.getPort(c)})")
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    /** 收到来电: 全屏拉起响铃界面 */
    fun incomingCall(c: Context, number: String) {
        val i = Intent(c, RingActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            putExtra("number", number)
        }
        val pi = PendingIntent.getActivity(
            c, 0, i,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val n = NotificationCompat.Builder(c, CH_CALL)
            .setSmallIcon(android.R.drawable.sym_call_incoming)
            .setContentTitle("主机来电")
            .setContentText(number)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_CALL)
            .setFullScreenIntent(pi, true)
            .setAutoCancel(true)
            .build()

        try {
            NotificationManagerCompat.from(c).notify(ID_CALL, n)
        } catch (_: SecurityException) {
        }

        // 前台服务允许后台启动 Activity, 作为主路径直接拉起
        try {
            c.startActivity(i)
        } catch (_: Exception) {
        }
    }

    /** 收到短信: 弹通知显示内容 */
    fun incomingSms(c: Context, from: String, body: String) {
        val n = NotificationCompat.Builder(c, CH_SMS)
            .setSmallIcon(android.R.drawable.sym_action_email)
            .setContentTitle("主机短信 · $from")
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setPriority(NotificationCompat.PRIORITY_HIGH) // 抬头(heads-up)提醒
            .setCategory(NotificationCompat.CATEGORY_MESSAGE)
            .setVibrate(longArrayOf(0, 400, 200, 400))
            .setLights(0xFF00FF00.toInt(), 500, 500)
            .setAutoCancel(true)
            .build()

        playAlert(c)
        try {
            NotificationManagerCompat.from(c).notify(smsId++, n)
        } catch (_: SecurityException) {
        }
    }
}
