package com.hearthappy.log.core

import com.hearthappy.log.Logger
import com.hearthappy.log.interceptor.LogInterceptor

internal object LogOutputterManager {

    private val outputterMap = mutableMapOf<String, LogOutputter>()

    /**
     * 简易工厂方法：快速创建自定义Scope（开发者无需实现接口）
     */
    internal fun create(customScope: String): LogScopeProxy {
        require(customScope.isNotBlank()) { "Scope标签不能为空" }
        return LogScopeProxy(customScope)
    }

    /**
     * 初始化默认Scope输出器：新增Scope只需在此添加
     */
    internal fun initDefaultOutputters(outputConfig: OutputConfig) {
        val isLog = outputConfig.isLog
        val isWriteDatabase = outputConfig.isWriteDatabase
        val isWriteFile = outputConfig.fileConfig?.isWriteFile ?: false
        val logInterceptorAdapter = object: LogInterceptor {
            override fun isDebug(): Boolean = isLog
            override fun isWriteFile(): Boolean = isWriteFile
            override fun isWriteDatabase(): Boolean = isWriteDatabase
        }
        outputterMap[Logger.COMMON.getTag()] = LogOutputter(scope = Logger.COMMON, logInterceptorAdapter)
        outputterMap[Logger.IMPORTANT.getTag()] = LogOutputter(scope = Logger.IMPORTANT, logInterceptorAdapter)
        outputterMap[Logger.KERNEL.getTag()] = LogOutputter(scope = Logger.KERNEL, logInterceptorAdapter)
        outputterMap[Logger.ERROR.getTag()] = LogOutputter(scope = Logger.ERROR, logInterceptorAdapter)
    }


    /**
     * 扩展自定义Scope输出器（动态新增）
     */
    internal fun registerOutputter(scope: String, outputter: LogOutputter) {
        outputterMap[scope] = outputter
    }

    /**
     * 获取Scope对应的输出器
     */
    internal fun getOutputter(scope: String): LogOutputter {
        return outputterMap[scope] ?: throw IllegalArgumentException("未注册的Scope：${scope}")
    }

    internal fun getScopes(): MutableSet<String> {
        return outputterMap.keys
    }

    internal fun getOutputters(): MutableCollection<LogOutputter> {
        return outputterMap.values
    }

}