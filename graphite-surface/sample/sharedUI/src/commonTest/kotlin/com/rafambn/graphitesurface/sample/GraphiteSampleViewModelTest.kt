package com.rafambn.graphitesurface.sample

import com.rafambn.graphitesurface.GraphiteRuntime
import com.rafambn.graphitesurface.GraphiteRuntimeState
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
class GraphiteSampleViewModelTest {
    @Test
    fun keepsRotationStableForLargeFrameTimes() {
        val manyRotations = 250_000_000L * 4_000_000_000L

        assertEquals(0f, loopingRotationDegrees(manyRotations))
        assertEquals(90f, loopingRotationDegrees(manyRotations + 1_000_000_000L))
        assertEquals(180f, loopingRotationDegrees(manyRotations + 2_000_000_000L))
    }

    @Test
    fun publishesSuccessfulInitializationAndClosesTheRuntime() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val runtime = GraphiteRuntime.create()
        try {
            val viewModel = GraphiteSampleViewModel { runtime }
            advanceUntilIdle()
            assertEquals(GraphiteSampleUiState.Ready(runtime), viewModel.uiState.value)

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
            val viewModel = GraphiteSampleViewModel { throw expected }
            advanceUntilIdle()

            val failed = assertIs<GraphiteSampleUiState.Failed>(viewModel.uiState.value)
            assertEquals(expected, failed.error)
            viewModel.onCleared()
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test
    fun closesARuntimeThatArrivesAfterTheViewModelWasCleared() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val runtime = GraphiteRuntime.create()
        val creation = CompletableDeferred<GraphiteRuntime>()
        try {
            val viewModel = GraphiteSampleViewModel { creation.await() }
            advanceUntilIdle()
            viewModel.onCleared()
            creation.complete(runtime)
            advanceUntilIdle()

            runtime.awaitClosed()
            assertEquals(GraphiteRuntimeState.Closed, runtime.state.value)
            assertEquals(GraphiteSampleUiState.Initializing, viewModel.uiState.value)
        } finally {
            runtime.close()
            runtime.awaitClosed()
            Dispatchers.resetMain()
        }
    }
}
