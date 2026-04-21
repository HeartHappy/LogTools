package com.hearthappy.loggerx.core

class FileLogWriteException(
    val userMessage: String,
    cause: Throwable? = null
) : RuntimeException(userMessage, cause)
