package com.hearthappy.logs

open class LogTools {


    companion object {

        //公共
        val common by lazy {
            Log("Common").apply {
                this.interceptor = object : LogInterceptorAdapter() {
                    override fun isDebug(): Boolean = DEBUG
                }
            }
        }

        //重要
        val important by lazy {
            Log("Important").apply {
                this.interceptor = object : LogInterceptorAdapter() {
                    override fun isWriteFile(): Boolean = false
                }
            }
        }

        //核心
        val kernel by lazy { Log("Kernel") }

        const val DEBUG = true
    }
}