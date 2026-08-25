package com.rafambn.graphitesurface.sample

import com.rafambn.graphitesurface.GraphiteDisplayList
import com.rafambn.graphitesurface.GraphitePaint
import com.rafambn.graphitesurface.GraphiteSize
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import kotlin.math.min

internal fun prepareGraphiteSampleScene(
    pixelSize: GraphiteSize,
): GraphiteDisplayList {
    val extent = min(pixelSize.width, pixelSize.height).toFloat()
    val halfWidth = extent * 0.35f
    return GraphiteDisplayList.build {
        drawPath(
            Path().apply {
                moveTo(0f, -halfWidth * 4f / 3f)
                lineTo(halfWidth, halfWidth * 2f / 3f)
                lineTo(-halfWidth, halfWidth * 2f / 3f)
                close()
            },
            GraphitePaint(Color.Red),
        )
    }
}
