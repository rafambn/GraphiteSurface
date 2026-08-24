package com.rafambn.graphitesurface.sample.continuous

import com.rafambn.graphitesurface.GraphiteRenderMode
import com.rafambn.graphitesurface.GraphiteRuntimeState
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlinx.coroutines.test.runTest

class ContinuousRendererViewModelTest {
    @Test
    fun publishesContinuousRendererAndClosesItsRuntime() = runTest {
        val viewModel = ContinuousRendererViewModel()
        val renderer = assertNotNull(viewModel.renderer)
        try {
            assertNull(viewModel.error.value)
            assertEquals(GraphiteRenderMode.Continuous, renderer.renderMode)

            viewModel.onCleared()
            renderer.runtime.awaitClosed()
            assertEquals(GraphiteRuntimeState.Closed, renderer.runtime.state.value)
        } finally {
            viewModel.onCleared()
            renderer.runtime.awaitClosed()
        }
    }
}
