package com.example.simrelay

import android.Manifest
import android.app.ActivityManager
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.widget.Button
import android.widget.EditText
import android.widget.RadioGroup
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import java.net.Inet4Address
import java.net.NetworkInterface

class MainActivity : AppCompatActivity() {

    private lateinit var roleGroup: RadioGroup
    private lateinit var etSlaves: EditText
    private lateinit var etPort: EditText
    private lateinit var etPkgFilter: EditText
    private lateinit var tvStatus: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        roleGroup = findViewById(R.id.roleGroup)
        etSlaves = findViewById(R.id.etSlaves)
        etPort = findViewById(R.id.etPort)
        etPkgFilter = findViewById(R.id.etPkgFilter)
        tvStatus = findViewById(R.id.tvStatus)

        // 载入已存配置
        if (Prefs.getRole(this) == Prefs.MASTER) {
            roleGroup.check(R.id.rbMaster)
        } else {
            roleGroup.check(R.id.rbSlave)
        }
        etSlaves.setText(Prefs.getSlaves(this))
        etPort.setText(Prefs.getPort(this).toString())
        etPkgFilter.setText(Prefs.getPkgFilterRaw(this))

        showLocalIp()

        findViewById<Button>(R.id.btnStart).setOnClickListener { save(); startService(); refresh() }
        findViewById<Button>(R.id.btnStop).setOnClickListener { stopService(); refresh() }
        findViewById<Button>(R.id.btnHistory).setOnClickListener {
            startActivity(Intent(this, HistoryActivity::class.java))
        }
        findViewById<Button>(R.id.btnNotiAccess).setOnClickListener {
            save()
            try {
                startActivity(Intent("android.settings.ACTION_NOTIFICATION_LISTENER_SETTINGS"))
            } catch (_: Exception) {
                startActivity(Intent(android.provider.Settings.ACTION_SETTINGS))
            }
        }
        findViewById<Button>(R.id.btnFullScreenIntent).setOnClickListener {
            save()
            openFullScreenIntentSettings()
        }

        requestNeededPermissions()
        refresh()
    }

    override fun onResume() {
        super.onResume()
        showLocalIp() // WiFi 重连后 IP 会变, 回到前台时刷新
        refresh()
    }

    private fun showLocalIp() {
        val (ip, iface) = localIp()
        findViewById<TextView>(R.id.tvLocalIp).text = ip ?: "未连接WiFi"
        findViewById<TextView>(R.id.tvIface).text = iface?.let { "网卡: $it" } ?: ""
    }

    private fun save() {
        val role = if (roleGroup.checkedRadioButtonId == R.id.rbMaster) Prefs.MASTER else Prefs.SLAVE
        Prefs.setRole(this, role)
        Prefs.setSlaves(this, etSlaves.text.toString())
        val port = etPort.text.toString().toIntOrNull() ?: 5566
        Prefs.setPort(this, port)
        Prefs.setPkgFilter(this, etPkgFilter.text.toString())
    }

    private fun startService() {
        val i = Intent(this, NetService::class.java)
        ContextCompat.startForegroundService(this, i)
        Toast.makeText(this, "已启动", Toast.LENGTH_SHORT).show()
        if (Prefs.getRole(this) == Prefs.SLAVE) {
            requestIgnoreBatteryOptimizations()
        }
    }

    /** 从机若不在电池优化白名单, 部分厂商 ROM 会在息屏一段时间后冻结/杀死后台服务,
     *  导致主机来电时从机收不到推送、不响铃 */
    private fun requestIgnoreBatteryOptimizations() {
        val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
        if (pm.isIgnoringBatteryOptimizations(packageName)) return
        try {
            startActivity(Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                data = Uri.parse("package:$packageName")
            })
        } catch (_: Exception) {
        }
    }

    /** Android 14 起, USE_FULL_SCREEN_INTENT 需要用户手动在设置里开启,
     *  否则来电全屏通知会被系统降级, 锁屏/黑屏下不会弹出响铃界面 */
    private fun canUseFullScreenIntent(): Boolean {
        if (Build.VERSION.SDK_INT < 34) return true
        val nm = getSystemService(NotificationManager::class.java)
        return nm.canUseFullScreenIntent()
    }

    private fun openFullScreenIntentSettings() {
        if (Build.VERSION.SDK_INT < 34) {
            Toast.makeText(this, "当前系统版本无需单独开启", Toast.LENGTH_SHORT).show()
            return
        }
        if (canUseFullScreenIntent()) {
            Toast.makeText(this, "已开启", Toast.LENGTH_SHORT).show()
            return
        }
        try {
            startActivity(Intent(Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT).apply {
                data = Uri.parse("package:$packageName")
            })
        } catch (_: Exception) {
            startActivity(Intent(Settings.ACTION_SETTINGS))
        }
    }

    private fun stopService() {
        stopService(Intent(this, NetService::class.java))
        Prefs.setRunning(this, false)
        Toast.makeText(this, "已停止", Toast.LENGTH_SHORT).show()
    }

    /** Prefs 里的"运行中"只是记忆的意图, 覆盖安装/进程被杀等情况下会跟实际状态脱节;
     *  这里直接查系统里 NetService 是否真的活着做校正, 不依赖任何安装/开机广播的时机 */
    private fun isNetServiceActuallyRunning(): Boolean {
        return try {
            val am = getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
            @Suppress("DEPRECATION")
            am.getRunningServices(Int.MAX_VALUE).any { it.service.className == NetService::class.java.name }
        } catch (_: Exception) {
            false
        }
    }

    private fun refresh() {
        if (Prefs.isRunning(this) && !isNetServiceActuallyRunning()) {
            Prefs.setRunning(this, false)
        }
        val role = if (Prefs.getRole(this) == Prefs.MASTER) "主机" else "从机"
        val run = if (Prefs.isRunning(this)) "运行中" else "已停止"
        var text = "状态: $role · $run · 端口 ${Prefs.getPort(this)}"
        if (Prefs.getRole(this) == Prefs.SLAVE && !canUseFullScreenIntent()) {
            text += "\n⚠ 未开启锁屏全屏来电权限, 来电时无法亮屏响铃, 请点上方按钮开启"
        }
        tvStatus.text = text
    }

    /** 返回 (IPv4 地址, 网卡名); 优先 wlan */
    private fun localIp(): Pair<String?, String?> {
        var fallback: Pair<String?, String?> = null to null
        try {
            for (nif in NetworkInterface.getNetworkInterfaces()) {
                if (!nif.isUp || nif.isLoopback || nif.isVirtual) continue
                val name = nif.name ?: ""
                for (addr in nif.inetAddresses) {
                    if (addr is Inet4Address && !addr.isLoopbackAddress) {
                        val ip = addr.hostAddress
                        if (name.startsWith("wlan") || name.startsWith("eth")) {
                            return ip to name
                        }
                        if (fallback.first == null) fallback = ip to name
                    }
                }
            }
        } catch (_: Exception) {
        }
        return fallback
    }

    private fun requestNeededPermissions() {
        val perms = mutableListOf(
            Manifest.permission.RECEIVE_SMS,
            Manifest.permission.READ_SMS,
            Manifest.permission.READ_PHONE_STATE,
            Manifest.permission.READ_CALL_LOG
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            perms.add(Manifest.permission.POST_NOTIFICATIONS)
        }
        val need = perms.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }
        if (need.isNotEmpty()) {
            ActivityCompat.requestPermissions(this, need.toTypedArray(), 1)
        }
    }
}
