package com.hearthappy.log.interceptor

/**
 * @Author ChenRui
 * @Email 1096885636@qq.com
 * @Date 9/20/23
 * @Describe：日志拦截器
 */
interface LogInterceptor {

    /**
     * 日志拦截器 true:拦截debug日志，不在打印Debug日志
     * @return String
     */
    fun isDebug(): Boolean


    /**
     * 是否写入文件 true：写文件，false：不在写文件
     * @return Boolean
     */
    fun isWriteFile():Boolean
}
