package com.hearthappy.loggerx.preview

import androidx.activity.viewModels
import com.hearthappy.basic.AbsBaseActivity
import com.hearthappy.basic.ext.addListener
import com.hearthappy.basic.ext.addStateAdapter
import com.hearthappy.loggerx.LoggerX
import com.hearthappy.loggerx.R
import com.hearthappy.loggerx.databinding.ActivityLoggerxPreviewBinding

class PreviewLogActivity: AbsBaseActivity<ActivityLoggerxPreviewBinding>() {
    private val viewModel by viewModels<PreviewOperateViewModel>()


    override fun ActivityLoggerxPreviewBinding.initData() {
    }

    override fun ActivityLoggerxPreviewBinding.initListener() {
        vp.addListener { tabLayout.getTabAt(it)?.select() }
        tabLayout.addListener(onSelect = {
            vp.currentItem = it.position
        })
        ivLogBack.setOnClickListener { finish() }
        ivStreamline.setOnClickListener {
            ivStreamline.isSelected = !ivStreamline.isSelected
            ivStreamline.setImageResource(if (ivStreamline.isSelected) R.drawable.ic_log_expand else R.drawable.ic_log_streamline)
            viewModel.updateLogStreamlineState(ivStreamline.isSelected)
        }
    }

    override fun ActivityLoggerxPreviewBinding.initView() {
        val outPutters = LoggerX.getScopes()
        outPutters.forEach { tabLayout.addTab(tabLayout.newTab().setText(it)) }
        vp.addStateAdapter(supportFragmentManager, lifecycle, outPutters.size) { PreviewLogFragment.newInstance(it) }
    }

    override fun ActivityLoggerxPreviewBinding.initViewModelListener() {
    }
}