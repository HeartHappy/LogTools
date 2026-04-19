package com.hearthappy.log.preview

import android.content.Context
import android.content.Intent
import android.graphics.drawable.GradientDrawable
import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.graphics.ColorUtils
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.hearthappy.basic.ext.dp2px
import com.hearthappy.basic.ext.show
import com.hearthappy.log.LoggerX
import com.hearthappy.log.core.LogLevel
import com.hearthappy.log.image.LogImageLoaderFactory
import com.hearthappy.logs.databinding.ItemLogListBinding
import com.hearthappy.logs.databinding.ItemLogStreamlineListBinding

class LogAdapter(val context : Context, val outPutterIndex : Int) : ListAdapter<Map<String, Any>, RecyclerView.ViewHolder>(LogDiffCallback) {

    var isSimplified = false

    init {
        setHasStableIds(true)
    }

    override fun onCreateViewHolder(parent : ViewGroup, viewType : Int) : RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return if (viewType == TYPE_SIMPLIFIED) {
            val binding = ItemLogStreamlineListBinding.inflate(inflater, parent, false)
            SimplifiedLogViewHolder(binding)
        } else {
            val binding = ItemLogListBinding.inflate(inflater, parent, false)
            FullLogViewHolder(binding)
        }
    }

    override fun onBindViewHolder(holder : RecyclerView.ViewHolder, position : Int) {
        val item = getItem(position)
        when (holder) {
            is FullLogViewHolder -> holder.bind(item)
            is SimplifiedLogViewHolder -> holder.bind(item)
        }
    }

    override fun onViewRecycled(holder : RecyclerView.ViewHolder) {
        if (holder is FullLogViewHolder) holder.unbind()
        if (holder is SimplifiedLogViewHolder) holder.unbind()
        super.onViewRecycled(holder)
    }

    override fun getItemId(position : Int) : Long {
        return getItem(position)[LoggerX.COLUMN_ID]?.toString()?.toLongOrNull() ?: RecyclerView.NO_ID
    }

    fun submitLogs(logs : List<Map<String, Any>>) { // ListAdapter 内置 DiffUtil，数据量较大时可增量刷新避免全量闪烁
        submitList(logs.toList())
    }

    override fun getItemViewType(position : Int) : Int {
        return if (isSimplified) TYPE_SIMPLIFIED else TYPE_FULL
    }

    inner class FullLogViewHolder(private val binding : ItemLogListBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(data : Map<String, Any>) = with(binding) {
            tvId.text = String.format("%s", data[LoggerX.COLUMN_ID])
            tvTime.text = data[LoggerX.COLUMN_TIME].toString()
            tvLevel.text = data[LoggerX.COLUMN_LEVEL].toString()
            level2Color(tvLevel, data[LoggerX.COLUMN_LEVEL].toString())
            tvTag.text = String.format(">_  %s", data[LoggerX.COLUMN_TAG])
            tvMethod.text = data[LoggerX.COLUMN_METHOD].toString()
            tvMessage.text = data[LoggerX.COLUMN_MESSAGE].toString()

            val isImage = LogImageUiHelper.isImageLog(data)
            val filePath = LogImageUiHelper.resolveFilePath(data)
            ivImageThumb.show(isImage, showBlock = {
                val requestPath = filePath.ifBlank { null }
                this.setImageDrawable(null)
                tag = requestPath
                if (requestPath != null) {
                    LogImageLoaderFactory.get(context).loadThumbnail(this, path = requestPath)
                }

                setOnClickListener {
                    data[LoggerX.COLUMN_ID]?.toString()?.toIntOrNull()?.let { logId ->
                        context.startActivity(Intent(context, PreviewLargeImageActivity::class.java).apply {
                            putExtra(PreviewLargeImageActivity.ARG_OUTPUTTER_INDEX, outPutterIndex)
                            putExtra(PreviewLargeImageActivity.ARG_LOG_ID, logId)
                        })
                    }
                }
            }) {
                tag = null
                setImageDrawable(null)
                setOnClickListener(null)
            }
        }

        fun unbind() = with(binding.ivImageThumb) {
            tag = null
            setImageDrawable(null)
            setOnClickListener(null)
        }


    }

    inner class SimplifiedLogViewHolder(private val binding : ItemLogStreamlineListBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(data : Map<String, Any>) = with(binding) {
            tvId.text = String.format("%s", data[LoggerX.COLUMN_ID])
            tvTime.text = data[LoggerX.COLUMN_TIME].toString()
            tvLevel.text = data[LoggerX.COLUMN_LEVEL].toString()
            level2Color(tvLevel, data[LoggerX.COLUMN_LEVEL].toString(),false)
            val isImage = LogImageUiHelper.isImageLog(data)
            val filePath = LogImageUiHelper.resolveFilePath(data)
            ivImageThumb.show(isImage, showBlock = {
                val requestPath = filePath.ifBlank { null }
                this.setImageDrawable(null)
                tag = requestPath
                if (requestPath != null) {
                    LogImageLoaderFactory.get(context).loadThumbnail(this, path = requestPath)
                }

                setOnClickListener {
                    data[LoggerX.COLUMN_ID]?.toString()?.toIntOrNull()?.let { logId ->
                        context.startActivity(Intent(context, PreviewLargeImageActivity::class.java).apply {
                            putExtra(PreviewLargeImageActivity.ARG_OUTPUTTER_INDEX, outPutterIndex)
                            putExtra(PreviewLargeImageActivity.ARG_LOG_ID, logId)
                        })
                    }
                }
            }) {
                tag = null
                setImageDrawable(null)
                setOnClickListener(null)
            }
            tvMessage.text = data[LoggerX.COLUMN_MESSAGE].toString()
        }

        fun unbind() = with(binding.ivImageThumb) {
            tag = null
            setImageDrawable(null)
            setOnClickListener(null)
        }
    }

    private fun level2Color(tvLevel : TextView, level : String,isBackground: Boolean=true) { // 1. 先获取对应的颜色值 (复用你之前的逻辑)
        val baseColor = when (level) {
            LogLevel.VERBOSE.value -> 0xFFBBBBBB.toInt()
            LogLevel.DEBUG.value -> 0xFF33B5E5.toInt()
            LogLevel.INFO.value -> 0xFF99CC00.toInt()
            LogLevel.WARN.value -> 0xFFFFBB33.toInt()
            LogLevel.ERROR.value -> 0xFFFF4444.toInt()
            else -> 0xFFBBBBBB.toInt()
        }
        tvLevel.setTextColor(baseColor) // 2. 计算 60% 透明度的颜色
        // 0.6f 转换为 16进制 Alpha 通道大约是 0x99 (255 * 0.6 ≈ 153)
       if(isBackground){
           val colorWithAlpha = ColorUtils.setAlphaComponent(baseColor, (255 * 0.3f).toInt())
           val borderColor = ColorUtils.setAlphaComponent(baseColor, (255 * 0.6f).toInt()) // 3. 创建 GradientDrawable (对应 Shape)
           val drawable = GradientDrawable()
           drawable.shape = GradientDrawable.RECTANGLE // 对应 <shape>
           drawable.cornerRadius = 50f.dp2px() // 对应 android:radius="4dp"
           drawable.setColor(colorWithAlpha) // 设置填充色
           drawable.setStroke(1.dp2px(), borderColor)

           // 4. 设置给 View
           // 注意：这里假设你的 View 是 tvLevel，如果不是请替换
           tvLevel.background = drawable
       }
    }

    private object LogDiffCallback : DiffUtil.ItemCallback<Map<String, Any>>() {
        override fun areItemsTheSame(oldItem : Map<String, Any>, newItem : Map<String, Any>) : Boolean {
            val oldId = oldItem[LoggerX.COLUMN_ID]?.toString()
            val newId = newItem[LoggerX.COLUMN_ID]?.toString()
            return oldId != null && oldId == newId
        }

        override fun areContentsTheSame(oldItem : Map<String, Any>, newItem : Map<String, Any>) : Boolean { // 1. 先比较大小
            if (oldItem.size != newItem.size) return false

            // 2. 遍历比较每个键值对
            for ((key, value) in oldItem) {
                val otherValue = newItem[key] // 注意：这里假设 value 和 otherValue 能够正确处理 equals
                if (value != otherValue) {
                    return false
                }
            }
            return true
        }
    }

    companion object {
        private const val TYPE_FULL = 0
        private const val TYPE_SIMPLIFIED = 1
    }
}
