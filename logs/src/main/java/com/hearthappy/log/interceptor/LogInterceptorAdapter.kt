package com.hearthappy.log.interceptor

open class LogInterceptorAdapter : LogInterceptor {

    override fun isDebug(): Boolean = true

    override fun isWriteFile(): Boolean = true
}