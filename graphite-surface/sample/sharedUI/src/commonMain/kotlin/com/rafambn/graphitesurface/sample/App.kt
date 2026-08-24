package com.rafambn.graphitesurface.sample

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.rafambn.graphitesurface.sample.continuous.ContinuousRendererScreen
import com.rafambn.graphitesurface.sample.dualrecorder.DualRecorderScreen
import com.rafambn.graphitesurface.sample.manual.ManualRendererScreen
import com.rafambn.graphitesurface.sample.ondemand.OnDemandRendererScreen
import com.rafambn.graphitesurface.sample.selection.RendererSelectionScreen

@Composable
fun App() {
    var destination by remember { mutableStateOf<SampleDestination?>(null) }

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
