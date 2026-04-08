package com.hearthappy.logtools.preview

import android.util.Log
import android.view.View
import com.hearthappy.basic.AbsSpecialAdapter
import com.hearthappy.basic.interfaces.IHeaderSupport
import com.hearthappy.log.core.LogScopeProxy
import com.hearthappy.logtools.databinding.ItemLogListBinding

class LogAdapter(private val scopeProxy: LogScopeProxy): AbsSpecialAdapter<ItemLogListBinding, Map<String, Any>>(), IHeaderSupport<ItemLogListBinding> {


    var data: List<String> =emptyList()

    init {
        val queryLogs = scopeProxy.queryLogs()
        val dataOrEmpty = queryLogs.map { it.keys }.firstOrNull()
        dataOrEmpty?.let {
            data = it.toList()
            initData(queryLogs)
        }?: Log.e("LogAdapter", "dataOrEmpty is null")

    }
    override fun ItemLogListBinding.bindViewHolder(data: Map<String, Any>, position: Int) {
        tvTime.text = data["time"].toString()
        tvLevel.text = data["level"].toString()
        tvTag.text = data["tag"].toString()
        tvMethod.text= data["method"].toString()
        tvMessage.text = data["message"].toString()
    }
    var timeListener: ((View) -> Unit)? = null

    var levelListener: ((View) -> Unit)? = null

    var tagListener: ((View) -> Unit)? = null

    var methodListener: ((View) -> Unit)? = null

    var messageListener: ((View) -> Unit)? = null
    override fun ItemLogListBinding.bindHeaderViewHolder() {

        tvTime.text = data[1]
        tvLevel.text = data[2]
        tvTag.text = data[3]
        tvMethod.text = data[4]
        tvMessage.text = data[5]

        tvTime.setOnClickListener { timeListener?.invoke(it) }
        tvLevel.setOnClickListener { levelListener?.invoke(it) }
        tvTag.setOnClickListener { tagListener?.invoke(it) }
        tvMethod.setOnClickListener { methodListener?.invoke(it) }
        tvMessage.setOnClickListener { messageListener?.invoke(it) }
    }

}