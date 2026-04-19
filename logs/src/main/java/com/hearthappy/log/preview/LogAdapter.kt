package com.hearthappy.log.preview

import android.graphics.drawable.GradientDrawable
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.graphics.ColorUtils
import androidx.core.graphics.alpha
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.hearthappy.basic.ext.dp2px
import com.hearthappy.log.LoggerX
import com.hearthappy.log.core.LogLevel
import com.hearthappy.log.image.LogImageLoaderFactory
import com.hearthappy.logs.databinding.ItemLogListBinding

class LogAdapter(private val onImageClick : (Int) -> Unit = {}) : ListAdapter<Map<String, Any>, LogAdapter.LogViewHolder>(LogDiffCallback) {

    var isSimplified = false

    init {
        setHasStableIds(true)
    }

    override fun onCreateViewHolder(parent : ViewGroup, viewType : Int) : LogViewHolder {
        val binding = ItemLogListBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return LogViewHolder(binding)
    }

    override fun onBindViewHolder(holder : LogViewHolder, position : Int) {
        holder.bind(getItem(position), onImageClick, isSimplified)
    }

    override fun onViewRecycled(holder : LogViewHolder) {
        holder.unbind()
        super.onViewRecycled(holder)
    }

    override fun getItemId(position : Int) : Long {
        return getItem(position)[LoggerX.COLUMN_ID]?.toString()?.toLongOrNull() ?: RecyclerView.NO_ID
    }

    fun submitLogs(logs : List<Map<String, Any>>) { // ListAdapter 内置 DiffUtil，数据量较大时可增量刷新避免全量闪烁
        submitList(logs.toList())
    }

    class LogViewHolder(private val binding : ItemLogListBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(data : Map<String, Any>, onImageClick : (Int) -> Unit, isSimplified : Boolean) = with(binding) {
            tvId.text= String.format("ID: %s", data[LoggerX.COLUMN_ID])
            tvTime.text = data[LoggerX.COLUMN_TIME].toString()
            tvLevel.text = data[LoggerX.COLUMN_LEVEL].toString()
            level2Color(data[LoggerX.COLUMN_LEVEL].toString())
            tvTag.text = data[LoggerX.COLUMN_TAG].toString()
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

                setOnClickListener { data[LoggerX.COLUMN_ID]?.toString()?.toIntOrNull()?.let(onImageClick) }
            }) {
                tag = null
                setImageDrawable(null)
                setOnClickListener(null)
            }
            ivImageThumb.layoutParams.apply {
                height = if (isSimplified) 36.dp2px() else 64.dp2px()
            }

            tvTag.show(!isSimplified)
            tvTime.show(!isSimplified)
            tvLevel.show(!isSimplified)
            tvMethod.show(!isSimplified)
        }

        fun unbind() = with(binding.ivImageThumb) {
            tag = null
            setImageDrawable(null)
            setOnClickListener(null)
        }

        fun <T : View> T.show(show : Boolean, showBlock : T.() -> Unit = {}, hide : T.() -> Unit = {}) {
            if (show) {
                showBlock()
                if (visibility != View.VISIBLE) visibility = View.VISIBLE
            } else {
                hide()
                if (visibility != View.GONE) visibility = View.GONE
            }
        }

        private fun ItemLogListBinding.level2Color(level: String) {
            // 1. 先获取对应的颜色值 (复用你之前的逻辑)
            val baseColor = when (level) {
                LogLevel.VERBOSE.value -> 0xFFBBBBBB.toInt()
                LogLevel.DEBUG.value -> 0xFF33B5E5.toInt()
                LogLevel.INFO.value -> 0xFF99CC00.toInt()
                LogLevel.WARN.value -> 0xFFFFBB33.toInt()
                LogLevel.ERROR.value -> 0xFFFF4444.toInt()
                else -> 0xFFBBBBBB.toInt()
            }
        tvLevel.setTextColor(baseColor)
            // 2. 计算 60% 透明度的颜色
            // 0.6f 转换为 16进制 Alpha 通道大约是 0x99 (255 * 0.6 ≈ 153)
            val colorWithAlpha = ColorUtils.setAlphaComponent(baseColor, (255 * 0.3f).toInt())
            val borderColor = ColorUtils.setAlphaComponent(baseColor, (255 * 0.6f).toInt())
            // 3. 创建 GradientDrawable (对应 Shape)
            val drawable = GradientDrawable()
            drawable.shape = GradientDrawable.RECTANGLE // 对应 <shape>
            drawable.cornerRadius = 4f.dp2px() // 对应 android:radius="4dp"
            drawable.setColor(colorWithAlpha) // 设置填充色
            drawable.setStroke(1.dp2px(),borderColor)

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

        override fun areContentsTheSame(oldItem : Map<String, Any>, newItem : Map<String, Any>) : Boolean {
            // 1. 先比较大小
            if (oldItem.size != newItem.size) return false

            // 2. 遍历比较每个键值对
            for ((key, value) in oldItem) {
                val otherValue = newItem[key]
                // 注意：这里假设 value 和 otherValue 能够正确处理 equals
                if (value != otherValue) {
                    return false
                }
            }
            return true
        }
    }


    companion object {
        private const val DEFAULT_REQUEST_EDGE = 512
    }
}
