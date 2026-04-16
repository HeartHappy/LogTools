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
     * 是否写入数据库 true：写数据库，false：不在写数据库
     * @return Boolean
     */
    fun isWriteDatabase(): Boolean
}
