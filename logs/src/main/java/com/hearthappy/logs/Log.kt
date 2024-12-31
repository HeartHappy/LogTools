package com.hearthappy.logs

import android.content.Context


/**
 * @Author ChenRui
 * @Email 1096885636@qq.com
 * @Date 9/20/23
 * @Describe 日志类，对外开放调用
 */
class Log(private var scope: String = "default",private var context: Context?=null) {

    var interceptor: LogInterceptor = LogInterceptorAdapter()

    private val logImpl: ILog by lazy { LogImpl(scope, interceptor,context) }

    companion object {

        const val VERBOSE = 2

        const val DEBUG = 3

        const val INFO = 4

        const val WARN = 5

        const val ERROR = 6

        const val ASSERT = 7
    }

    fun t(tag: String): Log {
        logImpl.t(tag)
        return this
    }

    fun d(message: String, vararg args: Any?) {
        logImpl.d(message, args)
    }

    fun d(`object`: Any?) {
        logImpl.d(`object`)
    }

    fun e(message: String, vararg args: Any?) {
        logImpl.e(message, args)
    }

    fun e(throwable: Throwable?, message: String, vararg args: Any?) {
        logImpl.e(throwable, message, args)
    }

    fun w(message: String, vararg args: Any?) {
        logImpl.w(message, args)
    }

    fun i(message: String, vararg args: Any?) {
        logImpl.i(message, args)
    }

    fun v(message: String, vararg args: Any?) {
        logImpl.v(message, args)
    }

    fun wtf(message: String, vararg args: Any?) {
        logImpl.wtf(message, args)
    }

    fun json(json: String?) {
        logImpl.json(json)
    }

    fun xml(xml: String?) {
        logImpl.xml(xml)
    }

    fun log(priority: Int, tag: String?, message: String?, throwable: Throwable?) {
        logImpl.log(priority, tag, message, throwable)
    }

    fun clear(scope: String) = logImpl.clear(scope)

    fun clearAll(): Boolean = logImpl.clearAll()
}