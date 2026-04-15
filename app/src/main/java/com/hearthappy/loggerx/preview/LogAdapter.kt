package com.hearthappy.loggerx.preview

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.hearthappy.log.LoggerX
import com.hearthappy.log.core.LogLevel
import com.hearthappy.loggerx.databinding.ItemLogListBinding

class LogAdapter : ListAdapter<Map<String, Any>, LogAdapter.LogViewHolder>(LogDiffCallback) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): LogViewHolder {
        val binding = ItemLogListBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return LogViewHolder(binding)
    }

    override fun onBindViewHolder(holder: LogViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    fun submitLogs(logs: List<Map<String, Any>>) {
        // ListAdapter 内置 DiffUtil，数据量较大时可增量刷新避免全量闪烁
        submitList(logs.toList())
    }

    class LogViewHolder(private val binding: ItemLogListBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(data: Map<String, Any>) = with(binding) {
            tvTime.text = data[LoggerX.COLUMN_TIME].toString()
            tvLevel.text = data[LoggerX.COLUMN_LEVEL].toString()
            level2Color(data[LoggerX.COLUMN_LEVEL].toString())
            tvTag.text = data[LoggerX.COLUMN_TAG].toString()
            tvMethod.text = data[LoggerX.COLUMN_METHOD].toString()
            tvMessage.text = data[LoggerX.COLUMN_MESSAGE].toString()
        }

        private fun ItemLogListBinding.level2Color(level: String) {
            when (level) {
                LogLevel.VERBOSE.value -> tvLevel.setTextColor(0xFFBBBBBB.toInt())
                LogLevel.DEBUG.value -> tvLevel.setTextColor(0xFF33B5E5.toInt())
                LogLevel.INFO.value -> tvLevel.setTextColor(0xFF99CC00.toInt())
                LogLevel.WARN.value -> tvLevel.setTextColor(0xFFFFBB33.toInt())
                LogLevel.ERROR.value -> tvLevel.setTextColor(0xFFFF4444.toInt())
                else -> tvLevel.setTextColor(0xFFBBBBBB.toInt())
            }
        }
    }

    private object LogDiffCallback : DiffUtil.ItemCallback<Map<String, Any>>() {
        override fun areItemsTheSame(oldItem: Map<String, Any>, newItem: Map<String, Any>): Boolean {
            val oldId = oldItem[LoggerX.COLUMN_ID]?.toString()
            val newId = newItem[LoggerX.COLUMN_ID]?.toString()
            return oldId != null && oldId == newId
        }

        override fun areContentsTheSame(oldItem: Map<String, Any>, newItem: Map<String, Any>): Boolean {
            return oldItem == newItem
        }
    }
}
