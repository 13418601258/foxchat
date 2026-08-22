package com.wjy.foxchat.ui

import android.Manifest
import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.wjy.foxchat.notification.ChatNotificationManager
import com.wjy.foxchat.notification.ScheduledNotification
import com.wjy.foxchat.notification.ScheduledNotificationScheduler
import com.wjy.foxchat.notification.ScheduledNotificationStore
import com.wjy.foxchat.ui.compose.ChatColors
import com.wjy.foxchat.ui.compose.ChatTheme
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.UUID

class ScheduledNotificationActivity : ComponentActivity() {
    private val requestNotificationPermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            !ChatNotificationManager.canPost(this)
        ) {
            requestNotificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
        setContent {
            ChatTheme {
                ScheduledNotificationScreen(onBack = { finish() })
            }
        }
    }

    companion object {
        fun newIntent(context: Context): Intent =
            Intent(context, ScheduledNotificationActivity::class.java)
    }
}

@Composable
private fun ScheduledNotificationScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    var content by rememberSaveable { mutableStateOf("") }
    var triggerAt by rememberSaveable { mutableStateOf<Long?>(null) }
    var items by remember { mutableStateOf(ScheduledNotificationStore.list(context)) }
    var error by remember { mutableStateOf<String?>(null) }
    val calendar = remember { Calendar.getInstance() }
    val timeFormat = remember { SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()) }

    fun refresh() {
        items = ScheduledNotificationStore.list(context)
    }

    fun pickDateTime() {
        val dateDialog = DatePickerDialog(
            context,
            { _, year, month, day ->
                calendar.set(Calendar.YEAR, year)
                calendar.set(Calendar.MONTH, month)
                calendar.set(Calendar.DAY_OF_MONTH, day)
                val timeDialog = TimePickerDialog(
                    context,
                    { _, hour, minute ->
                        calendar.set(Calendar.HOUR_OF_DAY, hour)
                        calendar.set(Calendar.MINUTE, minute)
                        calendar.set(Calendar.SECOND, 0)
                        calendar.set(Calendar.MILLISECOND, 0)
                        triggerAt = calendar.timeInMillis
                    },
                    calendar.get(Calendar.HOUR_OF_DAY),
                    calendar.get(Calendar.MINUTE),
                    true
                )
                timeDialog.show()
            },
            calendar.get(Calendar.YEAR),
            calendar.get(Calendar.MONTH),
            calendar.get(Calendar.DAY_OF_MONTH)
        )
        dateDialog.show()
    }

    Column(modifier = Modifier.fillMaxSize()) {
        // 顶栏
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(ChatColors.Surface)
                .padding(horizontal = 8.dp, vertical = 8.dp)
                .height(56.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "返回",
                    tint = ChatColors.OnSurface
                )
            }
            Text(
                text = "定时通知",
                color = ChatColors.OnSurface,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold
            )
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(ChatColors.Background)
                .padding(16.dp)
        ) {
            OutlinedTextField(
                value = content,
                onValueChange = { content = it },
                placeholder = { Text("输入提醒内容", color = ChatColors.TextSecondary) },
                modifier = Modifier.fillMaxWidth(),
                maxLines = 3,
                shape = RoundedCornerShape(12.dp),
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = Color.White,
                    unfocusedContainerColor = Color.White,
                    focusedIndicatorColor = ChatColors.FoxOrange,
                    unfocusedIndicatorColor = Color.Transparent
                )
            )
            Spacer(Modifier.height(12.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedButton(
                    onClick = { pickDateTime() },
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.Schedule,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = triggerAt?.let { timeFormat.format(Date(it)) } ?: "选择时间",
                        color = if (triggerAt == null) ChatColors.TextSecondary else ChatColors.OnSurface,
                        fontSize = 14.sp
                    )
                }
                Spacer(Modifier.weight(1f))
                Button(
                    onClick = {
                        when {
                            content.isBlank() -> error = "请输入提醒内容"
                            triggerAt == null -> error = "请选择时间"
                            triggerAt!! <= System.currentTimeMillis() -> error = "时间需晚于当前时间"
                            !ChatNotificationManager.canPost(context) -> error = "未授予通知权限，请到系统设置开启通知"
                            else -> {
                                val item = ScheduledNotification(
                                    id = UUID.randomUUID().toString(),
                                    triggerAt = triggerAt!!,
                                    content = content.trim()
                                )
                                ScheduledNotificationStore.add(context, item)
                                ScheduledNotificationScheduler.schedule(context, item)
                                content = ""
                                triggerAt = null
                                error = null
                                refresh()
                            }
                        }
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = ChatColors.FoxOrange
                    ),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("添加", color = Color.White)
                }
            }
            if (error != null) {
                Spacer(Modifier.height(8.dp))
                Text(
                    text = error.orEmpty(),
                    color = ChatColors.FoxOrangeDeep,
                    fontSize = 13.sp
                )
            }
        }

        // 列表
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "已添加的提醒（${items.size}）",
                color = ChatColors.TextSecondary,
                fontSize = 13.sp,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)
            )
            if (items.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 60.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "暂无定时通知",
                        color = ChatColors.TextSecondary,
                        fontSize = 14.sp
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(
                        horizontal = 16.dp,
                        vertical = 4.dp
                    ),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(items, key = { it.id }) { item ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(ChatColors.Surface, RoundedCornerShape(12.dp))
                                .padding(horizontal = 14.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = timeFormat.format(Date(item.triggerAt)),
                                    color = ChatColors.FoxOrange,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Spacer(Modifier.height(4.dp))
                                Text(
                                    text = item.content,
                                    color = ChatColors.OnSurface,
                                    fontSize = 15.sp
                                )
                            }
                            IconButton(
                                onClick = {
                                    ScheduledNotificationScheduler.cancel(context, item.id)
                                    ScheduledNotificationStore.remove(context, item.id)
                                    refresh()
                                }
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Delete,
                                    contentDescription = "删除",
                                    tint = ChatColors.TextSecondary
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
