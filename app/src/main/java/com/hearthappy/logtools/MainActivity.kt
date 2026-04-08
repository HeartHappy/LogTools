package com.hearthappy.logtools

import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import com.hearthappy.log.Logger
import com.hearthappy.logtools.preview.PreviewActivity
import com.hjq.permissions.Permission
import com.hjq.permissions.XXPermissions
import java.util.Random

class MainActivity: AppCompatActivity() {
    private val handler = Handler(Looper.getMainLooper())
    private val random = Random()
    private var logCount = 0

    private val logRunnable = object: Runnable {
        override fun run() {
            val level = when (random.nextInt(5)) {
                0 -> "D"
                1 -> "I"
                2 -> "W"
                3 -> "E"
                else -> "V"
            }
            val message = "测试日志消息 $logCount - ${System.currentTimeMillis()}"
            Logger.COMMON.i("testMethod:$message")
            logCount++
            if (logCount % 100 == 0) {
                Log.d(TAG, "Inserted $logCount logs.")
            }
            handler.postDelayed(this, 100) // 每100毫秒插入一条日志
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // 启动循环插入日志
//        handler.post(logRunnable)

        val dbFileSize = Logger.getDbFileSize()
        Log.i(TAG, "onCreate: $dbFileSize")
    }

    override fun onDestroy() {
        super.onDestroy()
        handler.removeCallbacks(logRunnable)
    }


    companion object {
        private const val TAG = "MainActivity"
    }


    fun outLogAndFile(view: View) {
        XXPermissions.with(this@MainActivity).permission(Permission.WRITE_EXTERNAL_STORAGE).request { permissions, all ->

            if (all) {
                Logger.COMMON.d("common test")
                Logger.COMMON.d("common onCreate d")
                Logger.COMMON.i("common onCreate i")
                Logger.COMMON.w("common onCreate w")

                Logger.IMPORTANT.d("important onCreate d 挺不错的，哈哈")

                Logger.KERNEL.d("kernel onCreate d")
                Logger.KERNEL.w("kernel onResume w")
                Logger.KERNEL.i("kernel onCreate1 i")
                Logger.KERNEL.v("kernel onCreate3 v")
                Logger.ERROR.e("kernel onCreate2 e", Throwable("runtime error"))
                Logger.KERNEL.i("${Logger.KERNEL.getDirectory()}")
                Logger.COMMON.json("")
                MyApp.CUSTOM_SCOPE.d("自定义的作用域日志  测试！！！")
            }
        }
    }

    fun deleteLogFile(view: View) {
        Logger.KERNEL.deleteOldestSingleFile()
        Logger.COMMON.clearAllFiles()
    }

    fun deleteAllLogFile(view: View) {
        val clearAll = Logger.clear()
        Log.d(TAG, "deleteAllLogFile: $clearAll")
    }

    fun openFile(view: View) {
        val csvFile = Logger.KERNEL.getListFiles()?.last() // 2. 打开CSV文件的核心方法
        csvFile?.let { file ->
            try { // 生成Content URI（适配Android 7.0+）
                val fileUri = FileProvider.getUriForFile(applicationContext, "${applicationContext.packageName}.fileprovider", file)

                // 构建打开CSV的Intent
                val intent = Intent(Intent.ACTION_VIEW).apply { // 设置URI和MIME类型（CSV核心类型）
                    setDataAndType(fileUri, "text/csv") // 兼容部分系统识别不到text/csv的情况
                    putExtra(Intent.EXTRA_MIME_TYPES, arrayOf("text/csv", "text/plain")) // 授予临时读取权限（关键：系统应用需要权限访问文件）
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION) // 避免多个应用时的弹窗（可选，如需指定默认应用则注释）
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }

                // 检查是否有应用能处理该Intent（避免崩溃）
                val resolveInfo = packageManager.resolveActivity(intent, PackageManager.MATCH_DEFAULT_ONLY)
                if (resolveInfo != null) {
                    startActivity(intent)
                } else { // 无可用应用的兜底提示
                    Toast.makeText(this, "未找到可打开CSV文件的应用", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) { // 捕获所有异常（如权限、URI错误、文件损坏等）
                Log.e("OpenCSV", "打开CSV文件失败", e)
                Toast.makeText(this, "打开失败：${e.message}", Toast.LENGTH_SHORT).show()
            }
        } ?: run { // 无有效CSV文件的提示
            Toast.makeText(this, "未找到Kernel目录下的有效CSV文件", Toast.LENGTH_SHORT).show()
        }

    }

    fun queryDBLogs(view: View) {

        val distinctValues = Logger.IMPORTANT.getDistinctValues(Logger.COLUMN_METHOD)
        Log.i(TAG, "queryDBLogs: ${distinctValues.toList()}")
        val queryLogs = Logger.IMPORTANT.queryLogs(isAsc = false)
        Log.i("PreviewActivity", "onCreate: ${queryLogs.toList().joinToString { "$it\n" }}")
        startActivity(Intent(this, PreviewActivity::class.java))
    }

}