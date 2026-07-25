package com.example.simrelay

import android.content.Context

/** 本地配置存储 */
object Prefs {
    private const val NAME = "simrelay"
    const val MASTER = "master"
    const val SLAVE = "slave"

    private fun sp(c: Context) = c.getSharedPreferences(NAME, Context.MODE_PRIVATE)

    fun getRole(c: Context): String = sp(c).getString("role", SLAVE) ?: SLAVE
    fun setRole(c: Context, v: String) = sp(c).edit().putString("role", v).apply()

    fun getPort(c: Context): Int = sp(c).getInt("port", 5566)
    fun setPort(c: Context, v: Int) = sp(c).edit().putInt("port", v).apply()

    /** 从机 IP 列表, 用逗号/分号/换行分隔 */
    fun getSlaves(c: Context): String = sp(c).getString("slaves", "") ?: ""
    fun setSlaves(c: Context, v: String) = sp(c).edit().putString("slaves", v).apply()

    fun isRunning(c: Context): Boolean = sp(c).getBoolean("running", false)
    fun setRunning(c: Context, v: Boolean) = sp(c).edit().putBoolean("running", v).apply()

    /** 要转发通知的包名关键词, 逗号/换行分隔; 默认只转发微信; 留空=全部 App */
    fun getPkgFilterRaw(c: Context): String = sp(c).getString("pkgfilter", "com.tencent.mm") ?: "com.tencent.mm"
    fun setPkgFilter(c: Context, v: String) = sp(c).edit().putString("pkgfilter", v).apply()
    fun getPkgFilter(c: Context): List<String> = getPkgFilterRaw(c)
        .split(",", ";", "\n", " ")
        .map { it.trim() }
        .filter { it.isNotEmpty() }
}
