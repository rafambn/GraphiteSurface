package com.rafambn.graphitesurface.sample.dualrecorder

import com.rafambn.graphitesurface.GraphiteRenderMode
import com.rafambn.graphitesurface.GraphiteRuntimeState
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest

class DualRecorderViewModelTest {
    @Test
    fun exposesTwoDedicatedRecordersAndClosesRuntime() = runTest {
        val viewModel = DualRecorderViewModel()
        val renderer = assertNotNull(viewModel.renderer)
        try {
            assertNull(viewModel.error.value)
            assertEquals(GraphiteRenderMode.Continuous, renderer.renderMode)
            assertEquals(2, renderer.runtime.recorders.size)
            assertTrue(viewModel.uiState.value.recorders.all { it.enabled })

            viewModel.toggleRecorder(0)

            assertFalse(viewModel.uiState.value.recorders[0].enabled)
            assertTrue(viewModel.uiState.value.recorders[1].enabled)

            viewModel.onCleared()
            renderer.runtime.awaitClosed()
            assertEquals(GraphiteRuntimeState.Closed, renderer.runtime.state.value)
        } finally {
            viewModel.onCleared()
            renderer.runtime.awaitClosed()
        }
    }
}
