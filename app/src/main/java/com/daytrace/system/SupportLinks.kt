package com.daytrace.system

/**
 * 支持作者的入口。
 *
 * 注意：这里只是"打开浏览器"，App 本身仍然没有 INTERNET 权限 ——
 * 联网的是浏览器，不是本应用。这一点在隐私政策里也要写清楚。
 */
object SupportLinks {

    /**
     * 爱发电主页。留空 = 界面上不显示这个按钮（不做假链接，点进去看到陌生首页比没有更糟）。
     * 有主页了就填进来，比如 "https://afdian.com/a/你的名字"。
     */
    const val AIFADIAN = ""

    /** 赞赏码图片放在 app/src/main/assets/donate_qr.png，App 会直接读这张图 */
    const val DONATE_QR_ASSET = "donate_qr.png"

    val hasAifadian: Boolean get() = AIFADIAN.isNotBlank()
}