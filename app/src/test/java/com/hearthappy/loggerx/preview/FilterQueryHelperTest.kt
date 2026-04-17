package com.hearthappy.loggerx.preview

import com.hearthappy.log.LoggerX
import com.hearthappy.log.preview.FilterQueryHelper
import com.hearthappy.log.preview.FilterState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FilterQueryHelperTest {

    @Test
    fun `sanitize page and limit should fallback safely`() {
        assertEquals(1, FilterQueryHelper.sanitizePage(0))
        assertEquals(1, FilterQueryHelper.sanitizePage(Int.MIN_VALUE))
        assertEquals(Int.MAX_VALUE, FilterQueryHelper.sanitizePage(Int.MAX_VALUE))
        assertEquals(100, FilterQueryHelper.sanitizeLimit(0))
        assertEquals(100, FilterQueryHelper.sanitizeLimit(-2))
        assertEquals(null, FilterQueryHelper.sanitizeLimit(null))
    }

    @Test
    fun `matches should support combined conditions`() {
        val state = FilterState(time = setOf("2026-04-14"), level = setOf("DEBUG", "INFO"), tag = setOf("MainActivity"), method = setOf("outLogAndFile"))
        val hit = mapOf<String, Any>(
            LoggerX.COLUMN_TIME to "2026-04-14 09:26:59",
            LoggerX.COLUMN_LEVEL to "DEBUG",
            LoggerX.COLUMN_TAG to "MainActivity",
            LoggerX.COLUMN_METHOD to "outLogAndFile\$lambda\$1(70)"
        )
        val miss = hit.toMutableMap().apply {
            put(LoggerX.COLUMN_LEVEL, "ERROR")
        }
        assertTrue(FilterQueryHelper.matches(hit, state))
        assertFalse(FilterQueryHelper.matches(miss, state))
    }

    @Test
    fun `build params should keep single value and fallback for multi select`() {
        val single = FilterState(time = setOf("2026-04-14"), level = setOf("DEBUG"), tag = setOf("MainActivity"), method = setOf("method"))
        val multi = FilterState(level = setOf("DEBUG", "INFO"))
        val singleParams = FilterQueryHelper.buildQueryParams(single, page = 1, limit = 100)
        val multiParams = FilterQueryHelper.buildQueryParams(multi, page = Int.MAX_VALUE, limit = 0)
        assertEquals("2026-04-14", singleParams.time)
        assertEquals("DEBUG", singleParams.level)
        assertEquals("MainActivity", singleParams.tag)
        assertEquals("method", singleParams.method)
        assertEquals(null, multiParams.level)
        assertEquals(Int.MAX_VALUE, multiParams.page)
        assertEquals(100, multiParams.limit)
    }
}
