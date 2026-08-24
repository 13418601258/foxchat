package com.wjy.foxchat.ui.compose

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Alarm
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Pets
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.rememberDrawerState
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import android.graphics.BitmapFactory
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import com.wjy.foxchat.R
import com.wjy.foxchat.model.Message
import kotlinx.coroutines.launch
import androidx.compose.ui.graphics.vector.ImageVector

/**
 * 主聊天页 Compose 实现。业务逻辑（发送、媒体、录音、同步）由 ChatActivity
 * 通过回调提供，本组件只负责界面渲染与 UI 状态。
 */
@Composable
fun ChatScreen(
    messages: List<Message>,
    backgroundPath: String?,
    replyToMessage: Message?,
    myAvatarPath: String?,
    partnerAvatarPath: String?,
    syncStatus: String,
    currentRole: String,
    isRecording: Boolean,
    inlineStatus: String?,
    onSendText: (String) -> Unit,
    onMoreClick: () -> Unit,
    onSidebarAction: (String) -> Unit,
    onReply: (Message) -> Unit,
    onCopy: (Message) -> Unit,
    onRecall: (Message) -> Unit,
    onDelete: (Message) -> Unit,
    onImageClick: (Message) -> Unit,
    onAudioClick: (Message) -> Unit,
    onClearReply: () -> Unit,
    onStartRecording: () -> Unit,
    onStopRecording: (send: Boolean) -> Unit,
    onCamera: () -> Unit,
    onGallery: () -> Unit,
    onBackground: () -> Unit
) {
    var inputText by rememberSaveable { mutableStateOf("") }
    var showMessageMenu by remember { mutableStateOf(false) }
    var selectedMessage by remember { mutableStateOf<Message?>(null) }
    var menuAnchor by remember { mutableStateOf(Offset.Zero) }
    var showToolsSheet by remember { mutableStateOf(false) }
    val listState = rememberLazyListState()
    val keyboardController = LocalSoftwareKeyboardController.current
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    var isInitialPosition by remember { mutableStateOf(true) }

    // 首次进入直接定位到最新（非动画，不移动屏幕）；之后新消息才动画滚动到底部
    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) {
            if (isInitialPosition) {
                listState.scrollToItem(messages.lastIndex)
                isInitialPosition = false
            } else {
                listState.animateScrollToItem(messages.lastIndex)
            }
        }
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            SidebarContent(
                onSidebarAction = { action ->
                    scope.launch { drawerState.close() }
                    onSidebarAction(action)
                }
            )
        }
    ) {
        Box(
            modifier = Modifier.fillMaxSize()
        ) {
            // 自定义聊天背景（若有）
        val bgBitmap = remember(backgroundPath) {
            backgroundPath?.let {
                runCatching {
                    BitmapFactory.decodeFile(it)?.asImageBitmap()
                }.getOrNull()
            }
        }
        if (bgBitmap != null) {
            Image(
                painter = BitmapPainter(bgBitmap),
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                alpha = 0.25f,
                contentScale = ContentScale.Crop
            )
        } else {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(ChatColors.Background)
            )
        }
        Column(modifier = Modifier.fillMaxSize()) {
            TopBar(
                syncStatus = syncStatus,
                onMoreClick = onMoreClick,
                onOpenSidebar = { scope.launch { drawerState.open() } }
            )
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp)
                    .pointerInput(Unit) {
                        detectTapGestures { keyboardController?.hide() }
                    },
                contentPadding = androidx.compose.foundation.layout.PaddingValues(
                    top = 14.dp,
                    bottom = 18.dp
                )
            ) {
                items(messages.size) { index ->
                    val message = messages[index]
                    val quoted = message.replyToMessageId
                        ?.let { replyId -> messages.firstOrNull { it.id == replyId } }
                    val checkinReplies = if (message.isCheckin) {
                        messages.filter {
                            it.replyToMessageId == message.id && it.type == Message.TYPE_CHECKIN_REPLY
                        }
                    } else {
                        emptyList()
                    }
                    MessageBubble(
                        message = message,
                        quoted = quoted,
                        myAvatar = myAvatarPath,
                        partnerAvatar = partnerAvatarPath,
                        checkinReplies = checkinReplies,
                        currentRole = currentRole,
                        onLongPressReport = { msg, offset ->
                            selectedMessage = msg
                            menuAnchor = offset
                            showMessageMenu = true
                        },
                        onImageClick = onImageClick,
                        onAudioClick = onAudioClick,
                        onEditRecalled = { msg ->
                            inputText = msg.recalledText.orEmpty()
                        },
                        onCheckinReply = onReply
                    )
                }
            }
            InlineStatus(inlineStatus)
            InputBar(
                inputText = inputText,
                replyToMessage = replyToMessage,
                onInputChange = { inputText = it },
                isRecording = isRecording,
                onSend = {
                    if (inputText.isNotBlank()) {
                        onSendText(inputText.trim())
                        inputText = ""
                    }
                },
                onToolsClick = { showToolsSheet = true },
                onStartRecording = {
                    onStartRecording()
                },
                onStopRecording = { send -> onStopRecording(send) },
                onClearReply = onClearReply
            )
        }

        // 消息长按悬浮菜单
        selectedMessage?.let { message ->
            if (showMessageMenu) {
                MessageActionsMenu(
                    anchor = menuAnchor,
                    message = message,
                    canRecall = message.isMine,
                    onReply = { onReply(message) },
                    onCopy = { onCopy(message) },
                    onRecall = { onRecall(message) },
                    onDelete = { onDelete(message) },
                    onDismiss = { showMessageMenu = false }
                )
            }
        }

        // 聊天工具底部面板
        if (showToolsSheet) {
            ChatToolsSheet(
                onDismiss = { showToolsSheet = false },
                onCamera = {
                    showToolsSheet = false
                    onCamera()
                },
                onGallery = {
                    showToolsSheet = false
                    onGallery()
                },
                onBackground = {
                    showToolsSheet = false
                    onBackground()
                }
            )
        }
        }
    }
}

