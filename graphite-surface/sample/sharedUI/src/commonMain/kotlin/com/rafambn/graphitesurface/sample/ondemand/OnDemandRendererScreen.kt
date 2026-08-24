package com.rafambn.graphitesurface.sample.ondemand

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.vector.PathData
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.rafambn.graphitesurface.sample.components.RendererDemoScreen
import com.rafambn.graphitesurface.sample.components.RendererErrorScreen
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive

@Composable
internal fun OnDemandRendererScreen(
    onBack: () -> Unit,
) {
    val viewModel = viewModel { OnDemandRendererViewModel() }
    val error by viewModel.error.collectAsStateWithLifecycle()
    val renderer = viewModel.renderer

    if (error != null || renderer == null) {
        RendererErrorScreen()
        return
    }

    LaunchedEffect(renderer) {
        while (isActive) {
            renderer.requestRender()
            delay(250L)
        }
    }
    RendererDemoScreen(
        renderer = renderer,
        title = "On demand",
        onBack = onBack,
        onRotationSpeedChange = viewModel::setRotationSpeed,
    )
}
