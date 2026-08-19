@file:OptIn(com.rafambn.graphitesurface.ExperimentalGraphiteSurfaceApi::class)

package com.rafambn.graphitesurface.sample

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rafambn.graphitesurface.GraphiteDrawContext
import com.rafambn.graphitesurface.GraphiteOutputMode
import com.rafambn.graphitesurface.GraphiteRenderer
import com.rafambn.graphitesurface.GraphiteSize
import com.rafambn.graphitesurface.GraphiteSurface
import kotlin.math.min
import kotlin.time.TimeSource

@Composable
fun App() {
    val renderer = remember { TriangleRenderer() }
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        GraphiteSurface(
            renderer = renderer,
            modifier = Modifier.fillMaxSize(),
            outputMode = GraphiteOutputMode.HardwareBuffer,
        )
        BasicText(
            text = "Graphite PoC",
            style = TextStyle(color = Color.Black, fontSize = 28.sp),
            modifier = Modifier.align(Alignment.TopCenter).padding(top = 120.dp),
        )
    }
}

private class TriangleRenderer : GraphiteRenderer {
    private val startTime = TimeSource.Monotonic.markNow()
    private var size = GraphiteSize.Zero

    override fun onSurfaceCreated() = Unit

    override fun onSurfaceChanged(size: GraphiteSize) {
        this.size = size
    }

    override fun onDrawFrame(context: GraphiteDrawContext) {
        val width = size.width
        val height = size.height
        if (width == 0 || height == 0) return
        context.clear(0xFFFFFFFF)
        val halfWidth = min(width, height) * 0.35f
        val top = -halfWidth * 4f / 3f
        val base = halfWidth * 2f / 3f
        context.save()
        context.translate(width / 2f, height / 2f)
        context.rotate((startTime.elapsedNow().inWholeMilliseconds / 1_000.0 * 90.0).toFloat())
        context.beginPath()
        context.moveTo(0f, top)
        context.lineTo(halfWidth, base)
        context.lineTo(-halfWidth, base)
        context.closePath()
        context.drawPath(0xFFFF0000, antiAlias = true)
        context.restore()
    }
}
