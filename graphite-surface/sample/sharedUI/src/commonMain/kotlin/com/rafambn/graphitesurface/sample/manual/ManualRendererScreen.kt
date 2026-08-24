package com.rafambn.graphitesurface.sample.manual

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.withFrameNanos
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.rafambn.graphitesurface.sample.components.RendererDemoScreen
import com.rafambn.graphitesurface.sample.components.RendererScreenState
import com.rafambn.graphitesurface.sample.components.RendererStatusScreen
import kotlinx.coroutines.isActive

@Composable
internal fun ManualRendererScreen(
    onBack: () -> Unit,
) {
    val viewModel = viewModel { ManualRendererViewModel() }
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    when (val state = uiState) {
        RendererScreenState.Initializing -> RendererStatusScreen()
        is RendererScreenState.Failed -> RendererStatusScreen(failed = true)
        is RendererScreenState.Ready -> {
            LaunchedEffect(state.renderer) {
                while (isActive) {
                    val frameTimeNanos = withFrameNanos { it }
                    state.renderer.render(frameTimeNanos)
                }
            }
            RendererDemoScreen(
                renderer = state.renderer,
                title = "Manual",
                onBack = onBack,
            )
        }
    }
}
