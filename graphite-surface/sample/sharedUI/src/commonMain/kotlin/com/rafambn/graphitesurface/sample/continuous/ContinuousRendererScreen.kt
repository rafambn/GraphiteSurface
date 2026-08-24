package com.rafambn.graphitesurface.sample.continuous

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.rafambn.graphitesurface.sample.components.RendererDemoScreen
import com.rafambn.graphitesurface.sample.components.RendererErrorScreen

@Composable
internal fun ContinuousRendererScreen(
    onBack: () -> Unit,
) {
    val viewModel = viewModel { ContinuousRendererViewModel() }
    val error by viewModel.error.collectAsStateWithLifecycle()
    val renderer = viewModel.renderer

    if (error != null || renderer == null) {
        RendererErrorScreen()
        return
    }

    RendererDemoScreen(
        renderer = renderer,
        title = "Continuous",
        onBack = onBack,
    )
}
