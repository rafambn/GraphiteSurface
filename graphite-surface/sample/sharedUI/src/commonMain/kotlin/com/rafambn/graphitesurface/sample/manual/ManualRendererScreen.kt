package com.rafambn.graphitesurface.sample.manual

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.withFrameNanos
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.rafambn.graphitesurface.sample.components.RendererDemoScreen
import com.rafambn.graphitesurface.sample.components.RendererErrorScreen
import kotlinx.coroutines.isActive

@Composable
internal fun ManualRendererScreen(
    onBack: () -> Unit,
) {
    val viewModel = viewModel { ManualRendererViewModel() }
    val error by viewModel.error.collectAsStateWithLifecycle()
    val renderer = viewModel.renderer

    if (error != null || renderer == null) {
        RendererErrorScreen()
        return
    }

    LaunchedEffect(renderer) {
        while (isActive) {
            val frameTimeNanos = withFrameNanos { it }
            renderer.render(frameTimeNanos)
        }
    }
    RendererDemoScreen(
        renderer = renderer,
        title = "Manual",
        onBack = onBack,
        onRotationSpeedChange = viewModel::setRotationSpeed,
    )
}
