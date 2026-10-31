package com.daytrace.system

import android.content.Context
import android.graphics.Bitmap
import android.util.LruCache
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.graphics.drawable.toBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object AppIconLoader {

    private val cache = LruCache<String, Bitmap>(96)

    suspend fun load(context: Context, pkg: String, sizePx: Int = 144): ImageBitmap? =
        withContext(Dispatchers.IO) {
            cache.get(pkg)?.let { return@withContext it.asImageBitmap() }
            val bitmap = try {
                context.packageManager.getApplicationIcon(pkg)
                    .toBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
            } catch (t: Throwable) {
                null
            }
            if (bitmap != null) cache.put(pkg, bitmap)
            bitmap?.asImageBitmap()
        }
}
