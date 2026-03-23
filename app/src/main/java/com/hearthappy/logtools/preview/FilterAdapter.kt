package com.hearthappy.logtools.preview

import com.hearthappy.basic.AbsSpecialAdapter
import com.hearthappy.logtools.databinding.ItemFilterListBinding

class FilterAdapter: AbsSpecialAdapter<ItemFilterListBinding, String>() {
    override fun ItemFilterListBinding.bindViewHolder(data: String, position: Int) {
        tvTitle.text = data
    }
}