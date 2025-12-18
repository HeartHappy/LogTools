package com.hearthappy.log.core


/**
 * 日志域（Scope）核心接口：开放扩展能力
 * 原有枚举的替代方案，支持自定义实现
 */
interface LogScope {
    // 获取Scope的标签（用于日志文件命名/格式化）
    fun getTag(): String
}




