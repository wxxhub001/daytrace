package com.daytrace

import com.daytrace.data.AppSettings
import com.daytrace.data.PopupBgMode
import com.daytrace.data.PopupStyle
import com.daytrace.data.PopupTextMode
import com.daytrace.domain.PopupFormat
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PopupStyleTest {

    @Test
    fun renderReplacesAllTokens() {
        val text = PopupFormat.render(
            template = "{app} 今天用了 {used}，上限 {limit}，还能用 {left}，开了 {opens} 次",
            app = "抖音",
            used = "1 小时",
            limit = "40 分钟",
            left = "0",
            opens = "12",
        )
        assertEquals("抖音 今天用了 1 小时，上限 40 分钟，还能用 0，开了 12 次", text)
    }

    @Test
    fun renderKeepsUnknownTokens() {
        val text = PopupFormat.render(
            template = "{app} 用了 {used}，{oops} 不认识",
            app = "微信",
            used = "5 分钟",
            limit = "",
            left = "",
            opens = "",
        )
        assertEquals("微信 用了 5 分钟，{oops} 不认识", text)
    }

    @Test
    fun contrastOfBlackAndWhiteIs21() {
        val ratio = PopupFormat.contrastRatio(0xFFFFFFFF.toInt(), 0xFF000000.toInt())
        assertEquals(21.0, ratio, 0.05)
    }

    @Test
    fun autoTextColorFollowsBackgroundBrightness() {
        assertEquals(PopupStyle.INK, PopupFormat.autoTextColor(0xFFFFFFFF.toInt()))
        assertEquals(0xFFFFFFFF.toInt(), PopupFormat.autoTextColor(0xFF191919.toInt()))
    }

    @Test
    fun lightYellowOnWhiteIsNotReadable() {
        assertFalse(PopupFormat.isReadable(0xFFFFFFFF.toInt(), 0xFFFFC300.toInt()))
        assertTrue(PopupFormat.isReadable(0xFFFFFFFF.toInt(), PopupStyle.INK))
    }

    @Test
    fun suggestionPicksTheHigherContrastSide() {
        assertEquals(PopupStyle.INK, PopupFormat.suggestedTextColor(0xFFFFFFFF.toInt()))
        assertEquals(0xFFFFFFFF.toInt(), PopupFormat.suggestedTextColor(0xFF191919.toInt()))
    }

    @Test
    fun imageBackgroundUsesWhiteTextInAutoMode() {
        val style = PopupStyle(bgMode = PopupBgMode.IMAGE, textMode = PopupTextMode.AUTO)
        assertEquals(0xFFFFFFFF.toInt(), PopupFormat.effectiveTextColor(style))
    }

    @Test
    fun encodeDecodeRoundTrip() {
        val original = PopupStyle(
            presetId = "mint",
            bgMode = PopupBgMode.COLOR,
            bgColor = 0xFFE8F7EE.toInt(),
            scrim = 0.5f,
            textMode = PopupTextMode.CUSTOM,
            textColor = 0xFF14532D.toInt(),
            accentColor = 0xFF3FAE6A.toInt(),
            title = "别刷了\n真的",
            bodyText = "{app} 今天 {used} 了",
            exitLabel = "遵命",
            snoozeLabel = "再 5 分钟",
            silenceLabel = "别烦我",
            textScale = 1.2f,
        )
        val decoded = PopupStyle.decode(original.encode())
        assertEquals(original, decoded)
    }

    @Test
    fun decodeRejectsGarbage() {
        assertNull(PopupStyle.decode(null))
        assertNull(PopupStyle.decode(""))
        assertNull(PopupStyle.decode("   "))
    }

    @Test
    fun overrideMapRoundTripAndLookup() {
        val a = PopupStyle(title = "全局")
        val b = PopupStyle(title = "抖音专用", bgColor = 0xFF191919.toInt())
        val map = mapOf("com.ss.android.ugc.aweme" to b)
        val decoded = PopupStyle.decodeMap(PopupStyle.encodeMap(map))
        assertEquals("抖音专用", decoded["com.ss.android.ugc.aweme"]?.title)

        val settings = AppSettings(popupStyle = a, popupStyleOverrides = map)
        assertEquals("抖音专用", settings.styleFor("com.ss.android.ugc.aweme")?.title)
        assertEquals("全局", settings.styleFor("com.tencent.mm")?.title)
        assertEquals("全局", settings.styleFor(null)?.title)
        assertTrue(settings.hasPopupOverride("com.ss.android.ugc.aweme"))
        assertFalse(settings.hasPopupOverride("com.tencent.mm"))
    }
}