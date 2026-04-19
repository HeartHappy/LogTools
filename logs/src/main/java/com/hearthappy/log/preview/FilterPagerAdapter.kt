package com.hearthappy.log.preview

import android.util.Log
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.viewpager2.adapter.FragmentStateAdapter

/**
 * ViewPager2 的 Adapter，管理多个分类的过滤页面
 * 提供性能优化，支持动态更新选中状态
 */
class FilterPagerAdapter(
    fragmentActivity: FragmentActivity,
    private val categories: List<FilterCategory>,
    private var items: Map<FilterCategory, List<FilterChipItem>>,
    private val selectedState: FilterState,
    private val disabledCategories: Set<FilterCategory> = emptySet(),
    private val onChipClick: (FilterCategory, String?) -> Unit
) : FragmentStateAdapter(fragmentActivity) {

    private val fragments = mutableMapOf<Int, FilterPageFragment>()

    override fun createFragment(position: Int): Fragment {
        val category = categories[position]
        val fragment = FilterPageFragment.newInstance(
            category = category,
            items = (items[category] ?: emptyList()).also { Log.i("TAG", "createFragment $position: ${it.toList()}") },
            selectedValues = selectedState.selectedValues(category),
            isEnabled = !disabledCategories.contains(category)
        )
        fragment.setOnChipClickListener(onChipClick)
        fragments[position] = fragment
        return fragment
    }

    override fun getItemCount(): Int = categories.size

    /**
     * 更新过滤项数据（当数据加载完成后调用）
     * 会刷新已创建的 Fragment 的数据显示
     */
    fun updateItems(newItems: Map<FilterCategory, List<FilterChipItem>>) {
        this.items = newItems
        // 更新所有已创建的 Fragment
        fragments.forEach { (position, fragment) ->
            if (position < categories.size) {
                val category = categories[position]
                val categoryItems = newItems[category] ?: emptyList()
                fragment.updateItems(categoryItems)
            }
        }
    }

    /**
     * 更新所有页面的选中状态
     * 性能优化：仅更新已创建的 Fragment
     */
    fun updateSelection(selectedState: FilterState) {
        fragments.forEach { (position, fragment) ->
            if (position < categories.size) {
                val category = categories[position]
                fragment.updateSelection(selectedState.selectedValues(category))
            }
        }
    }

    /**
     * 获取 Tab 标题
     */
    fun getPageTitle(position: Int): String = categories.getOrNull(position)?.title ?: ""
}

