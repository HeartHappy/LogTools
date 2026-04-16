package com.hearthappy.log.core

class ImageLogWriteException(
    message: String,
    val compressionLog: String,
    cause: Throwable? = null
) : RuntimeException(message, cause)
