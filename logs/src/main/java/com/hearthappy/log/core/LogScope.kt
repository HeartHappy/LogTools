package com.hearthappy.log.core


/**
 * 日志域（Scope）：新增域只需在此枚举添加，无需修改其他逻辑
 */
enum class LogScope(val tag: String) {
    COMMON("Common"), IMPORTANT("Important"), KERNEL("Kernel"), ERROR("Error")
}




