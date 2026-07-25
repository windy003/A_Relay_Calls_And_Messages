package com.example.simrelay

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/** 从机侧: 本地保存收到的短信/通知历史 */
object MessageStore {
    private const val NAME = "msgstore"
    private const val KEY = "list"
    private const val MAX = 1000 // 最多保留条数

    data class Item(val cat: String, val head: String, val body: String, val time: Long)

    private fun sp(c: Context) = c.getSharedPreferences(NAME, Context.MODE_PRIVATE)

    /** cat: "sms" 或 "noti" */
    @Synchronized
    fun add(c: Context, cat: String, head: String, body: String) {
        val arr = try {
            JSONArray(sp(c).getString(KEY, "[]"))
        } catch (_: Exception) {
            JSONArray()
        }
        val o = JSONObject()
        o.put("cat", cat)
        o.put("head", head)
        o.put("body", body)
        o.put("time", System.currentTimeMillis())
        arr.put(o)

        // 超出上限则丢弃最旧的
        val start = if (arr.length() > MAX) arr.length() - MAX else 0
        val out = JSONArray()
        for (i in start until arr.length()) out.put(arr.get(i))
        sp(c).edit().putString(KEY, out.toString()).apply()
    }

    /** 返回全部记录, 最新的在前 */
    fun getAll(c: Context): List<Item> {
        val list = ArrayList<Item>()
        try {
            val arr = JSONArray(sp(c).getString(KEY, "[]"))
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                list.add(
                    Item(
                        o.optString("cat", "noti"),
                        o.optString("head", ""),
                        o.optString("body", ""),
                        o.optLong("time", 0)
                    )
                )
            }
        } catch (_: Exception) {
        }
        list.reverse()
        return list
    }

    fun clear(c: Context) {
        sp(c).edit().remove(KEY).apply()
    }
}
