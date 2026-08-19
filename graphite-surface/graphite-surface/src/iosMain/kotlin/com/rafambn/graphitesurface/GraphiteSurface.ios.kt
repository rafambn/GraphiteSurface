@file:OptIn(
    androidx.compose.ui.ExperimentalComposeUiApi::class,
    com.rafambn.graphitesurface.ExperimentalGraphiteSurfaceApi::class,
)

package com.rafambn.graphitesurface

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.UIKitView

@Composable
@ExperimentalGraphiteSurfaceApi
public actual fun GraphiteSurface(
    renderer: GraphiteRenderer,
    modifier: Modifier,
    renderMode: GraphiteRenderMode,
    controller: GraphiteSurfaceController?,
    outputMode: GraphiteOutputMode,
) {
    val adapter = remember(renderer, renderMode) { GraphiteSurfaceAdapter(renderer, renderMode) }

    DisposableEffect(controller, adapter) {
        controller?.setRequestRenderHandler { adapter.requestRender() }
        onDispose {
            controller?.setRequestRenderHandler(null)
        }
    }

    UIKitView(
        factory = { adapter.view },
        modifier = modifier,
        onRelease = { adapter.dispose() },
    )
}
