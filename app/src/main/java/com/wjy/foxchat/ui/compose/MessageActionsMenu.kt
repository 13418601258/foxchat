package com.wjy.foxchat.ui.compose

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Reply
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import com.wjy.foxchat.model.Message

/**
 * 长按消息后显示在消息附近的紧凑悬浮操作菜单（不跳转页面、不使用 AlertDialog）。
 *
 * @param anchor 消息在窗口中的位置，用于将菜单定位在消息附近
 * @param canRecall 是否显示“撤回”（当前用户发送且未撤回）
 */
@Composable
fun MessageActionsMenu(
    anchor: Offset,
    message: Message,
    canRecall: Boolean,
    onReply: (Message) -> Unit,
    onCopy: (Message) -> Unit,
    onRecall: (Message) -> Unit,
    onDelete: (Message) -> Unit,
    onDismiss: () -> Unit
) {
    Popup(
        alignment = Alignment.TopStart,
        offset = IntOffset(anchor.x.toInt(), anchor.y.toInt()),
        onDismissRequest = onDismiss,
        properties = PopupProperties(focusable = true)
    ) {
        Surface(
            shape = RoundedCornerShape(10.dp),
            color = ChatColors.Surface,
            shadowElevation = 6.dp
        ) {
            Column(modifier = Modifier.padding(vertical = 4.dp)) {
                val showRecall = canRecall && !message.isRecalled
                MenuItem(Icons.AutoMirrored.Filled.Reply, "引用") {
                    onReply(message); onDismiss()
                }
                MenuItem(Icons.Filled.ContentCopy, "复制") {
                    onCopy(message); onDismiss()
                }
                if (showRecall) {
                    MenuItem(Icons.AutoMirrored.Filled.Undo, "撤回", tint = ChatColors.FoxOrange) {
                        onRecall(message); onDismiss()
                    }
                }
                MenuItem(Icons.Filled.Delete, "删除", tint = ChatColors.FoxOrangeDeep) {
                    onDelete(message); onDismiss()
                }
            }
        }
    }
}

@Composable
private fun MenuItem(icon: ImageVector, label: String, tint: Color = ChatColors.OnSurface, onClick: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 9.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = tint,
            modifier = Modifier.size(16.dp)
        )
        Spacer(Modifier.width(10.dp))
        Text(
            text = label,
            color = tint,
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium
        )
    }
}
