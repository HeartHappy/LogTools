package com.hearthappy.log.core

class LruTtlCache<K, V>(
    private val maxSize: Int,
    private val ttlMs: Long
) {
    private data class CacheEntry<V>(val value: V, val expiresAtMs: Long)

    private val entries = object : LinkedHashMap<K, CacheEntry<V>>(maxSize, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<K, CacheEntry<V>>): Boolean {
            return size > maxSize
        }
    }

    @Synchronized
    fun put(key: K, value: V) {
        entries[key] = CacheEntry(value, System.currentTimeMillis() + ttlMs)
    }

    @Synchronized
    fun get(key: K): V? {
        val entry = entries[key] ?: return null
        if (entry.expiresAtMs < System.currentTimeMillis()) {
            entries.remove(key)
            return null
        }
        return entry.value
    }

    @Synchronized
    fun clear() {
        entries.clear()
    }
}
