package com.rafambn.graphitesurface.sample.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

@Composable
internal fun RendererStatusScreen(failed: Boolean = false) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF101114)),
        contentAlignment = Alignment.Center,
    ) {
        PixelLabel(
            text = if (failed) "ERROR" else "LOADING",
            color = if (failed) Color(0xFFFF6B6B) else Color.White,
            pixelSize = if (failed) 5.dp else 4.dp,
        )
    }
}
