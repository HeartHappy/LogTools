package com.hearthappy.logtools.preview

import com.hearthappy.basic.AbsSpecialAdapter
import com.hearthappy.logtools.databinding.ItemLogListBinding

class LogAdapter: AbsSpecialAdapter<ItemLogListBinding, Map<String, Any>>() {
    override fun ItemLogListBinding.bindViewHolder(data: Map<String, Any>, position: Int) {
        tvTime.text = data["time"].toString()
        tvLevel.text = data["level"].toString()
        tvTag.text = data["tag"].toString()
        tvMethod.text= data["method"].toString()
        tvMessage.text = data["message"].toString()
    }
}