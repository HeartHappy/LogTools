package com.hearthappy.logtools.preview

import com.hearthappy.basic.interfaces.IHeaderSupport
import com.hearthappy.log.core.LogScopeProxy
import com.hearthappy.logtools.databinding.ItemLogListBinding

class HeaderView(val scopeProxy: LogScopeProxy, val data: List<String>): IHeaderSupport<ItemLogListBinding> {

    var timeListener: (() -> Unit)? = null

    var levelListener: (() -> Unit)? = null

    var tagListener: (() -> Unit)? = null

    var methodListener: (() -> Unit)? = null

    var messageListener: (() -> Unit)? = null

    override fun ItemLogListBinding.bindHeaderViewHolder() {
        tvTime.text = data[1]
        tvLevel.text = data[2]
        tvTag.text = data[3]
        tvMethod.text = data[4]
        tvMessage.text = data[5]

        tvTime.setOnClickListener { timeListener?.invoke() }
        tvLevel.setOnClickListener { levelListener?.invoke() }
        tvTag.setOnClickListener { tagListener?.invoke() }
        tvMethod.setOnClickListener { methodListener?.invoke() }
        tvMessage.setOnClickListener { messageListener?.invoke() }
    }
}