package com.daytrace.data

import java.net.URLDecoder
import java.net.URLEncoder

enum class PopupBgMode { COLOR, IMAGE }

enum class PopupTextMode { AUTO, CUSTOM }

/**
 * 到点弹窗的样式。纯 UI 配置，存在 DataStore 里，不进 Room（不需要数据库迁移）。
 *
 * 颜色都是 ARGB 整数。文字颜色默认 AUTO：按背景亮度自动选深色或白色，
 * 避免用户配出"白底白字"这种看不见的组合。
 */
data class PopupStyle(
    val presetId: String = PRESET_DEFAULT,
    val bgMode: PopupBgMode = PopupBgMode.COLOR,
    val bgColor: Int = WHITE,
    /** 图片背景：复制到应用私有目录后的文件路径，不走 URI 授权 */
    val bgImagePath: String? = null,
    /** 图片上压暗的强度 0–0.6，保证文字读得清 */
    val scrim: Float = 0.35f,
    val textMode: PopupTextMode = PopupTextMode.AUTO,
    val textColor: Int = INK,
    /** 主按钮（退出）的底色 */
    val accentColor: Int = AMBER,
    val title: String = "今天的额度用完了",
    val bodyText: String = "{app} 今天用了 {used}，上限 {limit}",
    val exitLabel: String = "好，先退出",
    val snoozeLabel: String = "再给 5 分钟",
    val silenceLabel: String = "今天不再提醒",
    /** 字号倍率 */
    val textScale: Float = 1f,
) {

    fun encode(): String = buildString {
        append("preset=").append(enc(presetId)).append('\n')
        append("bgMode=").append(enc(bgMode.name)).append('\n')
        append("bgColor=").append(enc(bgColor.toString())).append('\n')
        append("bgImage=").append(enc(bgImagePath.orEmpty())).append('\n')
        append("scrim=").append(enc(scrim.toString())).append('\n')
        append("textMode=").append(enc(textMode.name)).append('\n')
        append("textColor=").append(enc(textColor.toString())).append('\n')
        append("accent=").append(enc(accentColor.toString())).append('\n')
        append("title=").append(enc(title)).append('\n')
        append("body=").append(enc(bodyText)).append('\n')
        append("exit=").append(enc(exitLabel)).append('\n')
        append("snooze=").append(enc(snoozeLabel)).append('\n')
        append("silence=").append(enc(silenceLabel)).append('\n')
        append("scale=").append(enc(textScale.toString()))
    }

    companion object {
        const val PRESET_DEFAULT = "default"

        const val WHITE = 0xFFFFFFFF.toInt()
        const val INK = 0xFF191919.toInt()
        const val AMBER = 0xFFFFC300.toInt()

        /** 一行一个键值，值做 URL 编码，标题里有换行也不会破坏格式 */
        fun decode(raw: String?): PopupStyle? {
            if (raw.isNullOrBlank()) return null
            val map = HashMap<String, String>()
            raw.lineSequence().forEach { line ->
                val index = line.indexOf('=')
                if (index <= 0) return@forEach
                map[line.substring(0, index)] = dec(line.substring(index + 1))
            }
            if (map.isEmpty()) return null
            val d = PopupStyle()
            return PopupStyle(
                presetId = map["preset"] ?: d.presetId,
                bgMode = enumOr(map["bgMode"], d.bgMode),
                bgColor = intOr(map["bgColor"], d.bgColor),
                bgImagePath = map["bgImage"]?.takeIf { it.isNotBlank() },
                scrim = floatOr(map["scrim"], d.scrim).coerceIn(0f, 0.8f),
                textMode = enumOr(map["textMode"], d.textMode),
                textColor = intOr(map["textColor"], d.textColor),
                accentColor = intOr(map["accent"], d.accentColor),
                title = map["title"] ?: d.title,
                bodyText = map["body"] ?: d.bodyText,
                exitLabel = map["exit"] ?: d.exitLabel,
                snoozeLabel = map["snooze"] ?: d.snoozeLabel,
                silenceLabel = map["silence"] ?: d.silenceLabel,
                textScale = floatOr(map["scale"], d.textScale).coerceIn(0.8f, 1.5f),
            )
        }

        /** 单条规则覆盖：一行一条，形如 pkg=二次编码后的样式 */
        fun encodeMap(map: Map<String, PopupStyle>): String =
            map.entries.joinToString("\n") { (pkg, style) ->
                enc(pkg) + "=" + enc(style.encode())
            }

        fun decodeMap(raw: String?): Map<String, PopupStyle> {
            if (raw.isNullOrBlank()) return emptyMap()
            val out = HashMap<String, PopupStyle>()
            raw.lineSequence().forEach { line ->
                val index = line.indexOf('=')
                if (index <= 0) return@forEach
                val pkg = dec(line.substring(0, index))
                if (pkg.isBlank()) return@forEach
                decode(dec(line.substring(index + 1)))?.let { out[pkg] = it }
            }
            return out
        }

        private fun enc(value: String): String = URLEncoder.encode(value, "UTF-8")

        private fun dec(value: String): String = try {
            URLDecoder.decode(value, "UTF-8")
        } catch (t: Throwable) {
            value
        }

        private inline fun <reified T : Enum<T>> enumOr(raw: String?, fallback: T): T =
            runCatching { enumValueOf<T>(raw ?: "") }.getOrDefault(fallback)

        private fun intOr(raw: String?, fallback: Int): Int = raw?.toLongOrNull()?.toInt() ?: fallback

        private fun floatOr(raw: String?, fallback: Float): Float = raw?.toFloatOrNull() ?: fallback
    }
}

