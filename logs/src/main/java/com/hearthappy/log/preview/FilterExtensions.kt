package com.hearthappy.log.preview

import android.content.Context
import androidx.core.content.ContextCompat
import com.hearthappy.logs.R

/**
 * 过滤相关的扩展函数和工具
 */

/**
 * 检查 Level 值是否是特殊的（需要红色高亮）
 */
fun String?.isSpecialLevel(): Boolean {
    return this?.uppercase() in setOf("ERROR", "CRITICAL", "FATAL", "EXCEPTION")
}

/**
 * 将 Level 字符串标准化为大写
 */
fun String?.normalizeLevel(): String? {
    return this?.uppercase()
}

/**
 * 获取 Chip 的背景颜色（用于绘制自定义背景）
 */
fun Context.getChipBackgroundColor(selected: Boolean, isSpecial: Boolean): Int {
    return when {
        isSpecial && selected -> ContextCompat.getColor(this, R.color.chip_critical_selected)
        isSpecial -> ContextCompat.getColor(this, R.color.chip_critical_normal)
        selected -> ContextCompat.getColor(this, R.color.chip_selected)
        else -> ContextCompat.getColor(this, R.color.chip_normal)
    }
}

/**
 * 获取 Chip 的文本颜色
 */
fun Context.getChipTextColor(selected: Boolean): Int {
    return ContextCompat.getColor(
        this,
        if (selected) R.color.filter_text_selected else R.color.filter_text_normal
    )
}

/**
 * 将 FilterState 转换为可读的字符串（用于调试或显示）
 */
fun FilterState.toReadableString(): String {
    val parts = mutableListOf<String>()
    if (time.isNotEmpty()) parts.add("时间: ${time.joinToString(",")}")
    if (level.isNotEmpty()) parts.add("级别: ${level.joinToString(",")}")
    if (tag.isNotEmpty()) parts.add("标签: ${tag.joinToString(",")}")
    if (method.isNotEmpty()) parts.add("方法: ${method.joinToString(",")}")
    if (image.isNotEmpty()) parts.add("图片: 仅图片")
    return if (parts.isEmpty()) "无过滤条件" else parts.joinToString(" | ")
}

/**
 * 批量创建 FilterChipItem 列表
 */
fun List<String>.toFilterChipItems(isSpecialCheck: (String) -> Boolean = { false }): List<FilterChipItem> {
    return this.map { value ->
        FilterChipItem.fromString(value, isSpecialCheck(value))
    }
}
