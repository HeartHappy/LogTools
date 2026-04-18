package com.hearthappy.log.core


/**
 * Created Date: 2026/3/20
 * @author ChenRui
 * ClassDescription：输出配置
 *
 */
data class OutputConfig(
    var isLog: Boolean = true,
    var isWriteDatabase: Boolean = true,
    var storageDirPath: String? = null //图片文件存储目录
)
