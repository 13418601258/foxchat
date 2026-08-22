package com.wjy.foxchat.ui

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.lifecycleScope
import com.wjy.foxchat.data.repository.ChatRepository
import com.wjy.foxchat.ui.compose.ChatColors
import com.wjy.foxchat.ui.compose.ChatTheme
import kotlinx.coroutines.launch

class CheckinCreateActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val repository = ChatRepository.get(this)
        setContent {
            ChatTheme {
                CheckinCreateScreen(
                    onBack = { finish() },
                    onSend = { title ->
                        lifecycleScope.launch {
                            repository.sendCheckin(title)
                            repository.syncNow()
                            finish()
                        }
                    }
                )
            }
        }
    }

    companion object {
        fun newIntent(context: Context): Intent =
            Intent(context, CheckinCreateActivity::class.java)
    }
}

@Composable
private fun CheckinCreateScreen(
    onBack: () -> Unit,
    onSend: (String) -> Unit
) {
    var title by rememberSaveable { mutableStateOf("") }
    var error by rememberSaveable { mutableStateOf<String?>(null) }

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
                text = "发起打卡",
                color = ChatColors.OnSurface,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold
            )
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .background(ChatColors.Background)
                .padding(20.dp)
        ) {
            Text(
                text = "打卡主题",
                color = ChatColors.TextSecondary,
                fontSize = 13.sp,
                modifier = Modifier.padding(bottom = 8.dp)
            )
            OutlinedTextField(
                value = title,
                onValueChange = { title = it },
                placeholder = { Text("输入打卡主题，如：今天读书了吗？", color = ChatColors.TextSecondary) },
                modifier = Modifier.fillMaxWidth(),
                maxLines = 4,
                shape = RoundedCornerShape(12.dp),
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = Color.White,
                    unfocusedContainerColor = Color.White,
                    focusedIndicatorColor = ChatColors.FoxOrange,
                    unfocusedIndicatorColor = Color.Transparent
                )
            )

            Spacer(Modifier.height(24.dp))
            Text(
                text = "卡片预览",
                color = ChatColors.TextSecondary,
                fontSize = 13.sp,
                modifier = Modifier.padding(bottom = 8.dp)
            )
            // 实时预览卡片
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(ChatColors.Surface)
                    .border(1.dp, ChatColors.ChatLine, RoundedCornerShape(16.dp))
                    .padding(16.dp)
            ) {
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
                }
                Spacer(Modifier.height(10.dp))
                Text(
                    text = title.ifBlank { "打卡主题预览" },
                    color = if (title.isBlank()) ChatColors.TextSecondary else ChatColors.OnSurface,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                    lineHeight = 22.sp
                )
            }

            if (error != null) {
                Spacer(Modifier.height(12.dp))
                Text(
                    text = error.orEmpty(),
                    color = ChatColors.FoxOrangeDeep,
                    fontSize = 13.sp
                )
            }
        }

        // 底部发送按钮
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(ChatColors.Surface)
                .padding(20.dp)
        ) {
            Button(
                onClick = {
                    if (title.isBlank()) {
                        error = "请输入打卡主题"
                    } else {
                        onSend(title.trim())
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = ChatColors.FoxOrange),
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(
                    imageVector = Icons.Filled.CheckCircle,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    text = "发送打卡",
                    color = Color.White,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}
