package com.wjy.foxchat.ui.compose

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

// 颜色与 res/values/colors.xml 保持一致
object ChatColors {
    val FoxOrange = Color(0xFFFF6B3D)
    val FoxOrangeDeep = Color(0xFFDF5129)
    val Background = Color(0xFFF7F8F6)
    val Surface = Color(0xFFFFFFFF)
    val OnSurface = Color(0xFF253033)
    val TextSecondary = Color(0xFF738083)
    val BubbleAI = Color(0xFFFFFFFF)
    val BubbleUser = Color(0xFFFF6B3D)
    val QuotedUser = Color(0xFFE85D35)
    val QuotedUserText = Color(0xFFFFF4EF)
    val SyncGreen = Color(0xFF4D9A75)
    val SyncSurface = Color(0xFFEFF8F3)
    val ChatLine = Color(0xFFE2E8E5)
    val BubbleAILine = Color(0xFFE4EAE7)
}

private val ChatLightColors = lightColorScheme(
    primary = ChatColors.FoxOrange,
    onPrimary = Color.White,
    primaryContainer = ChatColors.FoxOrange,
    onPrimaryContainer = Color.White,
    secondary = ChatColors.FoxOrangeDeep,
    background = ChatColors.Background,
    onBackground = ChatColors.OnSurface,
    surface = ChatColors.Surface,
    onSurface = ChatColors.OnSurface,
    surfaceVariant = ChatColors.Surface,
    onSurfaceVariant = ChatColors.TextSecondary,
    outline = ChatColors.ChatLine
)

@Composable
fun ChatTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = ChatLightColors,
        shapes = androidx.compose.material3.Shapes(
            small = RoundedCornerShape(8.dp),
            medium = RoundedCornerShape(12.dp),
            large = RoundedCornerShape(16.dp)
        ),
        content = content
    )
}
