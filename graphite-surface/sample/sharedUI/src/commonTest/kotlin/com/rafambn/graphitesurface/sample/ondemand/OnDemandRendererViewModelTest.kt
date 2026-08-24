package com.rafambn.graphitesurface.sample.ondemand

import com.rafambn.graphitesurface.GraphiteRenderMode
import com.rafambn.graphitesurface.GraphiteRuntime
import com.rafambn.graphitesurface.GraphiteRuntimeState
import com.rafambn.graphitesurface.sample.components.RendererScreenState
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain

@OptIn(ExperimentalCoroutinesApi::class)
class OnDemandRendererViewModelTest {
    @Test
    fun publishesOnDemandRendererAndClosesItsRuntime() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val runtime = GraphiteRuntime.create()
        try {
            val viewModel = OnDemandRendererViewModel { runtime }
            advanceUntilIdle()
            val ready = assertIs<RendererScreenState.Ready>(viewModel.uiState.value)
            assertEquals(runtime, ready.renderer.runtime)
            assertEquals(GraphiteRenderMode.OnDemand, ready.renderer.renderMode)

            viewModel.onCleared()
            runtime.awaitClosed()
            assertEquals(GraphiteRuntimeState.Closed, runtime.state.value)
        } finally {
            runtime.close()
            runtime.awaitClosed()
            Dispatchers.resetMain()
        }
    }
}
