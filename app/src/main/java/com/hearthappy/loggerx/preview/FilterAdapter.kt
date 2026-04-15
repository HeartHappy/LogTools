package com.hearthappy.loggerx.preview

import com.hearthappy.basic.AbsSpecialAdapter
import com.hearthappy.loggerx.databinding.ItemFilterListBinding

class FilterAdapter: AbsSpecialAdapter<ItemFilterListBinding, String>() {
    override fun ItemFilterListBinding.bindViewHolder(data: String, position: Int) {
        tvTitle.text = data
    }
}