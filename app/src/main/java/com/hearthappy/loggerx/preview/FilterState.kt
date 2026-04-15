package com.hearthappy.loggerx.preview

import com.hearthappy.log.LoggerX

enum class FilterCategory(val columnName: String?, val title: String) {
    TIME(LoggerX.COLUMN_TIME, "time"),
    LEVEL(LoggerX.COLUMN_LEVEL, "level"),
    TAG(LoggerX.COLUMN_TAG, "tag"),
    METHOD(LoggerX.COLUMN_METHOD, "method");

    companion object {
        val filterable = listOf(TIME, LEVEL, TAG, METHOD)
    }
}

data class FilterState(
    val time: Set<String> = emptySet(),
    val level: Set<String> = emptySet(),
    val tag: Set<String> = emptySet(),
    val method: Set<String> = emptySet()
) {
    fun isAll(): Boolean {
        return time.isEmpty() && level.isEmpty() && tag.isEmpty() && method.isEmpty()
    }

    fun toggle(category: FilterCategory, value: String): FilterState {
        return when (category) {
            FilterCategory.TIME -> copy(time = time.toggleItem(value))
            FilterCategory.LEVEL -> copy(level = level.toggleItem(value))
            FilterCategory.TAG -> copy(tag = tag.toggleItem(value))
            FilterCategory.METHOD -> copy(method = method.toggleItem(value))
        }
    }

    fun selectedValues(category: FilterCategory): Set<String> {
        return when (category) {
            FilterCategory.TIME -> time
            FilterCategory.LEVEL -> level
            FilterCategory.TAG -> tag
            FilterCategory.METHOD -> method
        }
    }

    companion object {
        val EMPTY = FilterState()
    }
}

private fun Set<String>.toggleItem(value: String): Set<String> {
    return if (contains(value)) this - value else this + value
}

data class QueryParams(
    val time: String?,
    val level: String?,
    val tag: String?,
    val method: String?,
    val page: Int,
    val limit: Int?
)

object FilterQueryHelper {
    fun sanitizePage(page: Int): Int = if (page < 1) 1 else page

    fun sanitizeLimit(limit: Int?): Int? {
        if (limit == null) return null
        return if (limit <= 0) 100 else limit
    }

    fun buildQueryParams(
        state: FilterState,
        page: Int = 1,
        limit: Int? = 100
    ): QueryParams {
        return QueryParams(
            time = state.time.singleOrNull(),
            level = state.level.singleOrNull(),
            tag = state.tag.singleOrNull(),
            method = state.method.singleOrNull(),
            page = sanitizePage(page),
            limit = sanitizeLimit(limit)
        )
    }

    fun matches(log: Map<String, Any>, state: FilterState): Boolean {
        val timeValue = log[LoggerX.COLUMN_TIME]?.toString()
        val levelValue = log[LoggerX.COLUMN_LEVEL]?.toString()
        val tagValue = log[LoggerX.COLUMN_TAG]?.toString()
        val methodValue = log[LoggerX.COLUMN_METHOD]?.toString()
        return matchTime(timeValue, state.time) &&
            matchSet(levelValue, state.level) &&
            matchSet(tagValue, state.tag) &&
            matchMethod(methodValue, state.method)
    }

    private fun matchSet(actual: String?, selected: Set<String>): Boolean {
        if (selected.isEmpty()) return true
        return actual != null && selected.contains(actual)
    }

    private fun matchTime(actual: String?, selected: Set<String>): Boolean {
        if (selected.isEmpty()) return true
        if (actual.isNullOrEmpty()) return false
        return selected.any { actual.startsWith(it) }
    }

    private fun matchMethod(actual: String?, selected: Set<String>): Boolean {
        if (selected.isEmpty()) return true
        if (actual.isNullOrEmpty()) return false
        return selected.any { candidate ->
            actual == candidate || actual.startsWith(candidate) || actual.contains(candidate)
        }
    }
}
