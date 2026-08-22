package com.wjy.foxchat.notification

import android.content.Context
import org.json.JSONArray

/**
 * 定时通知的本地持久化存储（SharedPreferences + JSON）。
 * 数据简单，无需引入 Room 表。
 */
object ScheduledNotificationStore {
    private const val PREFS = "scheduled_notifications"
    private const val KEY_LIST = "list"

    fun list(context: Context): List<ScheduledNotification> {
        val raw = prefs(context).getString(KEY_LIST, null) ?: return emptyList()
        return runCatching {
            val arr = JSONArray(raw)
            buildList {
                for (i in 0 until arr.length()) {
                    add(ScheduledNotification.fromJson(arr.getJSONObject(i)))
                }
            }
        }.getOrDefault(emptyList())
            .sortedBy { it.triggerAt }
    }

    fun add(context: Context, item: ScheduledNotification) {
        val items = list(context).toMutableList().apply { add(item) }
        save(context, items)
    }

    fun remove(context: Context, id: String) {
        save(context, list(context).filterNot { it.id == id })
    }

    fun find(context: Context, id: String): ScheduledNotification? =
        list(context).firstOrNull { it.id == id }

    private fun save(context: Context, items: List<ScheduledNotification>) {
        val arr = JSONArray()
        items.forEach { arr.put(it.toJson()) }
        prefs(context).edit().putString(KEY_LIST, arr.toString()).apply()
    }

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}
