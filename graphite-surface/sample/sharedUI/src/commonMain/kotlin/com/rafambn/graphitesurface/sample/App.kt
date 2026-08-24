package com.rafambn.graphitesurface.sample

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.rafambn.graphitesurface.GraphiteRenderMode
import com.rafambn.graphitesurface.sample.continuous.ContinuousRendererScreen
import com.rafambn.graphitesurface.sample.manual.ManualRendererScreen
import com.rafambn.graphitesurface.sample.ondemand.OnDemandRendererScreen
import com.rafambn.graphitesurface.sample.selection.RendererSelectionScreen

@Composable
fun App() {
    var selectedMode by remember { mutableStateOf<GraphiteRenderMode?>(null) }

    when (selectedMode) {
        null -> RendererSelectionScreen(onSelect = { selectedMode = it })
        GraphiteRenderMode.Continuous -> ContinuousRendererScreen(
            onBack = { selectedMode = null },
        )
        GraphiteRenderMode.OnDemand -> OnDemandRendererScreen(
            onBack = { selectedMode = null },
        )
        GraphiteRenderMode.Manual -> ManualRendererScreen(
            onBack = { selectedMode = null },
        )
    }
}
