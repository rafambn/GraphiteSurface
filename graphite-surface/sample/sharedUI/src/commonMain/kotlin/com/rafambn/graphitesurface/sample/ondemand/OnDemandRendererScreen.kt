package com.rafambn.graphitesurface.sample.ondemand

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.rafambn.graphitesurface.sample.components.RendererDemoScreen
import com.rafambn.graphitesurface.sample.components.RendererScreenState
import com.rafambn.graphitesurface.sample.components.RendererStatusScreen
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive

@Composable
internal fun OnDemandRendererScreen(
    onBack: () -> Unit,
) {
    val viewModel = viewModel { OnDemandRendererViewModel() }
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    when (val state = uiState) {
        RendererScreenState.Initializing -> RendererStatusScreen()
        is RendererScreenState.Failed -> RendererStatusScreen(failed = true)
        is RendererScreenState.Ready -> {
            LaunchedEffect(state.renderer) {
                while (isActive) {
                    state.renderer.requestRender()
                    delay(250L)
                }
            }
            RendererDemoScreen(
                renderer = state.renderer,
                title = "On demand",
                onBack = onBack,
            )
        }
    }
}
