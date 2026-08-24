package com.rafambn.graphitesurface

import kotlin.test.Test
import kotlin.test.assertEquals

class GraphiteSurfaceApiTest {
    @Test
    fun stateForwardsRequestsToTheActiveSurface() {
        val state = GraphiteSurfaceState()
        var requestCount = 0
        val handler = { requestCount += 1 }

        state.setRequestFrameHandler(handler)
        state.requestFrame()
        state.requestFrame()

        assertEquals(2, requestCount)

        state.clearRequestFrameHandler(handler)
        state.requestFrame()
        assertEquals(2, requestCount)
    }

    @Test
    fun staleSurfaceCannotClearTheActiveRequestHandler() {
        val state = GraphiteSurfaceState()
        var firstSurfaceRequests = 0
        var secondSurfaceRequests = 0
        val firstHandler = { firstSurfaceRequests += 1 }
        val secondHandler = { secondSurfaceRequests += 1 }

        state.setRequestFrameHandler(firstHandler)
        state.setRequestFrameHandler(secondHandler)
        state.clearRequestFrameHandler(firstHandler)
        state.requestFrame()

        assertEquals(0, firstSurfaceRequests)
        assertEquals(1, secondSurfaceRequests)
    }

    @Test
    fun graphiteSizeIsLibraryOwnedAndRejectsNegativeDimensions() {
        assertEquals(GraphiteSize(640, 480), GraphiteSize(640, 480))

        kotlin.test.assertFailsWith<IllegalArgumentException> {
            GraphiteSize(-1, 480)
        }
    }
}
