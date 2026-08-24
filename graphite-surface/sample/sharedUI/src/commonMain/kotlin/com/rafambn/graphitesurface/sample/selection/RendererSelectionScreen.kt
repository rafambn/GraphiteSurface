package com.rafambn.graphitesurface.sample.selection

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.rafambn.graphitesurface.sample.SampleDestination
import com.rafambn.graphitesurface.sample.components.PixelLabel

@Composable
internal fun RendererSelectionScreen(onSelect: (SampleDestination) -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF101114))
            .padding(horizontal = 24.dp, vertical = 32.dp),
        verticalArrangement = Arrangement.Center,
    ) {
        PixelLabel(
            text = "RENDER MODE",
            color = Color.White,
            pixelSize = 5.dp,
        )
        Spacer(modifier = Modifier.height(28.dp))
        RendererModeCard(
            title = "Continuous",
            color = Color(0xFF65D89A),
            onClick = { onSelect(SampleDestination.Continuous) },
        )
        Spacer(modifier = Modifier.height(12.dp))
        RendererModeCard(
            title = "On demand",
            color = Color(0xFFF0B45B),
            onClick = { onSelect(SampleDestination.OnDemand) },
        )
        Spacer(modifier = Modifier.height(12.dp))
        RendererModeCard(
            title = "Manual",
            color = Color(0xFF6EA8FE),
            onClick = { onSelect(SampleDestination.Manual) },
        )
        Spacer(modifier = Modifier.height(12.dp))
        RendererModeCard(
            title = "Dual recorder",
            color = Color(0xFFB58CFF),
            onClick = { onSelect(SampleDestination.DualRecorder) },
        )
    }
}

@Composable
private fun RendererModeCard(
    title: String,
    color: Color,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(Color(0xFF1D2026))
            .clickable(onClick = onClick)
            .padding(20.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(14.dp)
                .clip(CircleShape)
                .background(color),
        )
        Spacer(modifier = Modifier.size(16.dp))
        PixelLabel(
            text = title,
            color = Color.White,
            pixelSize = 3.dp,
        )
    }
}
