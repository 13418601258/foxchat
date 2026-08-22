package com.wjy.foxchat.notification

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.wjy.foxchat.R
import com.wjy.foxchat.data.local.MessageEntity
import com.wjy.foxchat.model.Message
import com.wjy.foxchat.ui.ChatActivity

object ChatNotificationManager {
    const val CHANNEL_ID = "foxchat_messages"
    private const val NOTIFICATION_ID = 2001
    private const val CHANNEL_ID_REMINDER = "foxchat_reminders"
    private const val REMINDER_NOTIFICATION_ID = 2101

    fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val channel = NotificationChannel(
            CHANNEL_ID,
            "FoxChat 消息",
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "一对一聊天的新消息通知"
        }
        context.getSystemService(NotificationManager::class.java)
            .createNotificationChannel(channel)
    }

    fun canPost(context: Context): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED

    fun notifyIncoming(context: Context, messages: List<MessageEntity>) {
        if (messages.isEmpty() || !canPost(context)) return
        ensureChannel(context)

        val previews = messages.takeLast(5).map(::preview)
        val latest = previews.last()
        val contentIntent = PendingIntent.getActivity(
            context,
            NOTIFICATION_ID,
            ChatActivity.newIntent(context).apply {
                addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                addFlags(android.content.Intent.FLAG_ACTIVITY_CLEAR_TOP)
                addFlags(android.content.Intent.FLAG_ACTIVITY_SINGLE_TOP)
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val style = NotificationCompat.InboxStyle()
            .setSummaryText("${messages.size} 条新消息")
        previews.forEach(style::addLine)

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("FoxChat")
            .setContentText(latest)
            .setStyle(style)
            .setNumber(messages.size)
            .setContentIntent(contentIntent)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .build()

        NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, notification)
    }

    fun cancel(context: Context) {
        NotificationManagerCompat.from(context).cancel(NOTIFICATION_ID)
    }

    fun notifyScheduled(context: Context, item: ScheduledNotification) {
        if (!canPost(context)) return
        ensureReminderChannel(context)

        val contentIntent = PendingIntent.getActivity(
            context,
            REMINDER_NOTIFICATION_ID,
            ChatActivity.newIntent(context).apply {
                addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                addFlags(android.content.Intent.FLAG_ACTIVITY_CLEAR_TOP)
                addFlags(android.content.Intent.FLAG_ACTIVITY_SINGLE_TOP)
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val notification = NotificationCompat.Builder(context, CHANNEL_ID_REMINDER)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("定时提醒")
            .setContentText(item.content)
            .setStyle(NotificationCompat.BigTextStyle().bigText(item.content))
            .setContentIntent(contentIntent)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .build()

        NotificationManagerCompat.from(context).notify(REMINDER_NOTIFICATION_ID, notification)
    }

    private fun ensureReminderChannel(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val channel = NotificationChannel(
            CHANNEL_ID_REMINDER,
            "定时提醒",
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "用户设定的定时通知"
        }
        context.getSystemService(NotificationManager::class.java)
            .createNotificationChannel(channel)
    }

    private fun preview(message: MessageEntity): String = when (message.type) {
        Message.TYPE_IMAGE -> "收到图片消息"
        Message.TYPE_AUDIO -> "收到语音消息"
        Message.TYPE_CHECKIN -> "有新的打卡：${message.text.orEmpty().trim().take(40)}"
        Message.TYPE_CHECKIN_REPLY -> "对方已打卡"
        else -> message.text.orEmpty().trim().ifBlank { "收到一条新消息" }.take(80)
    }
}
