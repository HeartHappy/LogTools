package com.hearthappy.log.core

import android.app.Activity
import android.app.Application
import android.content.Context
import android.os.Bundle
import java.lang.ref.WeakReference
import java.util.Date

/**
 * Created Date: 2025/12/17/周三
 * @author ChenRui
 * ClassDescription：上下文采集器（自动获取Activity/类/方法/日期）
 */
object LogContextCollector {
    private var currentActivity: WeakReference<Activity>? = null

    /**
     * 初始化：在Application中调用，注册Activity生命周期以获取当前Activity
     */
    internal fun init(application: Application) {
        application.registerActivityLifecycleCallbacks(object : Application.ActivityLifecycleCallbacks {
            override fun onActivityResumed(activity: Activity) {
                currentActivity = WeakReference(activity)
            }

            override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {}
            override fun onActivityStarted(activity: Activity) {}
            override fun onActivityPaused(activity: Activity) {}
            override fun onActivityStopped(activity: Activity) {}
            override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) {}
            override fun onActivityDestroyed(activity: Activity) {}
        })
    }

    /**
     * 获取当前Activity名称（无则返回UnknownActivity）
     */
    internal fun getCurrentActivityName(): String {
        return currentActivity?.get()?.javaClass?.simpleName ?: "UnknownActivity"
    }

    /**
     * 获取当前日期（格式化）
     */
    internal fun getCurrentDate(): String {
        return LogFormatter.DATE_FORMAT.format(Date())
    }

    internal fun getContext(): Context {
        return ContextHolder.getAppContext()
    }

    /**
     * 获取调用栈信息（类名、方法名、行号）：自动跳过日志框架自身的栈帧
     */
    internal fun getStackTraceInfo(): StackTraceInfo {
        val stackTrace = Thread.currentThread().stackTrace // 跳过日志框架的栈帧，定位到业务代码的调用点
        val targetIndex = findTargetStackTraceIndex(stackTrace)
        val targetElement = stackTrace.getOrNull(targetIndex) ?: return StackTraceInfo()
        return StackTraceInfo(className = targetElement.className.substringAfterLast("."), // 简化类名（去掉包名）
            methodName = targetElement.methodName, lineNumber = targetElement.lineNumber)
    }

    /**
     * 找到业务代码的栈帧索引（跳过日志工具类的栈）
     */
    private fun findTargetStackTraceIndex(stackTrace: Array<StackTraceElement>): Int {
        val packageName = getContext().packageName
        for (i in stackTrace.indices) {
            val element = stackTrace[i]
            if (element.className.contains(packageName)) {
                return i
            }
        }
        return -1
    }

    /**
     * 调用栈信息数据类
     */
    data class StackTraceInfo(val className: String = "UnknownClass", val methodName: String = "unknownMethod", val lineNumber: Int = 0)
}