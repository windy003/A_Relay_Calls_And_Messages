package com.example.simrelay

import android.app.AlarmManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.os.SystemClock
import android.util.Log
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.ServerSocket
import java.net.Socket

/** 前台服务: 从机角色时监听 TCP, 接收主机推送的事件 */
class NetService : Service() {

    @Volatile private var running = false
    private var serverSocket: ServerSocket? = null
    private var thread: Thread? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        Notify.createChannels(this)
        startForeground(Notify.ID_FG, Notify.foreground(this))
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Prefs.setRunning(this, true)
        if (Prefs.getRole(this) == Prefs.SLAVE && !running) {
            running = true
            startServer(Prefs.getPort(this))
        }
        return START_STICKY
    }

    private fun startServer(port: Int) {
        thread = Thread {
            try {
                ServerSocket(port).use { ss ->
                    serverSocket = ss
                    Log.i(TAG, "监听端口 $port")
                    while (running) {
                        val client = ss.accept()
                        handle(client)
                    }
                }
            } catch (e: Exception) {
                if (running) Log.w(TAG, "server 异常: ${e.message}")
            }
        }.also { it.start() }
    }

    private fun handle(client: Socket) {
        try {
            client.use {
                val line = BufferedReader(
                    InputStreamReader(it.getInputStream(), Charsets.UTF_8)
                ).readLine() ?: return
                Log.i(TAG, "收到: $line")
                val o = JSONObject(line)
                when (o.optString("type")) {
                    "call" -> Notify.incomingCall(this, o.optString("number", "未知号码"))
                    "sms" -> {
                        val from = o.optString("from", "未知号码")
                        val body = o.optString("body", "")
                        MessageStore.add(this, "sms", from, body)
                        Notify.incomingSms(this, from, body)
                    }
                    "noti" -> {
                        val app = o.optString("app", "通知")
                        val title = o.optString("title", "")
                        val body = o.optString("body", "")
                        val head = if (title.isBlank()) app else "$app · $title"
                        MessageStore.add(this, "noti", head, body)
                        Notify.incomingNoti(this, app, title, body)
                    }
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "处理异常: ${e.message}")
        }
    }

    /** App 被从"最近任务"划掉时, 部分厂商 ROM 会直接杀掉进程(前台服务也不例外);
     *  这里用 AlarmManager 尽快把服务重新拉起来, 避免响铃功能失效 */
    override fun onTaskRemoved(rootIntent: Intent?) {
        super.onTaskRemoved(rootIntent)
        if (Prefs.getRole(this) == Prefs.SLAVE && Prefs.isRunning(this)) {
            val restartIntent = Intent(applicationContext, NetService::class.java)
            val pi = PendingIntent.getService(
                applicationContext, 1, restartIntent,
                PendingIntent.FLAG_ONE_SHOT or PendingIntent.FLAG_IMMUTABLE
            )
            val am = getSystemService(ALARM_SERVICE) as AlarmManager
            val triggerAt = SystemClock.elapsedRealtime() + 1000
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                am.setExactAndAllowWhileIdle(AlarmManager.ELAPSED_REALTIME_WAKEUP, triggerAt, pi)
            } else {
                am.set(AlarmManager.ELAPSED_REALTIME_WAKEUP, triggerAt, pi)
            }
        }
    }

    override fun onDestroy() {
        running = false
        try {
            serverSocket?.close()
        } catch (_: Exception) {
        }
        Prefs.setRunning(this, false)
        super.onDestroy()
    }

    companion object {
        private const val TAG = "NetService"
    }
}
