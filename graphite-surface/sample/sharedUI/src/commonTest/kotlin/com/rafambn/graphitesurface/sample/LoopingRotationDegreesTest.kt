package com.rafambn.graphitesurface.sample

import kotlin.test.Test
import kotlin.test.assertEquals

class LoopingRotationDegreesTest {
    @Test
    fun keepsRotationStableForLargeFrameTimes() {
        val manyRotations = 250_000_000L * 4_000_000_000L

        assertEquals(0f, loopingRotationDegrees(manyRotations))
        assertEquals(90f, loopingRotationDegrees(manyRotations + 1_000_000_000L))
        assertEquals(180f, loopingRotationDegrees(manyRotations + 2_000_000_000L))
    }

    @Test
    fun appliesRotationSpeedToElapsedTime() {
        assertEquals(45f, loopingRotationDegrees(500_000_000L))
        assertEquals(22.5f, loopingRotationDegrees(500_000_000L, 0.5f))
        assertEquals(90f, loopingRotationDegrees(500_000_000L, 2f))
    }
}
