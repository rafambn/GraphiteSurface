@file:OptIn(com.rafambn.graphitesurface.ExperimentalGraphiteSurfaceApi::class)

package com.rafambn.graphitesurface.sample.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.rafambn.graphitesurface.GraphiteRenderer
import com.rafambn.graphitesurface.GraphiteSurface
import com.rafambn.graphitesurface.sample.DEFAULT_ROTATION_SPEED

@Composable
internal fun RendererDemoScreen(
    renderer: GraphiteRenderer,
    title: String,
    onBack: () -> Unit,
    onRotationSpeedChange: (Float) -> Unit,
) {
    var rotationSpeed by remember(renderer) { mutableFloatStateOf(DEFAULT_ROTATION_SPEED) }

    Column(modifier = Modifier.fillMaxSize()) {
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
                    .background(Color(0xDD101114))
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
            PixelLabel(
                text = title,
                color = Color.White,
                pixelSize = 3.dp,
            )
        }
        RotationSpeedSlider(
            value = rotationSpeed,
            onValueChange = { speed ->
                rotationSpeed = speed
                onRotationSpeedChange(speed)
            },
        )
        GraphiteSurface(
            renderer = renderer,
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
        )
    }
}
