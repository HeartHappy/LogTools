package com.hearthappy.logs

open class LogInterceptorAdapter : LogInterceptor {

    override fun isDebug(): BuildTypes = BuildTypes.DEBUG

    override fun isWriteFile(): Boolean = true
}