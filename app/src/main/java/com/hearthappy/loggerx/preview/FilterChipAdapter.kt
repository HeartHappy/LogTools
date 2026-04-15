package com.hearthappy.loggerx.preview

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.hearthappy.loggerx.R
import com.hearthappy.loggerx.databinding.ItemFilterChipBinding

class FilterChipAdapter(
    private val category: FilterCategory,
    private val onChipClick: (FilterCategory, String?) -> Unit
) : RecyclerView.Adapter<FilterChipAdapter.ChipViewHolder>() {

    private var values: List<String> = emptyList()
    private var selectedValues: Set<String> = emptySet()
    private var enabled: Boolean = true

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ChipViewHolder {
        val binding = ItemFilterChipBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ChipViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ChipViewHolder, position: Int) {
        val value = if (position == 0) null else values[position - 1]
        val selected = if (value == null) selectedValues.isEmpty() else selectedValues.contains(value)
        holder.bind(value, selected, enabled)
    }

    override fun getItemCount(): Int = values.size + 1

    fun submit(newValues: List<String>, selected: Set<String>, isEnabled: Boolean) {
        values = newValues
        selectedValues = selected
        enabled = isEnabled
        notifyDataSetChanged()
    }

    inner class ChipViewHolder(private val binding: ItemFilterChipBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(value: String?, selected: Boolean, enabled: Boolean) = with(binding) {
            tvChip.text = value ?: root.context.getString(R.string.filter_all)
            tvChip.setTextColor(
                ContextCompat.getColor(
                    root.context,
                    if (selected) R.color.filter_text_selected else R.color.filter_text_normal
                )
            )
            tvChip.setBackgroundResource(
                if (selected) R.drawable.bg_filter_chip_selected else R.drawable.bg_filter_chip_normal
            )
            root.isEnabled = enabled
            root.alpha = if (enabled) 1f else 0.5f
            root.setOnClickListener {
                if (enabled) onChipClick(category, value)
            }
        }
    }
}
