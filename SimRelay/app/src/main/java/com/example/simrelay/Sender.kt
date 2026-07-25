package com.example.simrelay

import android.content.Context
import android.util.Log
import java.net.InetSocketAddress
import java.net.Socket

/** 主机侧: 把事件推送给所有从机 IP */
object Sender {

    fun broadcast(ctx: Context, json: String) {
        val port = Prefs.getPort(ctx)
        val slaves = Prefs.getSlaves(ctx)
            .split(",", ";", "\n", " ")
            .map { it.trim() }
            .filter { it.isNotEmpty() }

        if (slaves.isEmpty()) {
            Log.w("Sender", "没有配置从机 IP")
            return
        }

        Thread {
            for (ip in slaves) {
                try {
                    Socket().use { s ->
                        s.connect(InetSocketAddress(ip, port), 3000)
                        val out = s.getOutputStream()
                        out.write((json + "\n").toByteArray(Charsets.UTF_8))
                        out.flush()
                    }
                    Log.i("Sender", "已发送到 $ip")
                } catch (e: Exception) {
                    Log.w("Sender", "发送到 $ip 失败: ${e.message}")
                }
            }
        }.start()
    }
}
