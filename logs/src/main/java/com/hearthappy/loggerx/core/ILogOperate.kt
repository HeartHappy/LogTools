package com.hearthappy.loggerx.core


/**
 * Created Date: 2026/3/27
 * @author ChenRui
 * ClassDescription：日志操作接口
 */
interface ILogOperate {

    //查询日志
    fun queryLogs(time: String? = null, tag: String? = null, level: String? = null, method: String? = null)

}