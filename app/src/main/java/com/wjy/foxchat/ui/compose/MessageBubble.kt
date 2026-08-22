package com.wjy.foxchat.ui.compose

import android.graphics.BitmapFactory
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.wjy.foxchat.model.Message
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val AvatarTotalWidth = 40.dp // 32dp 头像 + 8dp 间距

/**
 * 单个消息气泡。头像与气泡底部对齐，时间显示在气泡下方。
 *
 * @param onLongPressReport 长按时上报消息及其在窗口中的位置，用于定位悬浮操作菜单
 * @param onEditRecalled 点击“重新编辑”（仅自己撤回且保留原文的文本消息）
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun MessageBubble(
    message: Message,
    quoted: Message?,
    myAvatar: String?,
    partnerAvatar: String?,
    checkinReplies: List<Message>,
    currentRole: String,
    onLongPressReport: (Message, Offset) -> Unit,
    onImageClick: (Message) -> Unit,
    onAudioClick: (Message) -> Unit,
    onEditRecalled: (Message) -> Unit,
    onCheckinReply: (Message) -> Unit,
    modifier: Modifier = Modifier
) {
    val mine = message.isMine
    var bubblePosition by remember { mutableStateOf(Offset.Zero) }

    if (message.isRecalled) {
        RecalledNotice(
            message = message,
            modifier = modifier
                .fillMaxWidth()
                .onGloballyPositioned { coords -> bubblePosition = coords.positionInWindow() }
                .combinedClickable(
                    enabled = true,
                    onClick = {},
                    onLongClick = { onLongPressReport(message, bubblePosition) }
                ),
            onEditRecalled = onEditRecalled
        )
        return
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 5.dp)
            .onGloballyPositioned { coords -> bubblePosition = coords.positionInWindow() }
            .combinedClickable(
                enabled = true,
                onClick = {},
                onLongClick = { onLongPressReport(message, bubblePosition) }
            ),
        horizontalAlignment = if (mine) Alignment.End else Alignment.Start
    ) {
        // 头像 + 气泡（底部对齐）
        Row(verticalAlignment = Alignment.Bottom) {
            if (!mine) {
                AvatarBadge(
                    path = partnerAvatar,
                    size = 32.dp,
                    modifier = Modifier.padding(end = 8.dp)
                )
            }
            if (message.isCheckin) {
                CheckinCard(
                    card = message,
                    replies = checkinReplies,
                    currentRole = currentRole,
                    onCheckinReply = onCheckinReply
                )
            } else {
                BubbleContent(
                    message = message,
                    quoted = quoted,
                    mine = mine,
                    onImageClick = onImageClick,
                    onAudioClick = onAudioClick
                )
            }
            if (mine) {
                AvatarBadge(
                    path = myAvatar,
                    size = 32.dp,
                    modifier = Modifier.padding(start = 8.dp)
                )
            }
        }
        // 时间 / 状态（气泡下方，缩进对齐到气泡）
        Row(
            modifier = Modifier
                .padding(top = 3.dp)
                .then(
                    if (mine) Modifier.padding(end = AvatarTotalWidth)
                    else Modifier.padding(start = AvatarTotalWidth)
                ),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (mine) {
                Text(
                    text = message.deliveryStatus,
                    color = ChatColors.TextSecondary,
                    fontSize = 10.sp,
                    modifier = Modifier.padding(end = 5.dp)
                )
            }
            Text(
                text = formatTime(message.timestamp),
                color = ChatColors.TextSecondary,
                fontSize = 10.sp
            )
        }
    }
}

@Composable
private fun BubbleContent(
    message: Message,
    quoted: Message?,
    mine: Boolean,
    onImageClick: (Message) -> Unit,
    onAudioClick: (Message) -> Unit
) {
    val bubbleColor = if (mine) ChatColors.BubbleUser else ChatColors.BubbleAI
    val contentColor = if (mine) Color.White else ChatColors.OnSurface

    Column(
        modifier = Modifier.widthIn(max = 280.dp),
        horizontalAlignment = if (mine) Alignment.End else Alignment.Start
    ) {
        // 引用（仅当原消息存在时显示）
        if (quoted != null && message.replyToMessageId != null) {
            QuotedBlock(
                quoted = quoted,
                mine = mine,
                modifier = Modifier.padding(bottom = 4.dp)
            )
        }

        // 图片
        if (message.isImage) {
            val path = message.mediaPath
            if (!path.isNullOrBlank()) {
                val bitmap = remember(path) {
                    runCatching { BitmapFactory.decodeFile(pathToFile(path).absolutePath) }.getOrNull()
                }
                if (bitmap != null) {
                    Image(
                        painter = BitmapPainter(bitmap.asImageBitmap()),
                        contentDescription = null,
                        modifier = Modifier
                            .width(220.dp)
                            .height(180.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(bubbleColor)
                            .clickable { onImageClick(message) }
                    )
                }
            }
        }

        // 语音
        if (message.isAudio) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .clip(RoundedCornerShape(16.dp))
                    .background(bubbleColor)
                    .padding(horizontal = 16.dp, vertical = 12.dp)
                    .clickable { onAudioClick(message) }
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.VolumeUp,
                    contentDescription = null,
                    tint = contentColor,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = "语音 ${(message.mediaDurationMs / 1000).coerceAtLeast(1)}s",
                    color = contentColor,
                    fontSize = 15.sp
                )
            }
        }

        // 文本
        if (!message.isImage && !message.isAudio) {
            if (message.isCheckinReply) {
                Column(
                    modifier = Modifier
                        .clip(RoundedCornerShape(16.dp))
                        .background(bubbleColor)
                        .padding(horizontal = 14.dp, vertical = 10.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Filled.CheckCircle,
                            contentDescription = null,
                            tint = if (mine) Color.White else ChatColors.FoxOrange,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(Modifier.width(4.dp))
                        Text(
                            text = "打卡",
                            color = if (mine) Color.White else ChatColors.FoxOrange,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = message.content,
                        color = contentColor,
                        fontSize = 15.sp,
                        lineHeight = 21.sp
                    )
                }
            } else {
                Text(
                    text = message.content,
                    color = contentColor,
                    fontSize = 15.sp,
                    lineHeight = 21.sp,
                    modifier = Modifier
                        .clip(RoundedCornerShape(16.dp))
                        .background(bubbleColor)
                        .padding(horizontal = 14.dp, vertical = 10.dp)
                )
            }
        }
    }
}

/**
 * 引用预览块：淡显示、固定样式（统一圆角与内边距）。
 */
