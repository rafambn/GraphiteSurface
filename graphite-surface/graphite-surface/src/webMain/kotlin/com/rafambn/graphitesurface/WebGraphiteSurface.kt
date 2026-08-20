@file:OptIn(
    androidx.compose.ui.ExperimentalComposeUiApi::class,
    com.rafambn.graphitesurface.ExperimentalGraphiteSurfaceApi::class,
)

package com.rafambn.graphitesurface

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.HtmlElementView

@Composable
@ExperimentalGraphiteSurfaceApi
internal fun WebGraphiteSurface(
    renderer: GraphiteRenderer,
    modifier: Modifier,
    renderMode: GraphiteRenderMode,
    controller: GraphiteSurfaceController?,
    @Suppress("UNUSED_PARAMETER") outputMode: GraphiteOutputMode,
) {
    val adapter = remember(renderer, renderMode) {
        WebGraphiteSurfaceAdapter(renderer, renderMode)
    }

    DisposableEffect(controller, adapter) {
        controller?.setRequestRenderHandler { adapter.requestRender() }
        onDispose {
            controller?.setRequestRenderHandler(null)
        }
    }

    HtmlElementView(
        factory = { adapter.createCanvas() },
        modifier = modifier,
        update = { adapter.updateSize() },
        onRelease = { adapter.dispose() },
    )
}
