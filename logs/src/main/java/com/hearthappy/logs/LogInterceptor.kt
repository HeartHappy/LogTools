package com.hearthappy.logs

interface LogInterceptor {

    /**
     * 日志拦截器 true:拦截debug日志，不在打印Debug日志
     * @return String
     */
    fun isDebug(): BuildTypes


    /**
     * 是否写入文件 true：写文件，false：不在写文件
     * @return Boolean
     */
    fun isWriteFile():Boolean
}

sealed class BuildTypes{
    object DEBUG:BuildTypes()
    object RELEASE:BuildTypes()
}