package com.wjy.foxchat.ui.compose

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
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
    val bitmap = remember(path) {
        path?.let { p ->
            runCatching { BitmapFactory.decodeFile(pathToFile(p).absolutePath) }.getOrNull()
        }
    }
    Box(modifier = modifier.size(size), contentAlignment = Alignment.Center) {
        if (bitmap != null) {
            Image(
                painter = BitmapPainter(bitmap.asImageBitmap()),
                contentDescription = null,
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