data class PopupPreset(
    val id: String,
    val name: String,
    val style: PopupStyle,
)

/** 预设主题：一键切换，90% 的场景用这一层就够 */
object PopupPresets {

    val ALL: List<PopupPreset> = listOf(
        PopupPreset("default", "暖阳", PopupStyle()),
        PopupPreset(
            "ink",
            "墨夜",
            PopupStyle(bgColor = 0xFF191919.toInt(), accentColor = PopupStyle.AMBER, textColor = 0xFFFFFFFF.toInt()),
        ),
        PopupPreset(
            "cream",
            "奶油",
            PopupStyle(bgColor = 0xFFFFF6DA.toInt(), accentColor = 0xFFFFC300.toInt()),
        ),
        PopupPreset(
            "mint",
            "薄荷",
            PopupStyle(bgColor = 0xFFE8F7EE.toInt(), accentColor = 0xFF3FAE6A.toInt()),
        ),
        PopupPreset(
            "sky",
            "晴空",
            PopupStyle(bgColor = 0xFFE3F0FF.toInt(), accentColor = 0xFF3D7DFF.toInt()),
        ),
        PopupPreset(
            "sunset",
            "晚霞",
            PopupStyle(bgColor = 0xFFFFE4D6.toInt(), accentColor = 0xFFFF7A45.toInt()),
        ),
        PopupPreset(
            "rose",
            "玫瑰",
            PopupStyle(bgColor = 0xFFFDE8EF.toInt(), accentColor = 0xFFE85D75.toInt()),
        ),
        PopupPreset(
            "paper",
            "纸白",
            PopupStyle(bgColor = 0xFFF7F4EC.toInt(), accentColor = 0xFF8A7B5C.toInt()),
        ),
        PopupPreset(
            "forest",
            "森野",
            PopupStyle(bgColor = 0xFF1E3A27.toInt(), accentColor = 0xFF8FD9A8.toInt()),
        ),
        PopupPreset(
            "graphite",
            "石墨",
            PopupStyle(bgColor = 0xFF2B2B2E.toInt(), accentColor = 0xFFB9A16B.toInt()),
        ),
    )

    fun byId(id: String): PopupPreset = ALL.firstOrNull { it.id == id } ?: ALL.first()

    /** 只替换配色相关的字段，用户改过的文案要留住 */
    fun apply(preset: PopupPreset, current: PopupStyle): PopupStyle = current.copy(
        presetId = preset.id,
        bgMode = PopupBgMode.COLOR,
        bgColor = preset.style.bgColor,
        accentColor = preset.style.accentColor,
        textMode = PopupTextMode.AUTO,
    )
}