@Composable
private fun TopBar(
    syncStatus: String,
    onMoreClick: () -> Unit,
    onOpenSidebar: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(ChatColors.Surface)
            .padding(horizontal = 8.dp, vertical = 8.dp)
            .height(68.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = onOpenSidebar) {
            Icon(
                imageVector = Icons.Filled.Menu,
                contentDescription = "侧边栏",
                tint = ChatColors.OnSurface
            )
        }
        Image(
            painter = painterResource(R.mipmap.ic_launcher),
            contentDescription = null,
            modifier = Modifier.size(40.dp)
        )
        Spacer(Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "FoxChat",
                color = ChatColors.OnSurface,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "在线",
                color = ChatColors.TextSecondary,
                fontSize = 11.sp
            )
        }
        Text(
            text = syncStatus,
            color = ChatColors.SyncGreen,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier
                .clip(RoundedCornerShape(14.dp))
                .background(ChatColors.SyncSurface)
                .padding(horizontal = 9.dp, vertical = 5.dp)
        )
        IconButton(onClick = onMoreClick) {
            Icon(
                imageVector = Icons.Filled.MoreVert,
                contentDescription = null,
                tint = ChatColors.OnSurface
            )
        }
    }
}

@Composable
private fun ReplyBar(replyToMessage: Message?, onClearReply: () -> Unit) {
    if (replyToMessage == null) return
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp, vertical = 6.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(ChatColors.Background)
            .padding(horizontal = 12.dp, vertical = 6.dp)
    ) {
        Box(
            modifier = Modifier
                .width(3.dp)
                .height(16.dp)
                .background(ChatColors.FoxOrange, RoundedCornerShape(2.dp))
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text = replyPreview(replyToMessage),
            color = ChatColors.TextSecondary,
            fontSize = 12.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
        IconButton(onClick = onClearReply, modifier = Modifier.size(28.dp)) {
            Icon(
                imageVector = Icons.Filled.Add,
                contentDescription = "取消引用",
                tint = ChatColors.TextSecondary,
                modifier = Modifier.rotate(45f)
            )
        }
    }
}

@Composable
private fun InlineStatus(status: String?) {
    if (status == null) return
    Text(
        text = status,
        color = ChatColors.TextSecondary,
        fontSize = 12.sp,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp)
    )
}

@Composable
private fun InputBar(
    inputText: String,
    replyToMessage: Message?,
    onInputChange: (String) -> Unit,
    isRecording: Boolean,
    onSend: () -> Unit,
    onToolsClick: () -> Unit,
    onStartRecording: () -> Unit,
    onStopRecording: (Boolean) -> Unit,
    onClearReply: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(ChatColors.Surface)
            .navigationBarsPadding()
            .imePadding()
    ) {
        InputRow(
            inputText = inputText,
            onInputChange = onInputChange,
            isRecording = isRecording,
            onSend = onSend,
            onToolsClick = onToolsClick,
            onStartRecording = onStartRecording,
            onStopRecording = onStopRecording
        )
        ReplyBar(replyToMessage, onClearReply)
    }
}

