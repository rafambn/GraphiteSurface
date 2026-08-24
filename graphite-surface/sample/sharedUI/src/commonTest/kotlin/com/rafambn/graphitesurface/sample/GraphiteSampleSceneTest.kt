package com.rafambn.graphitesurface.sample

import com.rafambn.graphitesurface.GraphiteEngine
import com.rafambn.graphitesurface.GraphiteSize
import kotlin.test.Test
import kotlin.test.assertNotSame
import kotlin.test.assertSame
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest

class GraphiteSampleSceneTest {
    @Test
    fun reusesPreparedSceneUntilItsRuntimeOrSizeChanges() = runTest {
        val firstRuntime = GraphiteEngine()
        val secondRuntime = GraphiteEngine()
        val scene = GraphiteSampleScene()
        try {
            val first = scene.prepare(firstRuntime, pixelSize = GraphiteSize(100, 80))
            val reused = scene.prepare(firstRuntime, pixelSize = GraphiteSize(100, 80))
            assertSame(first, reused)

            val resized = scene.prepare(
                firstRuntime,
                pixelSize = GraphiteSize(200, 160),
            )
            assertNotSame(first, resized)
            assertTrue(first.displayList.isClosed)

            val newRuntime = scene.prepare(
                secondRuntime,
                pixelSize = GraphiteSize(200, 160),
            )
            assertNotSame(resized, newRuntime)
            assertTrue(resized.displayList.isClosed)

            scene.close()
            assertTrue(newRuntime.displayList.isClosed)
        } finally {
            scene.close()
            firstRuntime.close()
            secondRuntime.close()
            firstRuntime.awaitClosed()
            secondRuntime.awaitClosed()
        }
    }
}
