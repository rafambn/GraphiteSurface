package com.rafambn.graphitesurface.sample

import com.rafambn.graphitesurface.GraphiteEngine
import com.rafambn.graphitesurface.GraphiteSize
import kotlin.test.Test
import kotlin.test.assertNotSame
import kotlinx.coroutines.test.runTest

class GraphiteSampleSceneTest {
    @Test
    fun preparesANewSceneForEachCall() = runTest {
        val runtime = GraphiteEngine()
        try {
            val (firstTarget, firstDisplayList) =
                prepareGraphiteSampleScene(runtime, GraphiteSize(100, 80))
            val (secondTarget, secondDisplayList) =
                prepareGraphiteSampleScene(runtime, GraphiteSize(100, 80))

            assertNotSame(firstTarget, secondTarget)
            assertNotSame(firstDisplayList, secondDisplayList)
        } finally {
            runtime.close()
            runtime.awaitClosed()
        }
    }
}