@Composable
private fun InputRow(
    inputText: String,
    onInputChange: (String) -> Unit,
    isRecording: Boolean,
    onSend: () -> Unit,
    onToolsClick: () -> Unit,
    onStartRecording: () -> Unit,
    onStopRecording: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = onToolsClick) {
            Icon(
                imageVector = Icons.Filled.Add,
                contentDescription = "聊天工具",
                tint = ChatColors.FoxOrange
            )
        }
        OutlinedTextField(
            value = inputText,
            onValueChange = onInputChange,
            placeholder = { Text("输入消息…", color = ChatColors.TextSecondary, fontSize = 14.sp) },
            modifier = Modifier
                .weight(1f)
                .clip(RoundedCornerShape(20.dp)),
            shape = RoundedCornerShape(20.dp),
            maxLines = 4,
            colors = TextFieldDefaults.colors(
                focusedContainerColor = Color(0xFFF8FAF9),
                unfocusedContainerColor = Color(0xFFF8FAF9),
                focusedIndicatorColor = Color.Transparent,
                unfocusedIndicatorColor = Color.Transparent
            ),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
            keyboardActions = KeyboardActions(onSend = { onSend() })
        )
        Spacer(Modifier.width(6.dp))
        if (inputText.isBlank()) {
            // 按住录音
            IconButton(
                onClick = {},
                modifier = Modifier
                    .size(40.dp)
                    .background(
                        color = if (isRecording) ChatColors.FoxOrange.copy(alpha = 0.15f)
                        else Color.Transparent,
                        shape = RoundedCornerShape(20.dp)
                    )
                    .pointerInput(Unit) {
                        detectTapGestures(
                            onPress = {
                                onStartRecording()
                                try {
                                    tryAwaitRelease()
                                    onStopRecording(true)
                                } catch (_: kotlinx.coroutines.CancellationException) {
                                    onStopRecording(false)
                                }
                            }
                        )
                    }
            ) {
                Icon(
                    imageVector = Icons.Filled.Mic,
                    contentDescription = "按住录音",
                    tint = if (isRecording) ChatColors.FoxOrangeDeep else ChatColors.FoxOrange
                )
            }
        } else {
            IconButton(
                onClick = onSend,
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(20.dp))
                    .background(ChatColors.FoxOrange)
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.Send,
                    contentDescription = "发送",
                    tint = Color.White
                )
            }
        }
    }
}

private fun replyPreview(message: Message): String = when {
    message.isCheckin -> "打卡：${message.content}"
    message.isRecalled -> "引用：消息已撤回"
    message.isImage -> "引用：图片消息"
    message.isAudio -> "引用：语音消息"
    message.content.isNotBlank() -> "引用：${message.content}"
    else -> "引用：消息"
}

@Composable
private fun SidebarContent(onSidebarAction: (String) -> Unit) {
    ModalDrawerSheet(
        modifier = Modifier.width(280.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 24.dp)
        ) {
            Image(
                painter = painterResource(R.mipmap.ic_launcher),
                contentDescription = null,
                modifier = Modifier.size(44.dp)
            )
            Spacer(Modifier.height(12.dp))
            Text(
                text = "FoxChat",
                color = ChatColors.OnSurface,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "一对一聊天",
                color = ChatColors.TextSecondary,
                fontSize = 12.sp
            )
        }
        HorizontalDivider(color = ChatColors.ChatLine)
        SidebarItem("宠物", Icons.Filled.Pets, "pet", onSidebarAction)
        SidebarItem("打卡", Icons.Filled.CheckCircle, "checkin", onSidebarAction)
        SidebarItem("定时通知", Icons.Filled.Alarm, "scheduled_notification", onSidebarAction)
    }
}

@Composable
private fun SidebarItem(
    label: String,
    icon: ImageVector,
    action: String,
    onAction: (String) -> Unit
) {
    NavigationDrawerItem(
        label = { Text(label, fontSize = 15.sp) },
        icon = { Icon(icon, contentDescription = label, tint = ChatColors.OnSurface) },
        selected = false,
        onClick = { onAction(action) },
        modifier = Modifier.padding(horizontal = 12.dp)
    )
}

