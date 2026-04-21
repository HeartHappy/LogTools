package com.hearthappy.loggerx

import com.hearthappy.loggerx.image.DefaultLogImageLoader
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ExampleUnitTest {
    @Test
    fun thumbnailCacheKey_shouldFollowMd5SizeQualityRule() {
        val key = DefaultLogImageLoader.buildThumbnailCacheKey(
            path = "D:/images/sample.png",
            width = 512,
            height = 512,
            quality = 60
        )
        assertTrue(key.matches(Regex("[a-f0-9]{32}_512x512_60")))
    }

    @Test
    fun originalCacheKey_shouldBeStableForSamePath() {
        val first = DefaultLogImageLoader.buildOriginalCacheKey("D:/images/sample.png")
        val second = DefaultLogImageLoader.buildOriginalCacheKey("D:/images/sample.png")
        assertEquals(first, second)
    }
}
