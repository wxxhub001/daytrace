package com.daytrace.system

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

/**
 * 弹窗背景图：选中的图直接复制进应用私有目录，
 * 不走 URI 授权，就不用担心相册权限被撤销或原图被删。
 */
object PopupBackground {

    private const val MAX_EDGE = 1600
    private const val FILE_NAME = "popup_background.jpg"

    /** 把相册里的图压到 ≤1600px 存本地，返回文件路径 */
    suspend fun saveFromUri(context: Context, uri: Uri): String? = withContext(Dispatchers.IO) {
        try {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            context.contentResolver.openInputStream(uri)?.use {
                BitmapFactory.decodeStream(it, null, bounds)
            }
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return@withContext null

            val options = BitmapFactory.Options().apply {
                inSampleSize = sampleSize(bounds.outWidth, bounds.outHeight)
                inPreferredConfig = Bitmap.Config.RGB_565
            }
            val bitmap = context.contentResolver.openInputStream(uri)?.use {
                BitmapFactory.decodeStream(it, null, options)
            } ?: return@withContext null

            val dir = File(context.filesDir, "popup").apply { mkdirs() }
            val target = File(dir, FILE_NAME)
            FileOutputStream(target).use { out ->
                bitmap.compress(Bitmap.CompressFormat.JPEG, 88, out)
            }
            bitmap.recycle()
            target.absolutePath
        } catch (t: Throwable) {
            null
        }
    }

    /** 读背景图；文件被删或解码失败返回 null，调用方回落到纯色 */
    suspend fun load(path: String?): ImageBitmap? = withContext(Dispatchers.IO) {
        if (path.isNullOrBlank()) return@withContext null
        val file = File(path)
        if (!file.exists() || file.length() == 0L) return@withContext null
        try {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(path, bounds)
            val options = BitmapFactory.Options().apply {
                inSampleSize = sampleSize(bounds.outWidth, bounds.outHeight)
            }
            BitmapFactory.decodeFile(path, options)?.asImageBitmap()
        } catch (t: Throwable) {
            null
        }
    }

    /** 清除已保存的背景图 */
    fun removeSaved(context: Context) {
        try {
            File(File(context.filesDir, "popup"), FILE_NAME).delete()
        } catch (t: Throwable) {
            // 忽略
        }
    }

    private fun sampleSize(width: Int, height: Int): Int {
        var longest = maxOf(width, height)
        var sample = 1
        while (longest / 2 >= MAX_EDGE) {
            longest /= 2
            sample *= 2
        }
        return sample
    }
}