package com.hearthappy.loggerx.preview

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.hearthappy.log.core.LogScopeProxy
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class PreviewLogUiState(
    val logs: List<Map<String, Any>> = emptyList(),
    val loading: Boolean = false
)

data class DistinctValuesUiState(
    val values: Map<FilterCategory, List<String>> = emptyMap(),
    val disabledCategories: Set<FilterCategory> = emptySet(),
    val loading: Boolean = false
)

class PreviewViewModel(private val scopeProxy: LogScopeProxy) : ViewModel() {
    private val _appliedState = MutableStateFlow(FilterState.EMPTY)
    val appliedState: StateFlow<FilterState> = _appliedState.asStateFlow()

    private val _draftState = MutableStateFlow(FilterState.EMPTY)
    val draftState: StateFlow<FilterState> = _draftState.asStateFlow()

    private val _activeCategory = MutableStateFlow(FilterCategory.ALL)
    val activeCategory: StateFlow<FilterCategory> = _activeCategory.asStateFlow()

    private val _distinctValues = MutableStateFlow(DistinctValuesUiState())
    val distinctValues: StateFlow<DistinctValuesUiState> = _distinctValues.asStateFlow()

    private val _logUiState = MutableStateFlow(PreviewLogUiState())
    val logUiState: StateFlow<PreviewLogUiState> = _logUiState.asStateFlow()

    private val _toastEvent = MutableSharedFlow<String>(extraBufferCapacity = 1)
    val toastEvent: SharedFlow<String> = _toastEvent.asSharedFlow()

    fun loadInitialLogs() {
        queryWithState(_appliedState.value)
    }

    fun refreshAppliedLogs() {
        queryWithState(_appliedState.value)
    }

    fun startFilterEditing() {
        _draftState.value = _appliedState.value
        _activeCategory.value = FilterCategory.ALL
        refreshDistinctValues()
    }

    fun setActiveCategory(category: FilterCategory) {
        _activeCategory.value = category
    }

    fun toggleSelection(category: FilterCategory, value: String?) {
        if (_distinctValues.value.disabledCategories.contains(category)) return
        _draftState.update { it.toggle(category, value) }
        queryWithState(_draftState.value)
    }

    fun resetDraft() {
        _draftState.value = FilterState.EMPTY
        _activeCategory.value = FilterCategory.ALL
        queryWithState(_draftState.value)
    }

    fun confirmDraft() {
        _appliedState.value = _draftState.value
        queryWithState(_appliedState.value)
    }

    fun cancelDraft() {
        _draftState.value = _appliedState.value
        queryWithState(_appliedState.value)
    }

    fun clearDistinctCache() {
        _distinctValues.value = DistinctValuesUiState()
    }

    private fun refreshDistinctValues() {
        viewModelScope.launch {
            _distinctValues.update { it.copy(loading = true, values = emptyMap(), disabledCategories = emptySet()) }
            val values = mutableMapOf<FilterCategory, List<String>>()
            val disabled = mutableSetOf<FilterCategory>()
            var hasError = false
            withContext(Dispatchers.IO) {
                FilterCategory.filterable.forEach { category ->
                    try {
                        val result = if (category == FilterCategory.IMAGE) {
                            listOf(FilterCategory.IMAGE_ONLY_VALUE)
                        } else {
                            scopeProxy.getDistinctValues(category.columnName.orEmpty())
                        }
                        values[category] = result
                    } catch (_: Exception) {
                        hasError = true
                        disabled.add(category)
                    }
                }
            }
            _distinctValues.value = DistinctValuesUiState(
                values = values,
                disabledCategories = disabled,
                loading = false
            )
            if (hasError) {
                _toastEvent.tryEmit("获取筛选条件失败")
            }
        }
    }

    private fun queryWithState(state: FilterState) {
        viewModelScope.launch {
            _logUiState.update { it.copy(loading = true) }
            val params = FilterQueryHelper.buildQueryParams(state, page = 1, limit = 100)
            val queried = withContext(Dispatchers.IO) {
                scopeProxy.queryLogs(
                    time = params.time,
                    tag = params.tag,
                    level = params.level,
                    method = params.method,
                    isImage = params.isImage,
                    keyword = null,
                    isAsc = false,
                    page = params.page,
                    limit = params.limit
                )
            }
            // 多选时底层 SQL 无法直接 in 查询，补一层内存过滤确保组合结果准确
            val result = queried.filter { FilterQueryHelper.matches(it, state) }
            _logUiState.value = PreviewLogUiState(
                logs = result,
                loading = false
            )
        }
    }

    class Factory(private val scopeProxy: LogScopeProxy) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return PreviewViewModel(scopeProxy) as T
        }
    }
}
