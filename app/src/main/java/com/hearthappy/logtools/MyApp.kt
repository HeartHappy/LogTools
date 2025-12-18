package com.hearthappy.logtools

import android.app.Application
import com.hearthappy.log.Logger
import com.hearthappy.log.interceptor.LogInterceptorAdapter

class MyApp:Application (){

    override fun onCreate() {
        super.onCreate()
        //日志框架初始化
//        LogTools.install(applicationContext)
        Logger.init(this)
        Logger.registerScope(CUSTOM_SCOPE, logInterceptor = LogInterceptorAdapter())
    }

    companion object{
        val CUSTOM_SCOPE=Logger.createScope("CustomScope")
    }
}