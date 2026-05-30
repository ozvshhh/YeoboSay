package com.yeobosay.app.voice

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AudioLevelMathTest {
    @Test
    fun rmsPcm16_returnsZeroForEmptyInput() {
        val rms = AudioLevelMath.rmsPcm16(shortArrayOf(1, 2, 3), 0)

        assertEquals(0.0, rms, 0.0)
    }

    @Test
    fun rmsPcm16_normalizesPcmAmplitude() {
        val rms = AudioLevelMath.rmsPcm16(
            shortArrayOf(Short.MAX_VALUE, Short.MAX_VALUE),
            2,
        )

        assertEquals(1.0, rms, 0.0001)
    }

    @Test
    fun rmsPcm16_increasesWithLouderSamples() {
        val quiet = AudioLevelMath.rmsPcm16(shortArrayOf(256, -256), 2)
        val loud = AudioLevelMath.rmsPcm16(shortArrayOf(8_192, -8_192), 2)

        assertTrue(loud > quiet)
    }
}
