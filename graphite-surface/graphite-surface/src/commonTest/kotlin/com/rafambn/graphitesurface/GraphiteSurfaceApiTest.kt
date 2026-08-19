package com.rafambn.graphitesurface

import kotlin.test.Test
import kotlin.test.assertEquals

class GraphiteSurfaceApiTest {
    @Test
    fun controllerForwardsRequestsToTheActiveSurface() {
        val controller = GraphiteSurfaceController()
        var requestCount = 0

        controller.setRequestRenderHandler { requestCount += 1 }
        controller.requestRender()
        controller.requestRender()

        assertEquals(2, requestCount)

        controller.setRequestRenderHandler(null)
        controller.requestRender()
        assertEquals(2, requestCount)
    }

    @Test
    fun graphiteSizeIsLibraryOwnedAndRejectsNegativeDimensions() {
        assertEquals(GraphiteSize(640, 480), GraphiteSize(640, 480))

        kotlin.test.assertFailsWith<IllegalArgumentException> {
            GraphiteSize(-1, 480)
        }
    }
}
