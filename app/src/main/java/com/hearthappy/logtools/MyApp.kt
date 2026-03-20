package com.hearthappy.logtools

import android.app.Application
import android.os.Environment
import android.util.Log
import com.hearthappy.log.Logger
import com.hearthappy.log.interceptor.LogInterceptorAdapter

class MyApp:Application (){

    override fun onCreate() {
        super.onCreate()
        //日志框架初始化
//        LogTools.install(applicationContext)
//        val path = externalCacheDir?.absolutePath?.plus("/hearthappy")
        Logger.init(this/*,path*/)
        Logger.registerScope(CUSTOM_SCOPE, logInterceptor = LogInterceptorAdapter())
    }

    companion object{
        val CUSTOM_SCOPE=Logger.createScope("CustomScope")
    }
}