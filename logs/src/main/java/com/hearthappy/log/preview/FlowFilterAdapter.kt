package com.hearthappy.log.preview

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.hearthappy.logs.R
import com.hearthappy.logs.databinding.ItemFilterFlowChipBinding

/**
 * 通用的流式网格过滤 Adapter
 * 支持多种分类、搜索过滤、单选/多选
 *
 * 特点：
 * - 使用 ListAdapter 进行高效的数据更新
 * - 支持特殊项目的高亮显示（如 Critical、Error）
 * - 性能优化：DiffUtil 计算差异更新
 */
class FlowFilterAdapter(
    private val category: FilterCategory,
    private val onChipClick: (FilterCategory, String?) -> Unit
) : ListAdapter<FilterChipItem, FlowFilterAdapter.ChipViewHolder>(FilterChipDiffCallback) {

    private var selectedValues: Set<String> = emptySet()
    private var isEnabled: Boolean = true

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ChipViewHolder {
        val binding = ItemFilterFlowChipBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ChipViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ChipViewHolder, position: Int) {
        val item = getItem(position)
        val selected = item.value?.let { selectedValues.contains(it) } ?: selectedValues.isEmpty()
        holder.bind(item, selected, isEnabled, category, onChipClick)
    }

    /**
     * 提交新数据
     * 当项目列表或选中状态改变时调用
     * 支持多选过滤
     */
    fun submitData(
        items: List<FilterChipItem>,
        selectedValues: Set<String> = emptySet(),
        isEnabled: Boolean = true
    ) {
        val selectionChanged = selectedValues != this.selectedValues
        val enablementChanged = isEnabled != this.isEnabled
        
        this.selectedValues = selectedValues
        this.isEnabled = isEnabled
        
        // 提交新的列表数据
        submitList(items) {
            // submitList完成后，如果选中状态或启用状态改变，刷新所有item以更新UI
            if (selectionChanged || enablementChanged) {
                notifyDataSetChanged()
            }
        }
    }

    inner class ChipViewHolder(private val binding: ItemFilterFlowChipBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(
            item: FilterChipItem,
            selected: Boolean,
            enabled: Boolean,
            category: FilterCategory,
            onChipClick: (FilterCategory, String?) -> Unit
        ) = with(binding) {
            tvChip.text = item.label
            tvChip.setTextColor(
                ContextCompat.getColor(
                    root.context,
                    if (selected) R.color.filter_text_selected else R.color.filter_text_normal
                )
            )

            // 根据选中状态和特殊标记设置背景
            val backgroundRes = when {
                item.isSpecial && selected -> R.drawable.bg_log_filter_chip_critical_selected
                item.isSpecial -> R.drawable.bg_log_filter_chip_critical_normal
                selected -> R.drawable.bg_log_filter_chip_selected
                else -> R.drawable.bg_log_filter_chip_normal
            }
            tvChip.setBackgroundResource(backgroundRes)

            root.isEnabled = enabled
            root.alpha = if (enabled) 1f else 0.5f
            root.setOnClickListener {
                if (enabled) onChipClick(category, item.value)
            }
        }
    }

    companion object {
        private val FilterChipDiffCallback = object : DiffUtil.ItemCallback<FilterChipItem>() {
            override fun areItemsTheSame(oldItem: FilterChipItem, newItem: FilterChipItem): Boolean =
                oldItem.value == newItem.value

            override fun areContentsTheSame(oldItem: FilterChipItem, newItem: FilterChipItem): Boolean =
                oldItem == newItem
        }
    }
}

/**
 * 过滤 Chip 项的数据模型
 * @param label 显示的标签文本
 * @param value 项目的实际值，null 表示"全部"
 * @param isSpecial 是否是特殊项目（如 Critical、Error），需要高亮显示
 */
data class FilterChipItem(
    val label: String,
    val value: String? = null,
    val isSpecial: Boolean = false
) : java.io.Serializable {
    companion object {
        fun all(context: android.content.Context): FilterChipItem =
            FilterChipItem(context.getString(R.string.filter_all), value = null)

        fun fromString(value: String, isSpecial: Boolean = false): FilterChipItem =
            FilterChipItem(value, value, isSpecial)
    }
}
