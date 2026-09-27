package com.wjy.foxchat.ui.compose

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import coil.compose.AsyncImage
import com.wjy.foxchat.R
import java.io.File

/**
 * 圆形头像：加载本地图片路径，无图时回退到 app 图标。
 *
 * @param path 本地头像文件路径，null 或读取失败时显示默认占位
 */
@Composable
fun AvatarBadge(
    path: String?,
    size: Dp,
    modifier: Modifier = Modifier
) {
    val file = path?.let { pathToFile(it) }?.takeIf { it.exists() }
    Box(modifier = modifier.size(size), contentAlignment = Alignment.Center) {
        if (file != null) {
            // 用 Coil 异步加载并自动下采样，避免直接解码大图卡顿/占用内存
            AsyncImage(
                model = file,
                contentDescription = null,
                placeholder = painterResource(R.mipmap.ic_launcher),
                error = painterResource(R.mipmap.ic_launcher),
                modifier = Modifier
                    .size(size)
                    .clip(CircleShape),
                contentScale = ContentScale.Crop
            )
        } else {
            Image(
                painter = painterResource(R.mipmap.ic_launcher),
                contentDescription = null,
                modifier = Modifier
                    .size(size)
                    .clip(CircleShape),
                contentScale = ContentScale.Crop
            )
        }
    }
}

private fun pathToFile(path: String): File {
    val parsed = android.net.Uri.parse(path)
    return if (parsed.scheme.isNullOrBlank()) File(path) else File(parsed.path ?: path)
}
