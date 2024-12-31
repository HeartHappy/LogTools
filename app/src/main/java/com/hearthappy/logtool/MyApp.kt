package com.hearthappy.logtool

import android.app.Application
import com.hearthappy.logs.LogTools

class MyApp:Application (){

    override fun onCreate() {
        super.onCreate()
        LogTools.install(applicationContext)
    }
}