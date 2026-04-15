package com.hearthappy.loggerx.preview

import android.util.Log
import com.hearthappy.basic.AbsSpecialAdapter
import com.hearthappy.log.core.LogLevel
import com.hearthappy.log.core.LogScopeProxy
import com.hearthappy.loggerx.databinding.ItemLogListBinding

class LogAdapter(private val scopeProxy: LogScopeProxy): AbsSpecialAdapter<ItemLogListBinding, Map<String, Any>>() {


    var data: List<String> = emptyList()

    init {
        val queryLogs = scopeProxy.queryLogs()
        val dataOrEmpty = queryLogs.map { it.keys }.firstOrNull()
        dataOrEmpty?.let {
            data = it.toList()
            initData(queryLogs)
        } ?: Log.e("LogAdapter", "dataOrEmpty is null")

    }

    override fun ItemLogListBinding.bindViewHolder(data: Map<String, Any>, position: Int) {
        tvTime.text = data["time"].toString()
        tvLevel.text = data["level"].toString()
        level2Color(data["level"].toString())
        tvTag.text = data["tag"].toString()
        tvMethod.text = data["method"].toString()
        tvMessage.text = data["message"].toString()
    }

    fun ItemLogListBinding.level2Color(level: String){
        when (level) {
            // Verbose - 浅灰
            LogLevel.VERBOSE.value -> tvLevel.setTextColor(0xFFBBBBBB.toInt())
            // Debug - 亮蓝
            LogLevel.DEBUG.value -> tvLevel.setTextColor(0xFF33B5E5.toInt())
            // Info - 绿色
            LogLevel.INFO.value -> tvLevel.setTextColor(0xFF99CC00.toInt())
            // Warn - 橙色
            LogLevel.WARN.value -> tvLevel.setTextColor(0xFFFFBB33.toInt())
            // Error - 红色
            LogLevel.ERROR.value -> tvLevel.setTextColor(0xFFFF4444.toInt())
            // 默认 - 浅灰
            else -> tvLevel.setTextColor(0xFFBBBBBB.toInt())
        }
    }
}