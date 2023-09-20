package com.hearthappy.logs

open class LogInterceptorAdapter : LogInterceptor {

    override fun isDebug(): Boolean = true

    override fun isWriteFile(): Boolean = true
}