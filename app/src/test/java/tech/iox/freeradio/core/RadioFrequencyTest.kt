package tech.iox.freeradio.core

import org.junit.Assert.assertEquals
import org.junit.Test

class RadioFrequencyTest {
    @Test
    fun stepMovesByOneTenthOfAMegahertz() {
        assertEquals(98.5f, RadioFrequency.step(98.4f, 1), 0.001f)
        assertEquals(98.3f, RadioFrequency.step(98.4f, -1), 0.001f)
    }

    @Test
    fun frequencyIsClampedToTheFmBand() {
        assertEquals(RadioFrequency.MIN_MHZ, RadioFrequency.step(87.5f, -1), 0.001f)
        assertEquals(RadioFrequency.MAX_MHZ, RadioFrequency.step(108.0f, 1), 0.001f)
    }

    @Test
    fun fractionMapsTheBandToZeroAndOne() {
        assertEquals(0f, RadioFrequency.fraction(87.5f), 0.001f)
        assertEquals(1f, RadioFrequency.fraction(108.0f), 0.001f)
    }
}
