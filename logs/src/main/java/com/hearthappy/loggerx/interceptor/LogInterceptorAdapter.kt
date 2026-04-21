package com.hearthappy.loggerx.interceptor

open class LogInterceptorAdapter: LogInterceptor {

    override fun isDebug(): Boolean = true

    override fun isWriteDatabase(): Boolean = true
}
