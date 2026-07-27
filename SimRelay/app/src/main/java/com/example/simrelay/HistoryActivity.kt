package com.example.simrelay

import android.app.AlertDialog
import android.content.res.ColorStateList
import android.graphics.Color
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.Button
import android.widget.ListView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** 从机侧: 短信/微信历史记录面板 */
class HistoryActivity : AppCompatActivity() {

    private lateinit var listView: ListView
    private lateinit var tvEmpty: TextView
    private lateinit var adapter: MsgAdapter
    private lateinit var btnAll: Button
    private lateinit var btnSms: Button
    private lateinit var btnNoti: Button
    private var filter: String = "all" // all / sms / noti
    private val fmt = SimpleDateFormat("MM-dd HH:mm:ss", Locale.getDefault())

    private val highlightTint = ColorStateList.valueOf(Color.parseColor("#2196F3"))
    private var defaultTextColor: ColorStateList? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_history)
        title = "历史记录"

        listView = findViewById(R.id.list)
        tvEmpty = findViewById(R.id.tvEmpty)
        adapter = MsgAdapter()
        listView.adapter = adapter

        btnAll = findViewById(R.id.btnAll)
        btnSms = findViewById(R.id.btnSms)
        btnNoti = findViewById(R.id.btnNoti)
        defaultTextColor = btnAll.textColors

        btnAll.setOnClickListener { filter = "all"; reload() }
        btnSms.setOnClickListener { filter = "sms"; reload() }
        btnNoti.setOnClickListener { filter = "noti"; reload() }
        findViewById<Button>(R.id.btnClear).setOnClickListener { confirmClear() }

        reload()
    }

    override fun onResume() {
        super.onResume()
        reload() // 回到面板时刷新, 显示新到的消息
    }

    private fun reload() {
        val all = MessageStore.getAll(this)
        val data = when (filter) {
            "sms" -> all.filter { it.cat == "sms" }
            "noti" -> all.filter { it.cat == "noti" }
            else -> all
        }
        adapter.setData(data)
        tvEmpty.visibility = if (data.isEmpty()) View.VISIBLE else View.GONE
        updateFilterHighlight()
    }

    private fun updateFilterHighlight() {
        for ((key, btn) in listOf("all" to btnAll, "sms" to btnSms, "noti" to btnNoti)) {
            if (key == filter) {
                btn.backgroundTintList = highlightTint
                btn.setTextColor(Color.WHITE)
            } else {
                btn.backgroundTintList = null
                btn.setTextColor(defaultTextColor)
            }
        }
    }

    private fun confirmClear() {
        AlertDialog.Builder(this)
            .setTitle("清空历史记录")
            .setMessage("确定删除全部记录?")
            .setPositiveButton("清空") { _, _ ->
                MessageStore.clear(this)
                reload()
            }
            .setNegativeButton("取消", null)
            .show()
    }

    private inner class MsgAdapter : BaseAdapter() {
        private var items: List<MessageStore.Item> = emptyList()

        fun setData(d: List<MessageStore.Item>) {
            items = d
            notifyDataSetChanged()
        }

        override fun getCount() = items.size
        override fun getItem(position: Int) = items[position]
        override fun getItemId(position: Int) = position.toLong()

        override fun getView(position: Int, convertView: View?, parent: ViewGroup?): View {
            val v = convertView ?: layoutInflater.inflate(R.layout.item_message, parent, false)
            val item = items[position]
            val tag = if (item.cat == "sms") "[短信] " else "[通知] "
            v.findViewById<TextView>(R.id.tvHead).text = tag + item.head
            v.findViewById<TextView>(R.id.tvBody).text = item.body
            v.findViewById<TextView>(R.id.tvTime).text = fmt.format(Date(item.time))
            return v
        }
    }
}
