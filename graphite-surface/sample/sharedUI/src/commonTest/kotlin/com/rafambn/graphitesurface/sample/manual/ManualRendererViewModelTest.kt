package com.rafambn.graphitesurface.sample.manual

import com.rafambn.graphitesurface.GraphiteRenderMode
import com.rafambn.graphitesurface.GraphiteEngineState
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlinx.coroutines.test.runTest

class ManualRendererViewModelTest {
    @Test
    fun publishesManualRendererAndClosesItsRuntime() = runTest {
        val viewModel = ManualRendererViewModel()
        val renderer = assertNotNull(viewModel.renderer)
        try {
            assertNull(viewModel.error.value)
            assertEquals(GraphiteRenderMode.Manual, renderer.renderMode)

            viewModel.onCleared()
            renderer.runtime.awaitClosed()
            assertEquals(GraphiteEngineState.Closed, renderer.runtime.state.value)
        } finally {
            viewModel.onCleared()
            renderer.runtime.awaitClosed()
        }
    }
}
