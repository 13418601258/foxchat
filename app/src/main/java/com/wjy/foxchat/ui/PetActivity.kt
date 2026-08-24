package com.wjy.foxchat.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Minimize
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.ImageLoader
import coil.compose.AsyncImage
import coil.decode.GifDecoder
import coil.decode.ImageDecoderDecoder
import com.wjy.foxchat.R
import com.wjy.foxchat.data.local.FoxChatDatabase
import com.wjy.foxchat.data.local.PetEntity
import com.wjy.foxchat.data.pet.PetManager
import com.wjy.foxchat.pet.PetFloatService
import com.wjy.foxchat.ui.compose.ChatColors
import com.wjy.foxchat.ui.compose.ChatTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class PetActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            ChatTheme {
                PetScreen(
                    onBack = { finish() },
                    onMinimize = ::minimizePet
                )
            }
        }
    }

    private fun minimizePet() {
        if (Settings.canDrawOverlays(this)) {
            PetFloatService.start(this)
            finish()
        } else {
            Toast.makeText(this, "请开启「显示在其他应用上层」权限", Toast.LENGTH_LONG).show()
            startActivity(
                Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName"))
            )
        }
    }

    companion object {
        fun newIntent(context: Context): Intent = Intent(context, PetActivity::class.java)
    }
}

@Composable
private fun PetScreen(
    onBack: () -> Unit,
    onMinimize: () -> Unit
) {
    val context = LocalContext.current
    val db = remember { FoxChatDatabase.get(context) }
    val scope = rememberCoroutineScope()
    var pet by remember { mutableStateOf<PetEntity?>(null) }
    var isFeeding by remember { mutableStateOf(false) }
    var bubble by remember { mutableStateOf("") }
    // 显式启用 GIF 解码器，确保动图播放
    val imageLoader = remember(context) {
        ImageLoader.Builder(context)
            .components {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    add(ImageDecoderDecoder.Factory())
                } else {
                    add(GifDecoder.Factory())
                }
            }
            .build()
    }

    // 加载并结算宠物状态（真实时间驱动）
    LaunchedEffect(Unit) {
        val loaded = db.petDao().get()
        val settled = if (loaded == null) PetEntity() else PetManager.settle(loaded)
        db.petDao().upsert(settled)
        pet = settled
        bubble = statusBubble(settled)
    }

    // 喂食动画 3 秒后恢复待机
    LaunchedEffect(isFeeding) {
        if (isFeeding) {
            delay(3000)
            isFeeding = false
        }
    }

    pet?.let { current ->
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
                    text = "我的宠物",
                    color = ChatColors.OnSurface,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(Modifier.weight(1f))
                IconButton(onClick = onMinimize) {
                    Icon(
                        imageVector = Icons.Filled.Minimize,
                        contentDescription = "最小化到悬浮窗",
                        tint = ChatColors.FoxOrange
                    )
                }
            }

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .background(ChatColors.Background)
                    .padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Spacer(Modifier.height(20.dp))
                // 宠物形象
                Box(
                    modifier = Modifier
                        .size(230.dp)
                        .background(Color.White, RoundedCornerShape(24.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    AsyncImage(
                        model = if (isFeeding) R.drawable.pet_feed else R.drawable.pet_sleep,
                        contentDescription = "宠物",
                        imageLoader = imageLoader,
                        modifier = Modifier.size(210.dp)
                    )
                }
                // 状态气泡
                if (bubble.isNotBlank()) {
                    Spacer(Modifier.height(10.dp))
                    Text(
                        text = bubble,
                        color = ChatColors.TextSecondary,
                        fontSize = 13.sp
                    )
                }

                Spacer(Modifier.height(24.dp))
                // 状态条
                StatBar("食物", current.food, PetManager.MAX_STAT, ChatColors.FoxOrange)
                StatBar("喝水", current.drink, PetManager.MAX_STAT, ChatColors.FoxOrangeDeep)
                StatBar("健康", current.condition, PetManager.MAX_STAT.toDouble(), ChatColors.SyncGreen)
                StatBar("亲密度", current.love, PetManager.MAX_LOVE, ChatColors.FoxOrange)

                Spacer(Modifier.height(20.dp))
                // 互动按钮
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Button(
                        onClick = {
                            isFeeding = true
                            bubble = "谢谢你喂我，真好吃！"
                            scope.launch {
                                val next = current.let { PetManager.feed(it) }
                                db.petDao().upsert(next)
                                pet = next
                            }
                        },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(containerColor = ChatColors.FoxOrange),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(Icons.Filled.Restaurant, contentDescription = null, tint = Color.White)
                        Spacer(Modifier.width(6.dp))
                        Text("喂食", color = Color.White)
                    }
                    Button(
                        onClick = {
                            bubble = "嘻嘻，和你玩最开心！"
                            scope.launch {
                                val next = current.let { PetManager.play(it) }
                                db.petDao().upsert(next)
                                pet = next
                            }
                        },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(containerColor = ChatColors.FoxOrangeDeep),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(Icons.Filled.Favorite, contentDescription = null, tint = Color.White)
                        Spacer(Modifier.width(6.dp))
                        Text("玩耍", color = Color.White)
                    }
                }

                Spacer(Modifier.height(16.dp))
                Text(
                    text = "已陪伴你 ${current.days} 天",
                    color = ChatColors.TextSecondary,
                    fontSize = 12.sp
                )
            }
        }
    }
}

@Composable
private fun StatBar(label: String, value: Int, max: Int, color: Color) {
    val fraction = (value.toFloat() / max.toFloat()).coerceIn(0f, 1f)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            color = ChatColors.TextSecondary,
            fontSize = 13.sp,
            modifier = Modifier.width(56.dp)
        )
        Box(
            modifier = Modifier
                .weight(1f)
                .height(12.dp)
                .background(Color(0xFFE6E6E6), RoundedCornerShape(6.dp))
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(fraction)
                    .height(12.dp)
                    .background(color, RoundedCornerShape(6.dp))
            )
        }
        Text(
            text = "$value/$max",
            color = ChatColors.TextSecondary,
            fontSize = 12.sp,
            modifier = Modifier.width(56.dp)
        )
    }
}

@Composable
private fun StatBar(label: String, value: Double, max: Double, color: Color) {
    val v = value.coerceIn(0.0, max)
    StatBar(label, v.toInt(), max.toInt(), color)
}

private fun statusBubble(pet: PetEntity): String = when {
    pet.love >= PetManager.MAX_LOVE -> "我最喜欢你了！"
    pet.food < 4 || pet.drink < 4 -> "我饿了…"
    pet.food < 6 || pet.drink < 6 -> "有点饿，陪我玩会吧"
    else -> "今天也要元气满满哦"
}
