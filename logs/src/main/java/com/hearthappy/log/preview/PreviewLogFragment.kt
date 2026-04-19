package com.hearthappy.log.preview

import android.content.Intent
import android.os.Bundle
import android.os.SystemClock
import android.transition.Slide
import android.view.View
import android.view.ViewGroup
import android.widget.PopupWindow
import android.widget.Toast
import androidx.core.view.isVisible
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.tabs.TabLayoutMediator
import com.hearthappy.basic.AbsBaseFragment
import com.hearthappy.basic.ext.dp2px
import com.hearthappy.basic.ext.popupWindow
import com.hearthappy.basic.ext.showAtBottom
import com.hearthappy.basic.ext.showAtCenter
import com.hearthappy.log.LoggerX
import com.hearthappy.log.core.LogScopeProxy
import com.hearthappy.log.image.LogImageLoaderFactory
import com.hearthappy.logs.R
import com.hearthappy.logs.databinding.FragmentPreviewBinding
import com.hearthappy.logs.databinding.PopHintBinding
import com.hearthappy.logs.databinding.PopMultiFilterBinding
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

class PreviewLogFragment : AbsBaseFragment<FragmentPreviewBinding>() {

    private val outputterIndex : Int by lazy { arguments?.getInt("index") ?: 0 }
    private val scopeProxy : LogScopeProxy by lazy { LoggerX.getOutputters()[outputterIndex].scope.getProxy() }
    private lateinit var logAdapter : LogAdapter
    private var popupWindow : PopupWindow? = null
    private val popupJobs = mutableListOf<Job>()
    private var lastClickTs : Long = 0L
    private var isConfirmed = false

    private val viewModel : PreviewViewModel by viewModels {
        PreviewViewModel.Factory(scopeProxy)
    }

    override fun FragmentPreviewBinding.initData() {
    }

    override fun FragmentPreviewBinding.initListener() {
        swipeRefreshLayout.setOnRefreshListener {
            viewModel.refreshAppliedLogs()
        }

        btnSimplify.setOnClickListener {
            it.isSelected = !it.isSelected
            logAdapter.isSimplified = it.isSelected
            logAdapter.notifyItemRangeChanged(0, logAdapter.itemCount)
        }
        btnFilter.setOnClickListener {
            showFilterPopup(btnFilter)
        }
        btnDelete.setOnClickListener {
            popupWindow(PopHintBinding.inflate(layoutInflater), width = 300.dp2px(), height = 200.dp2px(), viewEventListener = { vb ->
                vb.apply {
                    tvContent.text= getString(R.string.confirm_deletion)
                    tvConfirm.setOnClickListener { //删除指定作用域的日志
                        LoggerX.getOutputters()[outputterIndex].scope.delete()
                        // 删除后刷新日志列表，通过viewModel和StateFlow机制自动更新UI
                        viewModel.refreshAppliedLogs()
                        dismiss()
                    }
                    tvCancel.setOnClickListener { dismiss() }
                }
            }).showAtCenter(viewBinding.root)
        }
        btnShared.setOnClickListener {
            LoggerX.getOutputters()[outputterIndex].scope.doExportAndShare()
        }
    }

    override fun FragmentPreviewBinding.initView(savedInstanceState : Bundle?) {
        viewBinding.apply {
            logAdapter = LogAdapter { logId ->
                startActivity(Intent(context, PreviewLargeImageActivity::class.java).apply {
                    putExtra(PreviewLargeImageActivity.ARG_OUTPUTTER_INDEX, outputterIndex)
                    putExtra(PreviewLargeImageActivity.ARG_LOG_ID, logId)
                }) //                ImagePreviewDialogFragment.newInstance(outputterIndex, logId)
                //                    .show(childFragmentManager, "image_preview")
            }
            rvLogList.layoutManager = LinearLayoutManager(requireContext())
            rvLogList.adapter = logAdapter
            rvLogList.setHasFixedSize(true)
            rvLogList.addOnScrollListener(object : RecyclerView.OnScrollListener() {
                override fun onScrollStateChanged(recyclerView : RecyclerView, newState : Int) {
                    if (newState == RecyclerView.SCROLL_STATE_DRAGGING || newState == RecyclerView.SCROLL_STATE_SETTLING) {
                        LogImageLoaderFactory.pauseDecode()
                    } else {
                        LogImageLoaderFactory.resumeDecode()
                    }
                }
            })
            viewModel.loadInitialLogs()
        }
    }

