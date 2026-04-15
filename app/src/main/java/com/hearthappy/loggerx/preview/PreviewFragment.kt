package com.hearthappy.loggerx.preview

import android.os.Bundle
import android.os.SystemClock
import android.transition.SidePropagation
import android.transition.Slide
import android.view.Gravity
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
import com.hearthappy.basic.AbsBaseFragment
import com.hearthappy.basic.ext.dp2px
import com.hearthappy.basic.ext.popupWindow
import com.hearthappy.log.LoggerX
import com.hearthappy.log.core.LogScopeProxy
import com.hearthappy.loggerx.databinding.FragmentPreviewBinding
import com.hearthappy.loggerx.databinding.PopMultiFilterBinding
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

class PreviewFragment : AbsBaseFragment<FragmentPreviewBinding>() {

    private val outputterIndex: Int by lazy { arguments?.getInt("index") ?: 0 }
    private val scopeProxy: LogScopeProxy by lazy { LoggerX.getOutputters()[outputterIndex].scope.getProxy() }
    private lateinit var logAdapter: LogAdapter
    private var popupWindow: PopupWindow? = null
    private val popupJobs = mutableListOf<Job>()
    private var lastChipClickTs: Long = 0L
    private var lastResetClickTs: Long = 0L
    private var isConfirmed = false

    private val viewModel: PreviewViewModel by viewModels {
        PreviewViewModel.Factory(scopeProxy)
    }

    override fun FragmentPreviewBinding.initData() {
    }

    override fun FragmentPreviewBinding.initListener() {
        swipeRefreshLayout.setOnRefreshListener {
            viewModel.refreshAppliedLogs()
        }
    }

    override fun FragmentPreviewBinding.initView(savedInstanceState: Bundle?) {
        viewBinding.apply {
            logAdapter = LogAdapter()
            rvLogList.layoutManager = LinearLayoutManager(requireContext())
            rvLogList.adapter = logAdapter
            btnFilter.setOnClickListener {
                showFilterPopup(it)
            }
            viewModel.loadInitialLogs()
        }
    }

    override fun FragmentPreviewBinding.initViewModelListener() {
        lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    viewModel.logUiState.collect { state ->
                        loadingOverlay.isVisible = state.loading
                        swipeRefreshLayout.isRefreshing = false
                        btnFilter.isEnabled = !state.loading
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

    private fun showFilterPopup(anchor: View) {
        isConfirmed = false
        viewModel.startFilterEditing()
        popupWindow?.dismiss()
        popupWindow = popupWindow(
            viewBinding = PopMultiFilterBinding.inflate(layoutInflater),
            width = ViewGroup.LayoutParams.MATCH_PARENT,
            height = ViewGroup.LayoutParams.WRAP_CONTENT,
            viewEventListener = { vb ->
                setupFilterPopup(vb)
            }, transition = Slide()
        ).apply {
            isOutsideTouchable = true
            isFocusable = true
            setOnDismissListener {
                popupJobs.forEach { it.cancel() }
                popupJobs.clear()
                if (!isConfirmed) {
                    viewModel.cancelDraft()
                }
                viewModel.clearDistinctCache()
            }
        }

        popupWindow?.showAtLocation(viewBinding.root, Gravity.BOTTOM, 0, 0)
    }

    private fun setupFilterPopup(vb: PopMultiFilterBinding) {
        val dimensionAdapter = FilterDimensionRowAdapter { category, value ->
            if (!passDebounce(true)) return@FilterDimensionRowAdapter
            if (value == null) {
                viewModel.clearCategory(category)
            } else {
                viewModel.toggleSelection(category, value)
            }
        }
        vb.rvDimensionRows.layoutManager = LinearLayoutManager(requireContext())
        vb.rvDimensionRows.adapter = dimensionAdapter

        vb.btnReset.setOnClickListener {
            if (!passDebounce(false)) return@setOnClickListener
            viewModel.resetDraft()
        }
        vb.btnConfirm.setOnClickListener {
            isConfirmed = true
            viewModel.confirmDraft()
            popupWindow?.dismiss()
        }

        popupJobs += viewLifecycleOwner.lifecycleScope.launch {
            viewModel.distinctValues.collect { state ->
                vb.pbDistinctLoading.isVisible = state.loading
                dimensionAdapter.submitState(
                    values = state.values,
                    selectedState = viewModel.draftState.value,
                    disabledCategories = state.disabledCategories
                )
            }
        }
        popupJobs += viewLifecycleOwner.lifecycleScope.launch {
            viewModel.draftState.collect { draft ->
                dimensionAdapter.submitState(
                    values = viewModel.distinctValues.value.values,
                    selectedState = draft,
                    disabledCategories = viewModel.distinctValues.value.disabledCategories
                )
            }
        }
    }

    private fun passDebounce(isCategoryClick: Boolean): Boolean {
        val now = SystemClock.elapsedRealtime()
        return if (isCategoryClick) {
            val pass = now - lastChipClickTs >= 300
            if (pass) lastChipClickTs = now
            pass
        } else {
            val pass = now - lastResetClickTs >= 300
            if (pass) lastResetClickTs = now
            pass
        }
    }

    companion object {
        fun newInstance(i: Int): PreviewFragment {
            return PreviewFragment().apply {
                arguments = Bundle().apply {
                    putInt("index", i)
                }
            }
        }
    }

}
