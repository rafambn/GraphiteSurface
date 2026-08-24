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
            val first = GraphiteSampleScene.prepare(runtime, GraphiteSize(100, 80))
            val second = GraphiteSampleScene.prepare(runtime, GraphiteSize(100, 80))

            assertNotSame(first, second)
            assertNotSame(first.displayList, second.displayList)
        } finally {
            runtime.close()
            runtime.awaitClosed()
        }
    }
}
