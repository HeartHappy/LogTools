package com.hearthappy.loggerx.preview

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

class PreviewOperateViewModel : ViewModel() {

    //状态流
    private val _logStreamlineState = MutableStateFlow<Boolean>(false)
    val logShowState : StateFlow<Boolean> = _logStreamlineState


    fun updateLogStreamlineState(state : Boolean) {
        _logStreamlineState.value = state
    }
}