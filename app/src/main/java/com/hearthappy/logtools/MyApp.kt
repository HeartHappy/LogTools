package com.hearthappy.logtools

import android.app.Application
import com.hearthappy.logs.LogTools

class MyApp:Application (){

    override fun onCreate() {
        super.onCreate()
        //日志框架初始化
        LogTools.install(applicationContext,"/sdcard/test")
    }
}