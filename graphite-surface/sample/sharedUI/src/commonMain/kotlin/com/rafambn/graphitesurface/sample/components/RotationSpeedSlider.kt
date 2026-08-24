@file:OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)

package com.rafambn.graphitesurface.sample.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import com.rafambn.graphitesurface.sample.MAX_ROTATION_SPEED
import com.rafambn.graphitesurface.sample.MIN_ROTATION_SPEED

@Composable
internal fun RotationSpeedSlider(
    value: Float,
    onValueChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
) {
    val currentOnValueChange by rememberUpdatedState(onValueChange)
    val progress = ((value - MIN_ROTATION_SPEED) /
        (MAX_ROTATION_SPEED - MIN_ROTATION_SPEED)).coerceIn(0f, 1f)

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 10.dp),
    ) {
        Row(modifier = Modifier.fillMaxWidth()) {
            PixelLabel(
                text = "ROTATION",
                color = Color.White,
                pixelSize = 2.dp,
            )
            Spacer(modifier = Modifier.weight(1f))
            PixelLabel(
                text = "${value}X",
                color = Color(0xFFB9BDC7),
                pixelSize = 2.dp,
            )
        }
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(36.dp)
                .padding(horizontal = 8.dp)
                .pointerInput(Unit) {
                    detectDragGestures(
                        orientationLock = Orientation.Horizontal,
                        shouldAwaitTouchSlop = { false },
                        onDragStart = { _, change, _ ->
                            currentOnValueChange(valueForPosition(change.position.x, size.width))
                        },
                        onDrag = { change, _ ->
                            change.consume()
                            currentOnValueChange(valueForPosition(change.position.x, size.width))
                        },
                    )
                },
        ) {
            val thumbX = size.width * progress
            val centerY = size.height / 2f
            drawLine(
                color = Color(0xFF4B4E56),
                start = Offset(0f, centerY),
                end = Offset(size.width, centerY),
                strokeWidth = 4.dp.toPx(),
                cap = StrokeCap.Round,
            )
            drawLine(
                color = Color(0xFFF04A4A),
                start = Offset(0f, centerY),
                end = Offset(thumbX, centerY),
                strokeWidth = 4.dp.toPx(),
                cap = StrokeCap.Round,
            )
            drawCircle(
                color = Color.White,
                radius = 8.dp.toPx(),
                center = Offset(thumbX, centerY),
            )
        }
    }
}

private fun valueForPosition(position: Float, width: Int): Float {
    val progress = (position / width.coerceAtLeast(1)).coerceIn(0f, 1f)
    val value = MIN_ROTATION_SPEED +
        progress * (MAX_ROTATION_SPEED - MIN_ROTATION_SPEED)
    return (value * 10f).toInt() / 10f
}