    override fun FragmentPreviewBinding.initViewModelListener() {
        lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    viewModel.logUiState.collect { state -> //                        loadingOverlay.isVisible = state.loading
                        swipeRefreshLayout.isRefreshing = false
                        btnFilter.isEnabled = !state.loading //                        btnCancelQuery.isEnabled = state.canCancel
                        //                        switchBackgroundContinue.isChecked = state.keepInBackground
                        //                        pbLoading.progress = state.progressPercent
                        //                        tvQueryProgress.text = "${state.progressStage} ${state.progressPercent}%"
                        logAdapter.submitLogs(state.logs)
                    }
                }
                launch {
                    viewModel.toastEvent.collect {
                        Toast.makeText(requireContext(), it, Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
    }

    private fun showFilterPopup(@Suppress("UNUSED_PARAMETER") anchor : View) {
        isConfirmed = false
        viewModel.startFilterEditing()

        popupWindow?.dismiss()
        popupWindow = popupWindow(viewBinding = PopMultiFilterBinding.inflate(layoutInflater), width = ViewGroup.LayoutParams.MATCH_PARENT, height = 800.dp2px(), viewEventListener = { vb ->
            setupFilterPopup(vb)
            setOnDismissListener {
                popupJobs.forEach { it.cancel() }
                popupJobs.clear()
                if (!isConfirmed) {
                    viewModel.cancelDraft()
                }
                viewModel.clearDistinctCache()
            }
        }, transition = Slide())

        popupWindow?.showAtBottom(viewBinding.root)
    }

    private fun setupFilterPopup(vb : PopMultiFilterBinding) { // 构建过滤分类列表和数据
        val categories = FilterCategory.filterable
        var distinctValues = mapOf<FilterCategory, List<String>>()
        var disabledCategories = setOf<FilterCategory>()
        var pagerAdapter : FilterPagerAdapter? = null

        // 设置 ViewPager2 和 TabLayout
        vb.viewPager.isUserInputEnabled = true
        vb.viewPager.offscreenPageLimit = 2  // 预加载相邻页面，优化性能

        vb.btnReset.setOnClickListener {
            if (!passDebounce()) return@setOnClickListener
            viewModel.resetDraft()
        }

        vb.btnConfirm.setOnClickListener {
            isConfirmed = true
            viewModel.confirmDraft()
            popupWindow?.dismiss()
        }

        // 订阅 distinctValues 更新
        popupJobs += viewLifecycleOwner.lifecycleScope.launch {
            viewModel.distinctValues.collect { state ->
                vb.pbDistinctLoading.isVisible = state.loading

                distinctValues = state.values
                disabledCategories = state.disabledCategories

                // 将字符串列表转换为 FilterChipItem 列表
                val itemsMap = distinctValues.mapValues { (category, values) ->
                    listOf(FilterChipItem.all(requireContext())) + values.map { value ->
                        val isSpecial = category == FilterCategory.LEVEL && (value == "ERROR" || value == "CRITICAL" || value == "FATAL")
                        FilterChipItem.fromString(value, isSpecial)
                    }
                }

                // 创建或更新 PagerAdapter
                if (pagerAdapter == null && distinctValues.isNotEmpty()) { // 只在有数据时创建 adapter
                    pagerAdapter = FilterPagerAdapter(requireActivity(), categories, itemsMap, viewModel.draftState.value, disabledCategories) { category, value ->
                        if (!passDebounce()) return@FilterPagerAdapter
                        viewModel.toggleSelection(category, value)
                    }
                    vb.viewPager.adapter = pagerAdapter

                    // 设置 TabLayout 和 ViewPager2 的关联
                    TabLayoutMediator(vb.tabLayout, vb.viewPager) { tab, position ->
                        tab.text = categories[position].title
                    }.attach()
                } else if (pagerAdapter != null && distinctValues.isNotEmpty()) { // 数据加载完成后，更新 adapter 中的数据
                    pagerAdapter?.updateItems(itemsMap) // 同步选中状态
                    pagerAdapter?.updateSelection(viewModel.draftState.value)
                }
            }
        }

        // 订阅 draftState 更新以同步选中状态
        popupJobs += viewLifecycleOwner.lifecycleScope.launch {
            viewModel.draftState.collect { draft ->
                pagerAdapter?.updateSelection(draft)
            }
        }
    }

    private fun passDebounce() : Boolean {
        val now = SystemClock.elapsedRealtime()
        val pass = now - lastClickTs >= 250
        if (pass) lastClickTs = now
        return pass
    }

    override fun onDestroyView() {
        LogImageLoaderFactory.resumeDecode()
        super.onDestroyView()
    }

    companion object {
        fun newInstance(i : Int) : PreviewLogFragment {
            return PreviewLogFragment().apply {
                arguments = Bundle().apply {
                    putInt("index", i)
                }
            }
        }
    }

}
