package com.rafambn.graphitesurface.sample

import com.rafambn.graphitesurface.GraphiteRuntime
import com.rafambn.graphitesurface.GraphiteSize
import kotlin.test.Test
import kotlin.test.assertNotSame
import kotlin.test.assertSame
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest

class GraphiteSampleSceneTest {
    @Test
    fun reusesResourcesWithinAGenerationAndClosesTheReplacedList() = runTest {
        val runtime = GraphiteRuntime.create()
        val scene = GraphiteSampleScene()
        try {
            val first = scene.prepare(runtime, generation = 1, pixelSize = GraphiteSize(100, 80))
            val reused = scene.prepare(runtime, generation = 1, pixelSize = GraphiteSize(100, 80))
            assertSame(first, reused)

            val replacement = scene.prepare(
                runtime,
                generation = 2,
                pixelSize = GraphiteSize(200, 160),
            )
            assertNotSame(first, replacement)
            assertTrue(first.displayList.isClosed)

            scene.close()
            assertTrue(replacement.displayList.isClosed)
        } finally {
            scene.close()
            runtime.close()
            runtime.awaitClosed()
        }
    }
}
