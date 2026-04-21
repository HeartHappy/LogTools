package com.hearthappy.log.preview

import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.ViewTreeObserver
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import com.google.android.flexbox.FlexboxLayoutManager
import com.google.android.flexbox.JustifyContent
import com.hearthappy.logs.databinding.FragmentLoggerxFilterPageBinding

/**
 * 单个过滤分类页面
 * 使用 FlexboxLayoutManager 实现流式网格布局，支持搜索过滤
 *
 * 功能特点：
 * - 流式网格布局，自动换行
 * - 实时搜索过滤
 * - 性能优化（懒加载、缓存）
 * - 支持特殊状态（如 Critical、Error）的高亮显示
 */
class FilterPageFragment : Fragment() {

    private var _binding : FragmentLoggerxFilterPageBinding? = null
    private val binding get() = _binding!!

    private lateinit var category : FilterCategory
    private lateinit var allItems : List<FilterChipItem>
    private lateinit var adapter : FlowFilterAdapter
    private var selectedValues : Set<String> = emptySet()
    private var isEnabled : Boolean = true
    private var onChipClick : ((FilterCategory, String?) -> Unit)? = null

    override fun onCreateView(inflater : LayoutInflater, container : ViewGroup?, savedInstanceState : Bundle?) : View {
        _binding = FragmentLoggerxFilterPageBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view : View, savedInstanceState : Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        category = arguments?.getSerializable("category") as? FilterCategory ?: FilterCategory.ALL
        @Suppress("UNCHECKED_CAST") allItems = arguments?.get("items") as? List<FilterChipItem> ?: emptyList()
        @Suppress("UNCHECKED_CAST") selectedValues = (arguments?.get("selectedValues") as? Set<String>) ?: emptySet()
        isEnabled = arguments?.getBoolean("isEnabled", true) ?: true

        setupAdapter()
        setupSearch() // 在初始化时就提交初始数据（即使为空），确保后续 updateItems 可以正确更新显示
        // 如果此时数据为空，updateAdapter 会显示 empty 状态
        // 当数据加载完成后，updateItems 会触发 DiffUtil 更新
        updateAdapter()
    }

    /**
     * 初始化 RecyclerView 和 Adapter
     */
    private fun setupAdapter() {
        val layoutManager = FlexboxLayoutManager(requireContext()).apply {
            justifyContent = JustifyContent.FLEX_START
        }

        adapter = FlowFilterAdapter(category) { cat, value ->
            onChipClick?.invoke(cat, value)
        }

        binding.rvFilterChips.apply {
            this.layoutManager = layoutManager
            adapter = this@FilterPageFragment.adapter
            itemAnimator = null  // 禁用动画提高性能

            // 关键：禁用嵌套滚动，确保 RecyclerView 能自动扩展高度
            isNestedScrollingEnabled = false

            // 添加监听，确保布局完成后测量正确
            viewTreeObserver.addOnGlobalLayoutListener(object : ViewTreeObserver.OnGlobalLayoutListener {
                override fun onGlobalLayout() {
                    binding.rvFilterChips.viewTreeObserver.removeOnGlobalLayoutListener(this)
                }
            })
        }
    }

    /**
     * 设置搜索功能
     */
    private fun setupSearch() {
        binding.etSearch.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s : CharSequence?, start : Int, count : Int, after : Int) {}

            override fun onTextChanged(s : CharSequence?, start : Int, before : Int, count : Int) {
                updateAdapter()
            }

            override fun afterTextChanged(s : Editable?) {
                binding.btnClearSearch.isVisible = !s.isNullOrEmpty()
            }
        })

        binding.btnClearSearch.setOnClickListener {
            binding.etSearch.setText("")
        }
    }

    /**
     * 更新 Adapter 数据（支持搜索过滤）
     */
    private fun updateAdapter() { // 检查binding是否存在和view的生命周期
        if (_binding == null) return

        val searchText = binding.etSearch.text.toString().lowercase()
        val filteredItems = (if (searchText.isEmpty()) {
            allItems
        } else {
            allItems.filter { it.label.lowercase().contains(searchText) }
        }).toMutableList()

        binding.tvEmpty.isVisible = filteredItems.isEmpty()
        binding.rvFilterChips.isVisible = filteredItems.isNotEmpty()

        adapter.submitData(filteredItems, selectedValues, isEnabled)

    }

    /**
     * 更新选中状态（当外部过滤条件改变时调用）
     * 支持多选过滤
     */
    fun updateSelection(selectedValues : Set<String>) {
        this.selectedValues = selectedValues // 只有在view存在时才更新UI
        if (_binding != null) {
            updateAdapter()
        }
    }

    /**
     * 更新过滤项数据（当数据加载完成后调用）
     * 支持动态更新列表中的项目
     */
    fun updateItems(newItems : List<FilterChipItem>) {
        this.allItems = newItems // 只有在view存在时才更新UI
        if (_binding != null) {
            updateAdapter()
        }
    }

    /**
     * 设置 Chip 点击监听
     */
    fun setOnChipClickListener(listener : (FilterCategory, String?) -> Unit) {
        this.onChipClick = listener
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        /**
         * 创建新实例
         *
         * @param category 过滤分类
         * @param items 过滤项列表
         * @param selectedValues 已选中的值
         * @param isEnabled 是否启用
         */
        fun newInstance(category : FilterCategory, items : List<FilterChipItem>, selectedValues : Set<String> = emptySet(), isEnabled : Boolean = true) : FilterPageFragment {
            return FilterPageFragment().apply {
                arguments = Bundle().apply {
                    putSerializable("category", category)
                    putSerializable("items", items as java.io.Serializable)
                    putSerializable("selectedValues", selectedValues as java.io.Serializable)
                    putBoolean("isEnabled", isEnabled)
                }
            }
        }
    }
}
