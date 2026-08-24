package com.rafambn.graphitesurface.sample

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.rafambn.graphitesurface.sample.continuous.ContinuousRendererScreen
import com.rafambn.graphitesurface.sample.dualrecorder.DualRecorderScreen
import com.rafambn.graphitesurface.sample.manual.ManualRendererScreen
import com.rafambn.graphitesurface.sample.ondemand.OnDemandRendererScreen
import com.rafambn.graphitesurface.sample.selection.RendererSelectionScreen

@Composable
fun App() {
    var destination by remember { mutableStateOf<SampleDestination?>(null) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .safeDrawingPadding(),
    ) {
        when (destination) {
            null -> RendererSelectionScreen(onSelect = { destination = it })
            SampleDestination.Continuous -> ContinuousRendererScreen(
                onBack = { destination = null },
            )
            SampleDestination.OnDemand -> OnDemandRendererScreen(
                onBack = { destination = null },
            )
            SampleDestination.Manual -> ManualRendererScreen(
                onBack = { destination = null },
            )
            SampleDestination.DualRecorder -> DualRecorderScreen(
                onBack = { destination = null },
            )
        }
    }
}
