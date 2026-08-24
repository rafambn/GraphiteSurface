package com.rafambn.graphitesurface.sample.continuous

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.rafambn.graphitesurface.sample.components.RendererDemoScreen
import com.rafambn.graphitesurface.sample.components.RendererScreenState
import com.rafambn.graphitesurface.sample.components.RendererStatusScreen

@Composable
internal fun ContinuousRendererScreen(
    onBack: () -> Unit,
) {
    val viewModel = viewModel { ContinuousRendererViewModel() }
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    when (val state = uiState) {
        RendererScreenState.Initializing -> RendererStatusScreen()
        is RendererScreenState.Failed -> RendererStatusScreen(failed = true)
        is RendererScreenState.Ready -> RendererDemoScreen(
            renderer = state.renderer,
            title = "Continuous",
            onBack = onBack,
        )
    }
}
