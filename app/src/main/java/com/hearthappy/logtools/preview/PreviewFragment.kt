package com.hearthappy.logtools.preview

import android.os.Bundle
import android.view.ViewGroup
import com.hearthappy.basic.AbsBaseFragment
import com.hearthappy.basic.ext.dp2px
import com.hearthappy.basic.ext.popupWindow
import com.hearthappy.basic.ext.showAtCenter
import com.hearthappy.log.Logger
import com.hearthappy.log.core.LogScopeProxy
import com.hearthappy.logtools.databinding.FragmentPreviewBinding
import com.hearthappy.logtools.databinding.PopFilterListBinding

class PreviewFragment: AbsBaseFragment<FragmentPreviewBinding>() {
    override fun FragmentPreviewBinding.initData() {
    }

    override fun FragmentPreviewBinding.initListener() {
    }

    override fun FragmentPreviewBinding.initView(savedInstanceState: Bundle?) {
        val title = arguments?.getString("title")
        val index = arguments?.getInt("index") ?: 0
        viewBinding.apply {

            val outputter = Logger.getOutputters()[index]
            val scopeProxy = outputter.scope.getProxy()
            val queryLogs = scopeProxy.queryLogs()
            val data = queryLogs.map { it.keys }.first().toList()
            val logAdapter = LogAdapter()
            val headerView = HeaderView(scopeProxy, data)
            tvTitle.text = title
            rvLogList.adapter = logAdapter
            addHeaderListener(headerView, scopeProxy, logAdapter)
            logAdapter.addHeaderView(headerView)
            logAdapter.initData(queryLogs)
        }
    }

    private fun addHeaderListener(headerView: HeaderView, scopeProxy: LogScopeProxy, logAdapter: LogAdapter) {
        headerView.timeListener = {
            showPopupFilter(scopeProxy, Logger.COLUMN_TIME) {
                val filterTime = scopeProxy.queryLogs(time = it)
                logAdapter.initData(filterTime)
            }
        }
        headerView.levelListener = {
            showPopupFilter(scopeProxy, Logger.COLUMN_LEVEL) { levels ->
                val filterLevel = scopeProxy.queryLogs(level = levels)
                logAdapter.initData(filterLevel)
            }
        }

        headerView.tagListener = {
            showPopupFilter(scopeProxy, Logger.COLUMN_TAG) { tags ->
                val filterTag = scopeProxy.queryLogs(tag = tags)
                logAdapter.initData(filterTag)
            }
        }

        headerView.methodListener = {
            showPopupFilter(scopeProxy, Logger.COLUMN_METHOD) { methods ->
                val filterMethod = scopeProxy.queryLogs(method = methods)
                logAdapter.initData(filterMethod)
            }
        }
    }

    override fun FragmentPreviewBinding.initViewModelListener() {
    }


    fun showPopupFilter(scopeProxy: LogScopeProxy, name: String, block: (String) -> Unit) {

        val distinctValues = scopeProxy.getDistinctValues(name)

        popupWindow(viewBinding = PopFilterListBinding.inflate(layoutInflater), width = 180.dp2px(), height = ViewGroup.LayoutParams.WRAP_CONTENT, viewEventListener = { vb ->
            vb.apply {
                val filterAdapter = FilterAdapter()
                rvFilterList.adapter = filterAdapter
                filterAdapter.initData(distinctValues)
                filterAdapter.setOnItemClickListener { view, data, position, listPosition ->
                    block(data)
                    dismiss()
                }
            }
        }).showAtCenter(viewBinding.root)
    }

    companion object {
        fun newInstance(i: Int, title: String): PreviewFragment {
            return PreviewFragment().apply {
                arguments = Bundle().apply {
                    putInt("index", i)
                    putString("title", title)
                }
            }
        }
    }

}