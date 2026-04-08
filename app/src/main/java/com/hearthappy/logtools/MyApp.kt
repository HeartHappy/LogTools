package com.hearthappy.logtools

import android.app.Application
import com.hearthappy.log.Logger
import com.hearthappy.log.interceptor.LogInterceptorAdapter

class MyApp: Application() {

    override fun onCreate() {
        super.onCreate() //日志框架初始化
        //        LogTools.install(applicationContext)
        Logger.init(this/* OutputConfig(fileConfig = FileConfig(isWriteFile = true, externalCacheDir?.absolutePath?.plus("/hearthappy")))*/)
        Logger.registerScope(LogInterceptorAdapter(), CUSTOM_SCOPE)

        // 开启按日期自动清理，保留 7 天数据
//        Logger.enableAutoClean(3)

        // 开启按文件大小自动清理，最大 1MB，每次清理 0.2MB
//        Logger.enableAutoClean(1.0, 0.5)
    }

    companion object {
        val CUSTOM_SCOPE = Logger.createScope("CustomScope")
    }
}