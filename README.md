#### 一、集成

```apl
dependencies {
    implementation("com.github.HeartHappy:LogTools:1.0.4")
}
```

#### 二、使用教程

##### 1、日志初始化

```
class MyApp:Application (){

    override fun onCreate() {
        super.onCreate()
        //日志框架初始化
        LogTools.install(applicationContext)
    }
}
```

##### 2、使用

```kotlin
//公共日志
LogTools.common.t(TAG).d("outLog : onCreate")
LogTools.common.t(TAG).i("outLog : onCreate")
LogTools.common.t(TAG).w("outLog : onCreate")

//重要日志
LogTools.important.t(TAG).d("outLog : onCreate")

//核心日志
LogTools.kernel.t(TAG).d("outLog : onCreate")
```

#### 三、自定义你的Log

```kotlin
//1、创建Log对象，传入scope参数，该参数将作为文件名
//2、设置Log拦截器：重写LogInterceptorAdapter的isDebug()或isWriteFile()方法（默认都为：true）
//公共
val common by lazy {
    Log("Common").apply { //定义拦截器，返回BuildTypes.DEBUG：输出log日志
        this.interceptor = object : LogInterceptorAdapter() {
            override fun isDebug(): BuildTypes = BuildTypes.DEBUG
        }
    }
}

//重要
val important by lazy {
    Log("Important").apply { //定义拦截器，true：写入日志文件，false：该日志不写文件
        this.interceptor = object : LogInterceptorAdapter() {
            override fun isWriteFile(): Boolean = true
        }
    }
}

//核心
val kernel by lazy { Log("Kernel") }
```

#### 四、日志文件目录：

##### 1、默认目录

```apl
/sdcard/logger/*.csv
```

##### 2、获取指定文件列表

```apl
 LogTools.getListFile("Kernel")
```

