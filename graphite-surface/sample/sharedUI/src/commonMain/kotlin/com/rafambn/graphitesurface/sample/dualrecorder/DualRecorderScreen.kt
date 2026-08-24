@file:OptIn(com.rafambn.graphitesurface.ExperimentalGraphiteSurfaceApi::class)

package com.rafambn.graphitesurface.sample.dualrecorder

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.rafambn.graphitesurface.GraphiteRenderer
import com.rafambn.graphitesurface.GraphiteSurface
import com.rafambn.graphitesurface.sample.components.PixelLabel
import com.rafambn.graphitesurface.sample.components.RendererErrorScreen

@Composable
internal fun DualRecorderScreen(onBack: () -> Unit) {
    val viewModel = viewModel { DualRecorderViewModel() }
    val error by viewModel.error.collectAsStateWithLifecycle()
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val renderer = viewModel.renderer

    if (error != null || renderer == null) {
        RendererErrorScreen()
        return
    }

    DualRecorderContent(
        renderer = renderer,
        uiState = uiState,
        onBack = onBack,
        onToggleRecorder = viewModel::toggleRecorder,
    )
}

@Composable
private fun DualRecorderContent(
    renderer: GraphiteRenderer,
    uiState: DualRecorderUiState,
    onBack: () -> Unit,
    onToggleRecorder: (Int) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF101114)),
    ) {
        DualRecorderHeader(onBack)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color(0xFF15171C))
                .padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            uiState.recorders.forEach { recorder ->
                RecorderCard(
                    recorder = recorder,
                    color = if (recorder.index == 0) Color(0xFF65D89A) else Color(0xFFB58CFF),
                    onToggle = { onToggleRecorder(recorder.index) },
                    modifier = Modifier.weight(1f),
                )
            }
        }
        GraphiteSurface(
            renderer = renderer,
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
        )
    }
}

@Composable
private fun DualRecorderHeader(onBack: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0xFF101114))
            .padding(horizontal = 20.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Canvas(
            modifier = Modifier
                .size(44.dp)
                .clip(CircleShape)
                .clickable(onClick = onBack)
                .padding(13.dp),
        ) {
            drawLine(
                color = Color.White,
                start = center.copy(x = size.width),
                end = center.copy(x = 0f),
                strokeWidth = 2.dp.toPx(),
            )
            drawLine(
                color = Color.White,
                start = center.copy(x = 0f),
                end = center.copy(x = size.width * 0.45f, y = 0f),
                strokeWidth = 2.dp.toPx(),
            )
            drawLine(
                color = Color.White,
                start = center.copy(x = 0f),
                end = center.copy(x = size.width * 0.45f, y = size.height),
                strokeWidth = 2.dp.toPx(),
            )
        }
        Spacer(modifier = Modifier.size(16.dp))
        Column {
            PixelLabel(
                text = "Dual recorder",
                color = Color.White,
                pixelSize = 3.dp,
            )
            Spacer(modifier = Modifier.size(6.dp))
            PixelLabel(
                text = "2 queues  2 workers  1 frame",
                color = Color(0xFF8E939E),
                pixelSize = 1.dp,
            )
        }
    }
}

@Composable
private fun RecorderCard(
    recorder: DualRecorderUiState.Recorder,
    color: Color,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .background(Color(0xFF1D2026))
            .clickable(onClick = onToggle)
            .padding(12.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            PixelLabel(
                text = "Recorder ${recorder.index}",
                color = Color.White,
                pixelSize = 2.dp,
            )
            Box(
                modifier = Modifier
                    .size(9.dp)
                    .clip(CircleShape)
                    .background(if (recorder.enabled) color else Color(0xFF555A65)),
            )
        }
        Spacer(modifier = Modifier.size(9.dp))
        PixelLabel(
            text = "Graphite recorder ${recorder.index}",
            color = Color(0xFF8E939E),
            pixelSize = 1.dp,
        )
        Spacer(modifier = Modifier.size(8.dp))
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            MetricLine(
                label = "STATUS",
                value = if (recorder.enabled) "RUNNING" else "PAUSED",
                valueColor = if (recorder.enabled) color else Color(0xFF777C86),
            )
            MetricLine(
                label = "QUEUE",
                value = "${recorder.queueDepth}/${recorder.queueCapacity}",
            )
            MetricLine(label = "FRAMES", value = recorder.completed.toString())
            MetricLine(label = "AVG", value = formatNanos(recorder.averageRecordingNanos))
            MetricLine(label = "MAX", value = formatNanos(recorder.maximumRecordingNanos))
        }
        Spacer(modifier = Modifier.size(8.dp))
        PixelLabel(
            text = if (recorder.enabled) "TAP TO PAUSE" else "TAP TO RESUME",
            color = Color(0xFFBEC2CB),
            pixelSize = 1.dp,
        )
    }
}

@Composable
private fun MetricLine(
    label: String,
    value: String,
    valueColor: Color = Color.White,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        PixelLabel(
            text = label,
            color = Color(0xFF777C86),
            pixelSize = 1.dp,
        )
        PixelLabel(
            text = value,
            color = valueColor,
            pixelSize = 1.dp,
        )
    }
}

private fun formatNanos(nanos: Long): String {
    val tenthsOfMillis = nanos / 100_000L
    return "${tenthsOfMillis / 10}.${tenthsOfMillis % 10} ms"
}
