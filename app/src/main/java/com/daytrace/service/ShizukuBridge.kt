package com.daytrace.service

import android.content.Context
import android.content.pm.PackageManager
import moe.shizuku.server.IShizukuService
import rikka.shizuku.Shizuku

/**
 * Shizuku 桥接：借 adb 授权的 shell 权限执行 am force-stop，实现真正的关闭应用。
 * 没装 Shizuku 或没授权时所有调用都安全地返回 false。
 */
object ShizukuBridge {

    const val SHIZUKU_PACKAGE = "moe.shizuku.privileged.api"

    fun installed(context: Context): Boolean = try {
        context.packageManager.getPackageInfo(SHIZUKU_PACKAGE, 0)
        true
    } catch (t: Throwable) {
        false
    }

    fun isBinderAlive(): Boolean = try {
        Shizuku.pingBinder()
    } catch (t: Throwable) {
        false
    }

    fun hasPermission(): Boolean = try {
        isBinderAlive() && Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
    } catch (t: Throwable) {
        false
    }

    /** 返回 true 表示授权请求已发出 */
    fun requestPermission(requestCode: Int): Boolean = try {
        if (!isBinderAlive() || Shizuku.isPreV11()) {
            false
        } else {
            Shizuku.requestPermission(requestCode)
            true
        }
    } catch (t: Throwable) {
        false
    }

    /**
     * 以 shell 身份执行 `am force-stop`。
     * Shizuku 13 没有公开 newProcess，改用 IShizukuService 的 AIDL 接口。
     */
    fun forceStop(context: Context, packageName: String): Boolean = try {
        if (!hasPermission()) {
            false
        } else {
            val binder = Shizuku.getBinder()
            if (binder == null) {
                false
            } else {
                val service = IShizukuService.Stub.asInterface(binder)
                service?.newProcess(arrayOf("am", "force-stop", packageName), null, null)
                service != null
            }
        }
    } catch (t: Throwable) {
        false
    }
}
