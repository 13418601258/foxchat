package com.wjy.foxchat.model

/**
 * 预设头像。以整数索引（0..7）标识，登录时选择并存储。
 */
object AvatarPresets {
    /** 每个头像对应的背景色（十六进制，供 Compose 与 XML 两侧复用） */
    val colorHex: List<String> = listOf(
        "#FF6B3D", "#4A90D9", "#4CAF50", "#9C27B0",
        "#EC6F9E", "#F5A623", "#26A69A", "#E53935"
    )

    val emojis: List<String> = listOf(
        "\uD83E\uDD8A", // 🦊
        "\uD83D\uDC31", // 🐱
        "\uD83D\uDC36", // 🐶
        "\uD83D\uDC3C", // 🐼
        "\uD83D\uDC30", // 🐰
        "\uD83E\uDD81", // 🦁
        "\uD83D\uDC38", // 🐸
        "\uD83D\uDC2F"  // 🐯
    )

    /** 未设置头像时使用的默认值 */
    const val DEFAULT_INDEX = 0

    /** 无头像（未配对/未选择）的哨兵值 */
    const val NONE = -1

    fun emoji(index: Int): String = emojis.getOrElse(index) { emojis.first() }

    fun colorHex(index: Int): String = colorHex.getOrElse(index) { colorHex.first() }

    fun isValid(index: Int): Boolean = index in emojis.indices

    fun normalize(index: Int): Int = if (isValid(index)) index else DEFAULT_INDEX
}
