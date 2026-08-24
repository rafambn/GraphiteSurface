package com.rafambn.graphitesurface.sample.continuous

import com.rafambn.graphitesurface.GraphiteRenderMode
import com.rafambn.graphitesurface.GraphiteRuntime
import com.rafambn.graphitesurface.GraphiteRuntimeState
import com.rafambn.graphitesurface.sample.components.RendererScreenState
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain

@OptIn(ExperimentalCoroutinesApi::class)
class ContinuousRendererViewModelTest {
    @Test
    fun publishesContinuousRendererAndClosesItsRuntime() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val runtime = GraphiteRuntime.create()
        try {
            val viewModel = ContinuousRendererViewModel { runtime }
            advanceUntilIdle()
            val ready = assertIs<RendererScreenState.Ready>(viewModel.uiState.value)
            assertEquals(runtime, ready.renderer.runtime)
            assertEquals(GraphiteRenderMode.Continuous, ready.renderer.renderMode)

            viewModel.onCleared()
            runtime.awaitClosed()
            assertEquals(GraphiteRuntimeState.Closed, runtime.state.value)
        } finally {
            runtime.close()
            runtime.awaitClosed()
            Dispatchers.resetMain()
        }
    }

    @Test
    fun publishesInitializationFailure() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            val expected = IllegalStateException("runtime creation failed")
            val viewModel = ContinuousRendererViewModel { throw expected }
            advanceUntilIdle()

            val failed = assertIs<RendererScreenState.Failed>(viewModel.uiState.value)
            assertEquals(expected, failed.error)
            viewModel.onCleared()
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test
    fun closesRuntimeThatArrivesAfterViewModelWasCleared() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val runtime = GraphiteRuntime.create()
        val creation = CompletableDeferred<GraphiteRuntime>()
        try {
            val viewModel = ContinuousRendererViewModel { creation.await() }
            advanceUntilIdle()
            viewModel.onCleared()
            creation.complete(runtime)
            advanceUntilIdle()

            runtime.awaitClosed()
            assertEquals(GraphiteRuntimeState.Closed, runtime.state.value)
            assertEquals(RendererScreenState.Initializing, viewModel.uiState.value)
        } finally {
            runtime.close()
            runtime.awaitClosed()
            Dispatchers.resetMain()
        }
    }
}
