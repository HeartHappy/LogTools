package com.hearthappy.logtools

import android.app.Application
import com.hearthappy.log.Logger

class MyApp:Application (){

    override fun onCreate() {
        super.onCreate()
        //日志框架初始化
//        LogTools.install(applicationContext)
        Logger.init(this)
    }
}