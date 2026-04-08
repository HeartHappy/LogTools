package com.hearthappy.logtools.preview

import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import com.hearthappy.basic.AbsBaseFragment
import com.hearthappy.basic.ext.addLastListener
import com.hearthappy.basic.ext.dp2px
import com.hearthappy.basic.ext.popupWindow
import com.hearthappy.log.Logger
import com.hearthappy.log.core.LogOutputter
import com.hearthappy.log.core.LogScopeProxy
import com.hearthappy.logtools.databinding.FragmentPreviewBinding
import com.hearthappy.logtools.databinding.PopFilterListBinding
import kotlin.math.log

class PreviewFragment: AbsBaseFragment<FragmentPreviewBinding>() {

    lateinit var scopeProxy: LogScopeProxy
    var page:Int=1
    override fun FragmentPreviewBinding.initData() {
    }

    override fun FragmentPreviewBinding.initListener() {
        swipeRefreshLayout.setOnRefreshListener {
            val logAdapter = rvLogList.adapter as LogAdapter
            val logs = scopeProxy.queryLogs()
            logAdapter.initData(logs)
            Toast.makeText(context, "Refresh completed", Toast.LENGTH_SHORT).show()
            swipeRefreshLayout.isRefreshing = false
        }
    }

    override fun FragmentPreviewBinding.initView(savedInstanceState: Bundle?) {
        val title = arguments?.getString("title")
        val index = arguments?.getInt("index") ?: 0
        viewBinding.apply {
            val outputter: LogOutputter = Logger.getOutputters()[index]
            scopeProxy = outputter.scope.getProxy()
            val logAdapter = LogAdapter(scopeProxy)
            tvTitle.text = title
            rvLogList.adapter = logAdapter
            addHeaderListener(logAdapter, scopeProxy, logAdapter)
            rvLogList.addLastListener {
                if (logAdapter.getItemRealCount() % 100 == 0) {
                    page++
                    Toast.makeText(context, "count:${logAdapter.getItemRealCount()}", Toast.LENGTH_SHORT).show()
                    val queryLogs = scopeProxy.queryLogs(page = this@PreviewFragment.page)
                    logAdapter.addData(queryLogs)
                }
            }
        }
    }

    private fun addHeaderListener(headerView: LogAdapter, scopeProxy: LogScopeProxy, logAdapter: LogAdapter) {
        headerView.timeListener = {
            showPopupFilter(it, scopeProxy, Logger.COLUMN_TIME) { time ->
                val filterTime = scopeProxy.queryLogs(time = time)
                logAdapter.initData(filterTime)
            }
        }
        headerView.levelListener = {
            showPopupFilter(it, scopeProxy, Logger.COLUMN_LEVEL) { levels ->
                val filterLevel = scopeProxy.queryLogs(level = levels)
                logAdapter.initData(filterLevel)
            }
        }

        headerView.tagListener = {
            showPopupFilter(it, scopeProxy, Logger.COLUMN_TAG) { tags ->
                val filterTag = scopeProxy.queryLogs(tag = tags)
                logAdapter.initData(filterTag)
            }
        }

        headerView.methodListener = {
            showPopupFilter(it, scopeProxy, Logger.COLUMN_METHOD) { methods ->
                val filterMethod = scopeProxy.queryLogs(method = methods)
                logAdapter.initData(filterMethod)
            }
        }
    }

    override fun FragmentPreviewBinding.initViewModelListener() {
    }


    fun showPopupFilter(view: View, scopeProxy: LogScopeProxy, name: String, block: (String) -> Unit) {

        val distinctValues = scopeProxy.getDistinctValues(name)

        popupWindow(viewBinding = PopFilterListBinding.inflate(layoutInflater), width = 180.dp2px(), height = ViewGroup.LayoutParams.WRAP_CONTENT, viewEventListener = { vb ->
            vb.apply {
                val filterAdapter = FilterAdapter()
                rvFilterList.adapter = filterAdapter
                filterAdapter.initData(distinctValues, true)
                filterAdapter.setOnItemClickListener { view, data, position, listPosition ->
                    block(data)
                    dismiss()
                }
            }
        }).showAsDropDown(view)
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