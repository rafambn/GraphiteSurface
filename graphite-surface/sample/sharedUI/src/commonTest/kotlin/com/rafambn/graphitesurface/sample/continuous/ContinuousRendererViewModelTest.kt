package com.rafambn.graphitesurface.sample.continuous

import com.rafambn.graphitesurface.GraphiteRenderMode
import com.rafambn.graphitesurface.GraphiteRuntimeState
import com.rafambn.graphitesurface.sample.components.RendererScreenState
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlinx.coroutines.test.runTest

class ContinuousRendererViewModelTest {
    @Test
    fun publishesContinuousRendererAndClosesItsRuntime() = runTest {
        val viewModel = ContinuousRendererViewModel()
        val ready = assertIs<RendererScreenState.Ready>(viewModel.uiState.value)
        try {
            assertEquals(GraphiteRenderMode.Continuous, ready.renderer.renderMode)

            viewModel.onCleared()
            ready.renderer.runtime.awaitClosed()
            assertEquals(GraphiteRuntimeState.Closed, ready.renderer.runtime.state.value)
        } finally {
            viewModel.onCleared()
            ready.renderer.runtime.awaitClosed()
        }
    }
}
