package com.rafambn.graphitesurface.sample.ondemand

import com.rafambn.graphitesurface.GraphiteRenderMode
import com.rafambn.graphitesurface.GraphiteRuntimeState
import com.rafambn.graphitesurface.sample.components.RendererScreenState
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlinx.coroutines.test.runTest

class OnDemandRendererViewModelTest {
    @Test
    fun publishesOnDemandRendererAndClosesItsRuntime() = runTest {
        val viewModel = OnDemandRendererViewModel()
        val ready = assertIs<RendererScreenState.Ready>(viewModel.uiState.value)
        try {
            assertEquals(GraphiteRenderMode.OnDemand, ready.renderer.renderMode)

            viewModel.onCleared()
            ready.renderer.runtime.awaitClosed()
            assertEquals(GraphiteRuntimeState.Closed, ready.renderer.runtime.state.value)
        } finally {
            viewModel.onCleared()
            ready.renderer.runtime.awaitClosed()
        }
    }
}
