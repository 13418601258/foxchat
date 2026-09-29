package com.wjy.foxchat.ui

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Today
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.wjy.foxchat.data.local.TimeBlockEntity
import com.wjy.foxchat.data.repository.ChatRepository
import com.wjy.foxchat.ui.compose.ChatColors
import com.wjy.foxchat.ui.compose.ChatTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

class TimeBlocksActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            ChatTheme {
                TimeBlocksScreen(onBack = { finish() })
            }
        }
    }

    companion object {
        fun newIntent(context: Context): Intent = Intent(context, TimeBlocksActivity::class.java)
    }
}

@Composable
private fun TimeBlocksScreen(onBack: () -> Unit) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val repository = remember(context) { ChatRepository.get(context) }
    val scope = rememberCoroutineScope()
    val blocks by repository.observeTimeBlocks().collectAsState(initial = emptyList())
    val zone = remember { ZoneId.systemDefault() }
    val today = remember { LocalDate.now(zone) }
    var weekStart by remember { mutableStateOf(startOfWeek(today)) }
    var currentBlock by remember { mutableStateOf<TimeBlockEntity?>(null) }
    var descriptionDraft by remember { mutableStateOf("") }
    var descriptionTarget by remember { mutableStateOf<TimeBlockEntity?>(null) }
    var showDescriptionDialog by remember { mutableStateOf(false) }
    var selectedCellBlocks by remember { mutableStateOf<List<TimeBlockEntity>>(emptyList()) }
    var busy by remember { mutableStateOf(true) }
    var status by remember { mutableStateOf("正在读取记录") }
    var error by remember { mutableStateOf<String?>(null) }
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }

    LaunchedEffect(Unit) {
        runCatching {
            repository.ensureInitialized()
            currentBlock = repository.currentTimeBlock()
            if (currentBlock?.endedAt != null) {
                descriptionTarget = currentBlock
                showDescriptionDialog = true
            }
        }.onFailure { error = it.message ?: "读取计时状态失败" }
        busy = false
    }

    LaunchedEffect(Unit) {
        while (true) {
            now = System.currentTimeMillis()
            delay(1_000L)
        }
    }

    LaunchedEffect(Unit) {
        while (true) {
            if (repository.isRemoteConfigured && repository.isPaired) {
                val synced = repository.syncNow().isSuccess && !repository.hasPendingTimeBlocks()
                status = if (synced) "已同步" else "离线，待同步"
            } else {
                status = "仅本机"
            }
            delay(20_000L)
        }
    }

    LaunchedEffect(blocks) {
        currentBlock = blocks.firstOrNull { it.endedAt == null }
            ?: blocks.firstOrNull { it.endedAt != null && it.description.isNullOrBlank() }
    }

    val finishedBlocks = remember(blocks) {
        blocks.filter { it.endedAt != null && !it.description.isNullOrBlank() }
    }
    val visibleDays = remember(weekStart) { (0..6).map { weekStart.plusDays(it.toLong()) } }
    val weekEnd = weekStart.plusDays(7)
    val weekStartMs = weekStart.atStartOfDay(zone).toInstant().toEpochMilli()
    val weekEndMs = weekEnd.atStartOfDay(zone).toInstant().toEpochMilli()
    val weekDurationMs = finishedBlocks.sumOf { block ->
        val end = block.endedAt ?: return@sumOf 0L
        (minOf(end, weekEndMs) - maxOf(block.startedAt, weekStartMs)).coerceAtLeast(0L)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(ChatColors.Background)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(ChatColors.Surface)
                .padding(horizontal = 8.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
            }
            Column(modifier = Modifier.weight(1f)) {
                Text("时间块", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = ChatColors.OnSurface)
                Text(status, fontSize = 11.sp, color = ChatColors.TextSecondary)
            }
        }

        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
        ) {
            Spacer(Modifier.height(12.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { weekStart = weekStart.minusWeeks(1) }) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "上一周")
                }
                Text(
                    text = formatWeek(weekStart, weekStart.plusDays(6)),
                    modifier = Modifier.weight(1f),
                    textAlign = TextAlign.Center,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = ChatColors.OnSurface
                )
                IconButton(
                    onClick = { weekStart = weekStart.plusWeeks(1) },
                    enabled = true
                ) {
                        Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = "下一周")
                }
                IconButton(onClick = { weekStart = startOfWeek(today) }) {
                    Icon(Icons.Filled.Today, contentDescription = "回到本周", tint = ChatColors.FoxOrange)
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("本周学习", color = ChatColors.TextSecondary, fontSize = 12.sp)
                    Text(formatDuration(weekDurationMs), color = ChatColors.OnSurface, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text("已记录", color = ChatColors.TextSecondary, fontSize = 12.sp)
                    Text("${finishedBlocks.count { it.startedAt < weekEndMs && (it.endedAt ?: 0L) > weekStartMs }} 段", color = ChatColors.OnSurface, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                }
            }

            if (busy) {
                Text("正在加载…", color = ChatColors.TextSecondary, modifier = Modifier.padding(vertical = 12.dp))
            } else {
                val active = currentBlock?.takeIf { it.endedAt == null }
                val draft = currentBlock?.takeIf { it.endedAt != null }
                if (active != null) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color(0xFFFFEEE8))
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("正在计时", color = ChatColors.FoxOrangeDeep, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                            Text(formatDuration((now - active.startedAt).coerceAtLeast(0L)), color = ChatColors.OnSurface, fontSize = 13.sp)
                        }
                        Button(
                            onClick = {
                                if (busy) return@Button
                                busy = true
                                scope.launch {
                                    runCatching { repository.stopTimeBlock() }
                                        .onSuccess {
                                            currentBlock = it
                                            descriptionTarget = it
                                            descriptionDraft = ""
                                            showDescriptionDialog = true
                                        }
                                        .onFailure { error = it.message ?: "结束计时失败" }
                                    busy = false
                                }
                            },
                            enabled = !busy,
                            colors = ButtonDefaults.buttonColors(containerColor = ChatColors.FoxOrange)
                        ) {
                            Icon(Icons.Filled.Stop, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("结束")
                        }
                    }
                } else if (draft != null) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color(0xFFFFF4EF))
                            .padding(horizontal = 12.dp, vertical = 9.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("有一段时间待填写内容", modifier = Modifier.weight(1f), color = ChatColors.OnSurface, fontSize = 13.sp)
                        TextButton(onClick = {
                            descriptionTarget = draft
                            showDescriptionDialog = true
                        }) { Text("继续填写") }
                    }
                } else {
                    Button(
                        onClick = {
                            if (busy) return@Button
                            busy = true
                            scope.launch {
                                runCatching { repository.startTimeBlock() }
                                    .onSuccess { currentBlock = it; error = null }
                                    .onFailure { error = it.message ?: "开始计时失败" }
                                busy = false
                            }
                        },
                        enabled = !busy,
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = ChatColors.FoxOrange)
                    ) {
                        Icon(Icons.Filled.PlayArrow, contentDescription = null)
                        Spacer(Modifier.width(6.dp))
                        Text("开始学习计时")
                    }
                }
            }

            error?.let {
                Text(it, color = MaterialTheme.colorScheme.error, fontSize = 12.sp, modifier = Modifier.padding(top = 6.dp))
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 14.dp, bottom = 8.dp),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                LegendDot(color = colorForRole("A"))
                Text("成员 A", fontSize = 11.sp, color = ChatColors.TextSecondary)
                Spacer(Modifier.width(12.dp))
                LegendDot(color = colorForRole("B"))
                Text("成员 B", fontSize = 11.sp, color = ChatColors.TextSecondary)
            }
            HorizontalDivider(color = ChatColors.ChatLine)

            WeekGrid(
                days = visibleDays,
                blocks = finishedBlocks,
                zone = zone,
                onCellClick = { selectedCellBlocks = it }
            )

            HorizontalDivider(color = ChatColors.ChatLine, modifier = Modifier.padding(top = 10.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(vertical = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                visibleDays.forEachIndexed { index, date ->
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(DAY_LABELS[index], color = ChatColors.TextSecondary, fontSize = 10.sp)
                        Text(formatDuration(durationForDate(finishedBlocks, date, zone)), color = ChatColors.OnSurface, fontSize = 11.sp, fontWeight = FontWeight.Medium)
                    }
                }
            }
        }
    }

    if (showDescriptionDialog && descriptionTarget != null) {
        AlertDialog(
            onDismissRequest = { showDescriptionDialog = false },
            title = { Text("这段时间做了什么？") },
            text = {
                Column {
                    val target = descriptionTarget!!
                    Text(
                        "${formatDateTime(target.startedAt, zone)} - ${formatDateTime(target.endedAt ?: now, zone)} · ${formatDuration(((target.endedAt ?: now) - target.startedAt).coerceAtLeast(0L))}",
                        color = ChatColors.TextSecondary,
                        fontSize = 12.sp,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                    OutlinedTextField(
                        value = descriptionDraft,
                        onValueChange = { descriptionDraft = it },
                        placeholder = { Text("例如：复习线性代数第三章") },
                        minLines = 2,
                        maxLines = 4,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                TextButton(
                    enabled = descriptionDraft.isNotBlank() && !busy,
                    onClick = {
                        val target = descriptionTarget ?: return@TextButton
                        busy = true
                        scope.launch {
                            runCatching { repository.completeTimeBlock(target.id, descriptionDraft) }
                                .onSuccess {
                                    currentBlock = null
                                    descriptionTarget = null
                                    showDescriptionDialog = false
                                    error = null
                                }
                                .onFailure { error = it.message ?: "保存失败" }
                            busy = false
                        }
                    }
                ) { Text("保存记录") }
            },
            dismissButton = {
                TextButton(onClick = { showDescriptionDialog = false }) { Text("稍后填写") }
            }
        )
    }

    if (selectedCellBlocks.isNotEmpty()) {
        AlertDialog(
            onDismissRequest = { selectedCellBlocks = emptyList() },
            title = { Text("时间块详情") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    selectedCellBlocks.sortedBy { it.startedAt }.forEach { block ->
                        Column {
                            Text(
                                "成员 ${block.creatorRole} · ${formatDateTime(block.startedAt, zone)} - ${formatDateTime(block.endedAt!!, zone)}",
                                fontSize = 12.sp,
                                color = ChatColors.TextSecondary
                            )
                            Text(block.description.orEmpty(), fontSize = 14.sp, color = ChatColors.OnSurface)
                            Text(formatDuration(block.endedAt - block.startedAt), fontSize = 12.sp, color = ChatColors.TextSecondary)
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { selectedCellBlocks = emptyList() }) { Text("关闭") }
            }
        )
    }
}

@Composable
private fun WeekGrid(
    days: List<LocalDate>,
    blocks: List<TimeBlockEntity>,
    zone: ZoneId,
    onCellClick: (List<TimeBlockEntity>) -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Spacer(Modifier.width(32.dp))
            days.forEach { date ->
                Column(
                    modifier = Modifier.weight(1f),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(DAY_LABELS[date.dayOfWeek.value - 1], fontSize = 10.sp, color = ChatColors.OnSurface, fontWeight = FontWeight.SemiBold)
                    Text("${date.monthValue}/${date.dayOfMonth}", fontSize = 9.sp, color = ChatColors.TextSecondary)
                }
            }
        }
        Spacer(Modifier.height(5.dp))
        (0..23).forEach { hour ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Text(
                    text = "%02d".format(hour),
                    modifier = Modifier.width(30.dp),
                    textAlign = TextAlign.End,
                    color = ChatColors.TextSecondary,
                    fontSize = 9.sp
                )
                days.forEach { date ->
                    val cellStart = date.atStartOfDay(zone).plusHours(hour.toLong()).toInstant().toEpochMilli()
                    val cellEnd = date.atStartOfDay(zone).plusHours(hour + 1L).toInstant().toEpochMilli()
                    val cellBlocks = if (cellEnd > cellStart) {
                        blocks.filter { block ->
                            block.startedAt < cellEnd && (block.endedAt ?: 0L) > cellStart
                        }
                    } else {
                        emptyList()
                    }
                    val roles = cellBlocks.map { it.creatorRole }.distinct()
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(18.dp)
                            .clip(RoundedCornerShape(3.dp))
                            .background(Color(0xFFE9EEEB))
                            .clickable(enabled = cellBlocks.isNotEmpty()) { onCellClick(cellBlocks) }
                    ) {
                        if (roles.size > 1) {
                            Row(Modifier.fillMaxSize()) {
                                roles.forEach { role ->
                                    Box(
                                        Modifier
                                            .weight(1f)
                                            .fillMaxSize()
                                            .background(colorForRole(role))
                                    )
                                }
                            }
                        } else if (roles.isNotEmpty()) {
                            Box(Modifier.fillMaxSize().background(colorForRole(roles.first())))
                        }
                    }
                }
            }
            Spacer(Modifier.height(2.dp))
        }
    }
}

