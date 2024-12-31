package com.hearthappy.logs

import android.content.Context

open class LogTools {
    private var context: Context? = null

    companion object {
        private val logTools by lazy { LogTools() }

        //公共
        val common by lazy {
            Log("Common", logTools.context).apply {
                this.interceptor = object : LogInterceptorAdapter() {
                    override fun isDebug(): BuildTypes = BuildTypes.DEBUG
                }
            }
        }

        //重要
        val important by lazy {
            Log("Important", logTools.context).apply {
                this.interceptor = object : LogInterceptorAdapter() {
                    override fun isWriteFile(): Boolean = true
                }
            }
        }

        //核心
        val kernel by lazy { Log("Kernel", logTools.context) }


        fun install(context: Context) {
            logTools.context = context
        }

        const val DEBUG = true
    }
}