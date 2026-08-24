package com.rafambn.graphitesurface.sample

import com.rafambn.graphitesurface.GraphiteSize
import kotlin.test.Test
import kotlin.test.assertNotSame

class GraphiteSampleSceneTest {
    @Test
    fun preparesANewSceneForEachCall() {
        val first = prepareGraphiteSampleScene(GraphiteSize(100, 80))
        val second = prepareGraphiteSampleScene(GraphiteSize(100, 80))

        assertNotSame(first, second)
    }
}
