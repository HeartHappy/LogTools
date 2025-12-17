package com.hearthappy.log.core


import android.annotation.SuppressLint
import android.app.Application
import android.content.Context
import android.content.ContextWrapper
import java.lang.ref.WeakReference

/**
 * 安全的Context持有器：仅持有Application Context，避免泄漏
 */
object ContextHolder {
    // 用WeakReference进一步兜底（可选，Application本身不会泄漏，强引用也可）
    private var appContext: WeakReference<Context>? = null

    /**
     * 初始化：必须在Application.onCreate中调用
     */
    fun init(application: Application) {
        appContext = WeakReference(application.applicationContext)
    }

    /**
     * 获取Application Context（非空兜底）
     */
    fun getAppContext(): Context {
        return appContext?.get() ?: reflectAppContext()
    }

    /**
     * 兜底方案：从任意Context中提取Application Context
     */
    fun getSafeContext(context: Context): Context {
        var ctx = context
        while (ctx is ContextWrapper) {
            if (ctx is Application) {
                return ctx.applicationContext
            }
            ctx = ctx.baseContext
        }
        return ctx.applicationContext
    }

    /**
     * 反射兜底：未初始化时，自动获取Application Context（避免崩溃）
     */
    @SuppressLint("PrivateApi")
    private fun reflectAppContext(): Context {
        return try {
            val activityThreadClass = Class.forName("android.app.ActivityThread")
            val currentActivityThread = activityThreadClass.getMethod("currentActivityThread").invoke(null)
            val getApplication = activityThreadClass.getMethod("getApplication")
            val application = getApplication.invoke(currentActivityThread) as Application
            application.applicationContext
        } catch (e: Exception) {
            throw IllegalStateException("请先在Application中调用ContextHolder.init(this)初始化！", e)
        }
    }
}