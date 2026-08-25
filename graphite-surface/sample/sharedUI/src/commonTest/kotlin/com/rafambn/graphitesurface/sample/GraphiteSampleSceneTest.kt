package com.rafambn.graphitesurface.sample

import androidx.compose.ui.unit.IntSize
import kotlin.test.Test
import kotlin.test.assertNotSame

class GraphiteSampleSceneTest {
    @Test
    fun preparesANewSceneForEachCall() {
        val first = prepareGraphiteSampleScene(IntSize(100, 80))
        val second = prepareGraphiteSampleScene(IntSize(100, 80))

        assertNotSame(first, second)
    }
}
