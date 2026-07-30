package com.example.simrelay

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.os.Build
import android.os.PowerManager
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
    @Volatile private var alertPlayer: MediaPlayer? = null

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
     * 主动播放一次提示音(短信/微信等转发通知共用), 用 assets 里指定的 mp3,
     * 走"铃声"声道(USAGE_NOTIFICATION_RINGTONE), 和来电响铃相同的声道 ——
     * 既然来电能出声, 这条路在本机一定能出声, 不受"通知音量为 0 / 震动模式"影响.
     */
    fun playAlert(c: Context) {
        try {
            alertPlayer?.let { old ->
                if (old.isPlaying) old.stop()
                old.release()
            }
            alertPlayer = null

            val afd = c.resources.openRawResourceFd(R.raw.message_coming_male) ?: return
            val mp = MediaPlayer()
            afd.use { mp.setDataSource(it.fileDescriptor, it.startOffset, it.length) }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                mp.setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_NOTIFICATION_RINGTONE)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build()
                )
            }
            mp.setOnCompletionListener { p ->
                p.release()
                if (alertPlayer === p) alertPlayer = null
            }
            mp.prepare()
            mp.start()
            alertPlayer = mp
        } catch (_: Exception) {
        }
    }

    /** 黑屏/锁屏下短信、App通知也要亮屏, 只是不像来电那样弹全屏 Activity:
     *  用 SCREEN_BRIGHT_WAKE_LOCK + ACQUIRE_CAUSES_WAKEUP 直接点亮屏幕几秒,
     *  让抬头通知能在锁屏上正常显示、出声 */
    @Suppress("DEPRECATION")
    private fun wakeScreenBriefly(c: Context) {
        try {
            val pm = c.getSystemService(Context.POWER_SERVICE) as PowerManager
            pm.newWakeLock(
                PowerManager.SCREEN_BRIGHT_WAKE_LOCK or PowerManager.ACQUIRE_CAUSES_WAKEUP,
                "SimRelay:msg"
            ).acquire(5_000L)
        } catch (_: Exception) {
        }
    }

    /** 收到转发的其它 App 通知(如微信) */
    fun incomingNoti(c: Context, app: String, title: String, body: String) {
        wakeScreenBriefly(c)
        val contentTitle = if (title.isBlank()) app else "$app · $title"
        val n = NotificationCompat.Builder(c, CH_NOTI)
            .setSmallIcon(R.drawable.ic_notify)
            .setContentTitle(contentTitle)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_MESSAGE)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
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
            .setSmallIcon(R.drawable.ic_notify)
            .setContentTitle("SimRelay 运行中")
            .setContentText("角色: " + if (Prefs.getRole(c) == Prefs.MASTER) "主机" else "从机(端口 ${Prefs.getPort(c)})")
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    /** 收到来电: 全屏拉起响铃界面 */
    fun incomingCall(c: Context, number: String) {
        // 黑屏/Doze 状态下, 保证处理完到拉起 Activity 这段过程 CPU 不会又睡回去
        try {
            val pm = c.getSystemService(Context.POWER_SERVICE) as PowerManager
            pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "SimRelay:call").acquire(10_000L)
        } catch (_: Exception) {
        }
        // RingActivity 的 setTurnScreenOn 在部分机型(如三星)上不保证真的点亮屏幕:
        // 实测出现过 Activity/通知已经弹出(showWhenLocked 生效), 但屏幕背光没亮,
        // 用户要手动按电源键才看到已经在响铃 —— 这里主动点亮屏幕兜底
        wakeScreenBriefly(c)

        val i = Intent(c, RingActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            putExtra("number", number)
        }
        val pi = PendingIntent.getActivity(
            c, 0, i,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val n = NotificationCompat.Builder(c, CH_CALL)
            .setSmallIcon(R.drawable.ic_notify)
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

    /** 主机接听或挂断了电话: 取消全屏响铃通知, 并关掉正在显示的响铃界面 */
    fun cancelCall(c: Context) {
        try {
            NotificationManagerCompat.from(c).cancel(ID_CALL)
        } catch (_: Exception) {
        }
        RingActivity.dismiss()
    }

    /** 收到短信: 弹通知显示内容 */
    fun incomingSms(c: Context, from: String, body: String) {
        wakeScreenBriefly(c)
        val n = NotificationCompat.Builder(c, CH_SMS)
            .setSmallIcon(R.drawable.ic_notify)
            .setContentTitle("主机短信 · $from")
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setPriority(NotificationCompat.PRIORITY_HIGH) // 抬头(heads-up)提醒
            .setCategory(NotificationCompat.CATEGORY_MESSAGE)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC) // 锁屏亮屏界面上也显示完整内容, 不被折叠成"新通知"
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