@Composable
private fun QuotedBlock(quoted: Message, mine: Boolean, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .widthIn(max = 260.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(if (mine) ChatColors.QuotedUser else Color(0xFFF1F4F2))
            .padding(horizontal = 10.dp, vertical = 7.dp)
    ) {
        Text(
            text = quotedPreview(quoted),
            color = (if (mine) ChatColors.QuotedUserText else ChatColors.TextSecondary)
                .copy(alpha = 0.75f),
            fontSize = 12.sp,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
    }
}

/**
 * 已撤回消息：居中显示“消息已撤回”，自己撤回且保留原文时可“重新编辑”。
 */
@Composable
private fun RecalledNotice(
    message: Message,
    modifier: Modifier = Modifier,
    onEditRecalled: (Message) -> Unit
) {
    Row(
        modifier = modifier.padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "消息已撤回",
            color = ChatColors.TextSecondary.copy(alpha = 0.85f),
            fontSize = 12.sp
        )
        if (message.isMine && !message.recalledText.isNullOrBlank()) {
            Spacer(Modifier.width(10.dp))
            Text(
                text = "重新编辑",
                color = ChatColors.FoxOrange,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.clickable { onEditRecalled(message) }
            )
        }
    }
}

private fun quotedPreview(message: Message): String = when {
    message.isRecalled -> "消息已撤回"
    message.isImage -> "[图片]"
    message.isAudio -> "[语音]"
    message.content.isNotBlank() -> message.content
    else -> "消息"
}

