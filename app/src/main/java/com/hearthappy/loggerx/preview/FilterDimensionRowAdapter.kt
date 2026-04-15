package com.hearthappy.loggerx.preview

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.hearthappy.loggerx.databinding.ItemFilterDimensionRowBinding

class FilterDimensionRowAdapter(
    private val onChipClick: (FilterCategory, String?) -> Unit
) : RecyclerView.Adapter<FilterDimensionRowAdapter.RowViewHolder>() {

    private val categories = FilterCategory.filterable
    private val chipAdapters = mutableMapOf<FilterCategory, FilterChipAdapter>()
    private var values: Map<FilterCategory, List<String>> = emptyMap()
    private var selectedState: FilterState = FilterState.EMPTY
    private var disabledCategories: Set<FilterCategory> = emptySet()

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RowViewHolder {
        val binding = ItemFilterDimensionRowBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return RowViewHolder(binding)
    }

    override fun onBindViewHolder(holder: RowViewHolder, position: Int) {
        holder.bind(categories[position])
    }

    override fun getItemCount(): Int = categories.size

    fun submitState(
        values: Map<FilterCategory, List<String>>,
        selectedState: FilterState,
        disabledCategories: Set<FilterCategory>
    ) {
        this.values = values
        this.selectedState = selectedState
        this.disabledCategories = disabledCategories
        notifyDataSetChanged()
    }

    inner class RowViewHolder(private val binding: ItemFilterDimensionRowBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(category: FilterCategory) = with(binding) {
            tvDimension.text = category.title
            val adapter = chipAdapters.getOrPut(category) {
                FilterChipAdapter(category, onChipClick)
            }
            rvChips.layoutManager = LinearLayoutManager(root.context, RecyclerView.HORIZONTAL, false)
            rvChips.adapter = adapter
            adapter.submit(
                newValues = values[category].orEmpty(),
                selected = selectedState.selectedValues(category),
                isEnabled = !disabledCategories.contains(category)
            )
        }
    }
}
