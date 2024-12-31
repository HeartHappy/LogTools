package com.hearthappy.logtools

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import com.hearthappy.logs.LogTools
import com.hjq.permissions.OnPermissionCallback
import com.hjq.permissions.Permission
import com.hjq.permissions.XXPermissions
import java.io.File

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        requestPermissions()

    }

    private fun MainActivity.requestPermissions() {
        XXPermissions.with(this@MainActivity).permission(Permission.WRITE_EXTERNAL_STORAGE).request(object :OnPermissionCallback{
            override fun onGranted(permissions: MutableList<String>?, all: Boolean) {
                outLog()
            }
        })
//        PermissionManager.with(this).permission(object : OnPermissionCallback {
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

    fun openFile(view: View){
        val file = LogTools.getListFile("Kernel")?.last()?.absolutePath?.let { File(it) }
        Log.d(TAG, "openFile: ${file?.absolutePath}")
        val intent = Intent(Intent.ACTION_VIEW)
        val uri: Uri = Uri.fromParts("file", file?.absolutePath, null)
        intent.setDataAndType(uri, "text/csv")
        startActivity(intent)
    }
}