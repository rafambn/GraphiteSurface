@file:OptIn(com.rafambn.graphitesurface.ExperimentalGraphiteSurfaceApi::class)

package com.rafambn.graphitesurface.sample

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import com.rafambn.graphitesurface.GraphiteSurface
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.isActive

@Composable
fun App() {
    val viewModel = viewModel { GraphiteSampleViewModel() }
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    Box(modifier = Modifier.fillMaxSize()) {
        when (val state = uiState) {
            GraphiteSampleUiState.Initializing -> Unit
            is GraphiteSampleUiState.Failed -> BasicText(
                text = state.error.message ?: state.error.toString(),
            )
            is GraphiteSampleUiState.Ready -> {
                GraphiteSurface(
                    runtime = state.runtime,
                    modifier = Modifier.fillMaxSize(),
                )
                LaunchedEffect(viewModel, state.runtime) {
                    while (isActive) {
                        val frameTimeNanos = withFrameNanos { it }
                        viewModel.renderFrame(frameTimeNanos)
                    }
                }
            }
        }
    }
}
