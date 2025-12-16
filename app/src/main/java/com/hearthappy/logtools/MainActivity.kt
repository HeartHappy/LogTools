package com.hearthappy.logtools

import android.content.Intent
import android.content.Intent.ACTION_OPEN_DOCUMENT
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import com.hearthappy.logs.LogTools
import com.hjq.permissions.OnPermissionCallback
import com.hjq.permissions.Permission
import com.hjq.permissions.XXPermissions
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        requestPermissions()

    }

    private fun MainActivity.requestPermissions() {
        XXPermissions.with(this@MainActivity).permission(Permission.WRITE_EXTERNAL_STORAGE).request(object : OnPermissionCallback {
            override fun onGranted(permissions: MutableList<String>?, all: Boolean) {
                outLog()
            }
        }) //        PermissionManager.with(this).permission(object : OnPermissionCallback {
        //            override fun onGranted(permissions: MutableList<String>, allGranted: Boolean) {
        //                if (allGranted) { //                    outLog()
        //                }
        //            }
        //
        //            override fun onDenied(permissions: MutableList<String>, doNotAskAgain: Boolean) {
        //                if (doNotAskAgain) { // 如果是被永久拒绝就跳转到应用权限系统设置页面
        //                    XXPermissions.startPermissionActivity(this@MainActivity, permissions)
        //                } else {
        //                    Toast.makeText(this@MainActivity, "权限被拒绝！", Toast.LENGTH_SHORT).show()
        //                    finish()
        //                }
        //            }
        //        }, PermissionManager.STORAGE)
    }

    private fun outLog() {
        LogTools.common.t(TAG).d("outLog : common test")
        LogTools.common.t(TAG).d("outLog : common onCreate d")
        LogTools.common.t(TAG).i("outLog : common onCreate i")
        LogTools.common.t(TAG).w("outLog : common onCreate w")

        LogTools.important.t(TAG).d("outLog : important onCreate d")

        LogTools.kernel.t(TAG).d("outLog :kernel onCreate d")
        LogTools.kernel.t(TAG).d("outLog :kernel onResume d")
        LogTools.kernel.t(TAG).i("outLog :kernel onCreate1 i")
        LogTools.kernel.t(TAG).i("outLog :kernel onCreate2 i")
        LogTools.kernel.t(TAG).d("outLog :kernel onCreate3 d")
    }

    companion object {
        private const val TAG = "MainActivity"
    }


    fun outLogAndFile(view: View) {
        outLog()
    }

    fun deleteLogFile(view: View) {
        LogTools.common.clear("Common")
    }

    fun deleteAllLogFile(view: View) {
        val clearAll = LogTools.kernel.clearAll()
        val d = Log.d(TAG, "deleteAllLogFile: $clearAll")
    }

    fun openFile(view: View) {
        val csvFile = LogTools.getListFile("Kernel")?.last() // 2. 打开CSV文件的核心方法
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