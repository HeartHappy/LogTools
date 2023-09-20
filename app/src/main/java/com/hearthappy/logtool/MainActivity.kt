package com.hearthappy.logtool

import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.hearthappy.logs.LogTools
import com.hjq.permissions.OnPermissionCallback
import com.hjq.permissions.XXPermissions

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        requestPermissions()

    }

    private fun MainActivity.requestPermissions() {
        PermissionManager.with(this).permission(object : OnPermissionCallback {
            override fun onGranted(permissions: MutableList<String>, allGranted: Boolean) {
                if (allGranted) {
//                    outLog()
                }
            }

            override fun onDenied(permissions: MutableList<String>, doNotAskAgain: Boolean) {
                if (doNotAskAgain) { // 如果是被永久拒绝就跳转到应用权限系统设置页面
                    XXPermissions.startPermissionActivity(this@MainActivity, permissions)
                } else {
                    Toast.makeText(this@MainActivity, "权限被拒绝！", Toast.LENGTH_SHORT).show()
                    finish()
                }
            }
        }, PermissionManager.STORAGE)
    }

    private fun outLog() {

        LogTools.common.t(TAG).d("outLog : onCreate")
        LogTools.common.t(TAG).i("outLog : onCreate")
        LogTools.common.t(TAG).w("outLog : onCreate")



        LogTools.important.t(TAG).d("outLog : onCreate")

        LogTools.kernel.t(TAG).d("outLog : onCreate")
        LogTools.kernel.t(TAG).d("outLog : onResume")
        LogTools.kernel.t(TAG).i("outLog : onCreate1")
        LogTools.kernel.t(TAG).i("outLog : onCreate2")
        LogTools.kernel.t(TAG).d("outLog : onCreate3")
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
}