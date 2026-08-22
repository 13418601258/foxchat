package com.wjy.foxchat.notification

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/** 接收 AlarmManager 的定时广播，到点后发送通知。 */
class ScheduledNotificationReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val id = intent.getStringExtra(EXTRA_ID) ?: return
        val item = ScheduledNotificationStore.find(context, id) ?: return
        ChatNotificationManager.notifyScheduled(context, item)
        // 一次性通知，触发后移除
        ScheduledNotificationStore.remove(context, id)
    }

    companion object {
        const val EXTRA_ID = "scheduled_id"
    }
}