@Composable
private fun LegendDot(color: Color) {
    Box(
        Modifier
            .padding(end = 4.dp)
            .size(9.dp)
            .clip(RoundedCornerShape(2.dp))
            .background(color)
    )
}

private fun startOfWeek(date: LocalDate): LocalDate =
    date.with(java.time.temporal.TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))

private fun formatWeek(start: LocalDate, end: LocalDate): String =
    "${start.monthValue}月${start.dayOfMonth}日 - ${end.monthValue}月${end.dayOfMonth}日"

private fun formatDateTime(millis: Long, zone: ZoneId): String =
    DateTimeFormatter.ofPattern("M月d日 HH:mm", Locale.CHINA)
        .withZone(zone)
        .format(Instant.ofEpochMilli(millis))

private fun formatDuration(durationMs: Long): String {
    val totalMinutes = durationMs.coerceAtLeast(0L) / 60_000L
    val hours = totalMinutes / 60L
    val minutes = totalMinutes % 60L
    return when {
        hours > 0 && minutes > 0 -> "${hours}小时${minutes}分"
        hours > 0 -> "${hours}小时"
        totalMinutes > 0 -> "${minutes}分钟"
        else -> "不足1分钟"
    }
}

private fun durationForDate(blocks: List<TimeBlockEntity>, date: LocalDate, zone: ZoneId): Long {
    val start = date.atStartOfDay(zone).toInstant().toEpochMilli()
    val end = date.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
    return blocks.sumOf { block ->
        val blockEnd = block.endedAt ?: return@sumOf 0L
        (minOf(blockEnd, end) - maxOf(block.startedAt, start)).coerceAtLeast(0L)
    }
}

private fun colorForRole(role: String): Color =
    if (role == "A") Color(0xFFFF8A64) else Color(0xFF44A58A)

private val DAY_LABELS = listOf("周一", "周二", "周三", "周四", "周五", "周六", "周日")