private fun formatTime(timestamp: Long): String =
    SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(timestamp))

private fun pathToFile(path: String): File {
    val parsed = android.net.Uri.parse(path)
    return if (parsed.scheme.isNullOrBlank()) File(path) else File(parsed.path ?: path)
}

/**
 * 打卡卡片：展示主题、双方打卡状态，以及「打卡」按钮。
 * 点击「打卡」后进入打卡回复状态（复用引用机制）。
 */
@Composable
private fun CheckinCard(
    card: Message,
    replies: List<Message>,
    currentRole: String,
    onCheckinReply: (Message) -> Unit
) {
    val isInitiator = card.senderRole == currentRole
    val hasCheckedIn = replies.any { it.senderRole == currentRole }
    val partnerCheckedIn = replies.isNotEmpty()

    Column(
        modifier = Modifier
            .widthIn(max = 260.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(ChatColors.Surface)
            .border(1.dp, ChatColors.ChatLine, RoundedCornerShape(16.dp))
            .padding(16.dp)
    ) {
        // 顶部：打卡徽标 + 发起人
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = Icons.Filled.CheckCircle,
                contentDescription = null,
                tint = ChatColors.FoxOrange,
                modifier = Modifier.size(18.dp)
            )
            Spacer(Modifier.width(6.dp))
            Text(
                text = "打卡",
                color = ChatColors.FoxOrange,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.weight(1f))
            Text(
                text = "发起人 ${card.senderRole}",
                color = ChatColors.TextSecondary,
                fontSize = 11.sp
            )
        }
        Spacer(Modifier.height(10.dp))
        // 主题
        Text(
            text = card.content,
            color = ChatColors.OnSurface,
            fontSize = 16.sp,
            fontWeight = FontWeight.SemiBold,
            lineHeight = 22.sp
        )
        // 状态区：已打卡成员
        if (replies.isNotEmpty()) {
            Spacer(Modifier.height(10.dp))
            HorizontalDivider(color = ChatColors.ChatLine)
            Spacer(Modifier.height(8.dp))
            replies.forEach { reply ->
                Row(
                    verticalAlignment = Alignment.Top,
                    modifier = Modifier.padding(vertical = 3.dp)
                ) {
                    Text(
                        text = "${reply.senderRole} 已打卡",
                        color = ChatColors.SyncGreen,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(end = 6.dp)
                    )
                    Text(
                        text = reply.content,
                        color = ChatColors.OnSurface,
                        fontSize = 13.sp,
                        lineHeight = 18.sp,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
        // 底部：发起人看状态、对方看打卡按钮
        Spacer(Modifier.height(12.dp))
        if (isInitiator) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(
                        if (partnerCheckedIn) ChatColors.SyncSurface else Color(0xFFF1F4F2)
                    )
                    .padding(vertical = 9.dp),
                horizontalArrangement = Arrangement.Center
            ) {
                if (partnerCheckedIn) {
                    Icon(
                        imageVector = Icons.Filled.CheckCircle,
                        contentDescription = null,
                        tint = ChatColors.SyncGreen,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = "对方已打卡",
                        color = ChatColors.SyncGreen,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold
                    )
                } else {
                    Icon(
                        imageVector = Icons.Filled.Schedule,
                        contentDescription = null,
                        tint = ChatColors.TextSecondary,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = "等待对方打卡",
                        color = ChatColors.TextSecondary,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        } else if (hasCheckedIn) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(ChatColors.SyncSurface)
                    .padding(vertical = 9.dp),
                horizontalArrangement = Arrangement.Center
            ) {
                Icon(
                    imageVector = Icons.Filled.CheckCircle,
                    contentDescription = null,
                    tint = ChatColors.SyncGreen,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    text = "打卡成功",
                    color = ChatColors.SyncGreen,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        } else {
            Button(
                onClick = { onCheckinReply(card) },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = ChatColors.FoxOrange),
                shape = RoundedCornerShape(10.dp)
            ) {
                Icon(
                    imageVector = Icons.Filled.CheckCircle,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    text = "打卡",
                    color = Color.White,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}
