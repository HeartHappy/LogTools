package com.hearthappy.loggerx.image

import android.content.Context
import com.hearthappy.loggerx.core.ContextHolder

object LogImageLoaderFactory {
    private const val HOST_IMPL_SUFFIX = ".LogImageLoaderImpl"

    @Volatile
    private var loader: ILogImageLoader? = null

    @Volatile
    private var defaultLoader: DefaultLogImageLoader? = null

    fun get(context: Context = ContextHolder.getAppContext()): ILogImageLoader {
        loader?.let { return it }
        return synchronized(this) {
            loader ?: createLoader(context.applicationContext).also { created ->
                loader = created
                if (created is DefaultLogImageLoader) {
                    defaultLoader = created
                }
            }
        }
    }

    fun pauseDecode() {
        (loader as? DecodeControllable)?.pauseDecode()
    }

    fun resumeDecode() {
        (loader as? DecodeControllable)?.resumeDecode()
    }

//    fun clearCache() {
//        loader?.clearCache()
//    }

    fun snapshot(): LogImageLoadStatsSnapshot? {
        val current = loader ?: return null
        return (current as? ILogImageLoaderDiagnostics)?.snapshot()
    }

    internal fun buildHostClassName(context: Context): String {
        return context.packageName + HOST_IMPL_SUFFIX
    }

    private fun createLoader(context: Context): ILogImageLoader {
        val hostLoader = instantiateHostLoader(context)
        return hostLoader ?: DefaultLogImageLoader(context)
    }

    private fun instantiateHostLoader(context: Context): ILogImageLoader? {
        val className = buildHostClassName(context)
        return runCatching {
            val clazz = Class.forName(className)
            val contextCtor = clazz.constructors.firstOrNull { constructor ->
                val params = constructor.parameterTypes
                params.size == 1 && Context::class.java.isAssignableFrom(params[0])
            }
            val instance = when {
                contextCtor != null -> contextCtor.newInstance(context)
                else -> clazz.getDeclaredConstructor().newInstance()
            }
            instance as? ILogImageLoader
        }.getOrNull()
    }
}

interface DecodeControllable {
    fun pauseDecode()
    fun resumeDecode()
}
