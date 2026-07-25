package com.example.simrelay

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import android.telephony.TelephonyManager
import android.util.Log
import org.json.JSONObject

/** 主机侧: 监听来电与短信广播 */
class PhoneReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        // 只有主机角色才转发
        if (Prefs.getRole(context) != Prefs.MASTER) return

        when (intent.action) {
            TelephonyManager.ACTION_PHONE_STATE_CHANGED -> {
                val state = intent.getStringExtra(TelephonyManager.EXTRA_STATE)
                if (TelephonyManager.EXTRA_STATE_RINGING == state) {
                    val number = intent.getStringExtra(TelephonyManager.EXTRA_INCOMING_NUMBER)
                        ?: "未知号码"
                    // 同一次响铃系统可能回调多次, 用简单去重
                    if (number == lastNumber &&
                        System.currentTimeMillis() - lastCallTime < 3000
                    ) return
                    lastNumber = number
                    lastCallTime = System.currentTimeMillis()

                    val o = JSONObject()
                    o.put("type", "call")
                    o.put("number", number)
                    Log.i("PhoneReceiver", "来电: $number")
                    Sender.broadcast(context, o.toString())
                }
            }

            Telephony.Sms.Intents.SMS_RECEIVED_ACTION -> {
                val msgs = Telephony.Sms.Intents.getMessagesFromIntent(intent)
                if (msgs != null && msgs.isNotEmpty()) {
                    val from = msgs[0].displayOriginatingAddress ?: "未知号码"
                    val body = StringBuilder()
                    for (m in msgs) body.append(m.displayMessageBody ?: "")

                    val o = JSONObject()
                    o.put("type", "sms")
                    o.put("from", from)
                    o.put("body", body.toString())
                    Log.i("PhoneReceiver", "短信: $from")
                    Sender.broadcast(context, o.toString())
                }
            }
        }
    }

    companion object {
        private var lastNumber: String? = null
        private var lastCallTime: Long = 0
    }
}
