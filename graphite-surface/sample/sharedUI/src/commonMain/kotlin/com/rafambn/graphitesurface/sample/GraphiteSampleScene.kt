package com.rafambn.graphitesurface.sample

import com.rafambn.graphitesurface.GraphiteDisplayList
import com.rafambn.graphitesurface.graphiteDisplayList
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.unit.IntSize
import kotlin.math.min

internal fun prepareGraphiteSampleScene(
    pixelSize: IntSize,
): GraphiteDisplayList {
    val extent = min(pixelSize.width, pixelSize.height).toFloat()
    val halfWidth = extent * 0.35f
    return graphiteDisplayList {
        drawPath(
            Path().apply {
                moveTo(0f, -halfWidth * 4f / 3f)
                lineTo(halfWidth, halfWidth * 2f / 3f)
                lineTo(-halfWidth, halfWidth * 2f / 3f)
                close()
            },
            Color.Red,
        )
    }
}
