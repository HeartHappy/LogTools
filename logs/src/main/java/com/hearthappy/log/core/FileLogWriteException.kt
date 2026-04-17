package com.hearthappy.log.core

class FileLogWriteException(
    val userMessage: String,
    cause: Throwable? = null
) : RuntimeException(userMessage, cause)
