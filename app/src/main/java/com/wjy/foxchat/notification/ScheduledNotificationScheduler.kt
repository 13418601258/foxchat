package com.wjy.foxchat.notification

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build

/** 用 AlarmManager 在指定时间精确触发定时通知。 */
object ScheduledNotificationScheduler {

    fun schedule(context: Context, item: ScheduledNotification) {
        val am = context.getSystemService(AlarmManager::class.java) ?: return
        val pi = pendingIntent(context, item.id)
        val canExact = Build.VERSION.SDK_INT < Build.VERSION_CODES.S ||
            am.canScheduleExactAlarms()
        if (canExact) {
            am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, item.triggerAt, pi)
        } else {
            // 无精确闹钟权限时降级为不精确提醒
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, item.triggerAt, pi)
        }
    }

    fun cancel(context: Context, id: String) {
        val am = context.getSystemService(AlarmManager::class.java) ?: return
        am.cancel(pendingIntent(context, id))
    }

    private fun pendingIntent(context: Context, id: String): PendingIntent {
        val intent = Intent(context, ScheduledNotificationReceiver::class.java).apply {
            putExtra(ScheduledNotificationReceiver.EXTRA_ID, id)
        }
        return PendingIntent.getBroadcast(
            context,
            id.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }
}
