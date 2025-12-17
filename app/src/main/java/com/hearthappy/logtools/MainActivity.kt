package com.hearthappy.logtools

import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import com.hearthappy.log.Logger
import com.hearthappy.log.core.LogManager.getListFile
import com.hjq.permissions.Permission
import com.hjq.permissions.XXPermissions

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
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

                Logger.IMPORTANT.d("important onCreate d")

                Logger.KERNEL.d("kernel onCreate d")
                Logger.KERNEL.w("kernel onResume w")
                Logger.KERNEL.i("kernel onCreate1 i")
                Logger.KERNEL.v("kernel onCreate3 v")
                Logger.KERNEL.e("kernel onCreate2 e", Throwable("runtime error"))
            }
        }
    }

    fun deleteLogFile(view: View) {
        Logger.COMMON.clear()
    }

    fun deleteAllLogFile(view: View) {
        val clearAll = Logger.clearAllFiles()
        val d = Log.d(TAG, "deleteAllLogFile: $clearAll")
    }

    fun openFile(view: View) {
        val csvFile = Logger.KERNEL.getListFiles()?.last() // 2. 打开CSV文件的核心方法
        csvFile?.let { file ->
            try { // 生成Content URI（适配Android 7.0+）
                val fileUri = FileProvider.getUriForFile(applicationContext, "${BuildConfig.APPLICATION_ID}.fileprovider", file)

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

}