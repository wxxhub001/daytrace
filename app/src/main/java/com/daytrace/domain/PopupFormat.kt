package com.daytrace.domain

import com.daytrace.data.PopupStyle
import com.daytrace.data.PopupTextMode
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

/** 弹窗文案里的占位符 → 真实值，以及配色对比度计算 */
object PopupFormat {

    const val TOKEN_APP = "{app}"
    const val TOKEN_USED = "{used}"
    const val TOKEN_LIMIT = "{limit}"
    const val TOKEN_LEFT = "{left}"
    const val TOKEN_OPENS = "{opens}"

    val TOKENS = listOf(TOKEN_APP, TOKEN_USED, TOKEN_LIMIT, TOKEN_LEFT, TOKEN_OPENS)

    /** 未知占位符原样保留，不做报错 */
    fun render(
        template: String,
        app: String,
        used: String,
        limit: String,
        left: String,
        opens: String,
    ): String = template
        .replace(TOKEN_APP, app)
        .replace(TOKEN_USED, used)
        .replace(TOKEN_LIMIT, limit)
        .replace(TOKEN_LEFT, left)
        .replace(TOKEN_OPENS, opens)

    /** WCAG 相对亮度 */
    fun luminance(color: Int): Double {
        val r = linear(((color shr 16) and 0xFF) / 255.0)
        val g = linear(((color shr 8) and 0xFF) / 255.0)
        val b = linear((color and 0xFF) / 255.0)
        return 0.2126 * r + 0.7152 * g + 0.0722 * b
    }

    private fun linear(channel: Double): Double =
        if (channel <= 0.03928) channel / 12.92 else ((channel + 0.055) / 1.055).pow(2.4)

    /** 对比度 1–21，越高越清楚；3 以上算可用，4.5 以上算舒服 */
    fun contrastRatio(bg: Int, fg: Int): Double {
        val a = luminance(bg)
        val b = luminance(fg)
        return (max(a, b) + 0.05) / (min(a, b) + 0.05)
    }

    fun isReadable(bg: Int, fg: Int): Boolean = contrastRatio(bg, fg) >= 3.0

    /** 背景偏亮就用深字，偏暗就用白字 */
    fun autoTextColor(bg: Int): Int =
        if (luminance(bg) > 0.45) PopupStyle.INK else 0xFFFFFFFF.toInt()

    /** 最终生效的文字颜色；图片背景下 AUTO 一律用白字（上面还压了一层遮罩） */
    fun effectiveTextColor(style: PopupStyle): Int = when {
        style.textMode == PopupTextMode.CUSTOM -> style.textColor
        style.bgMode == com.daytrace.data.PopupBgMode.IMAGE -> 0xFFFFFFFF.toInt()
        else -> autoTextColor(style.bgColor)
    }

    /** 给"一键修正"用：返回和背景对比度最高的那个文字色 */
    fun suggestedTextColor(bg: Int): Int {
        val dark = PopupStyle.INK
        val light = 0xFFFFFFFF.toInt()
        return if (contrastRatio(bg, dark) >= contrastRatio(bg, light)) dark else light
    }
}