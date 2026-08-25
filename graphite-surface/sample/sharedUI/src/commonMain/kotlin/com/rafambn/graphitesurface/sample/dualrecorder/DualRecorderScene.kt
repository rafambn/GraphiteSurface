package com.rafambn.graphitesurface.sample.dualrecorder

import com.rafambn.graphitesurface.GraphiteDisplayList
import com.rafambn.graphitesurface.GraphitePaint
import com.rafambn.graphitesurface.GraphiteSize
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import kotlin.math.max
import kotlin.math.min

internal object DualRecorderScene {
    internal fun prepare(
        pixelSize: GraphiteSize,
    ): PreparedScene {
        val background = buildBackground(pixelSize)
        return PreparedScene(
            background = background,
            foreground = buildForeground(pixelSize),
        )
    }

    private fun buildBackground(pixelSize: GraphiteSize): GraphiteDisplayList {
        val width = pixelSize.width.toFloat()
        val height = pixelSize.height.toFloat()
        val spacing = max(32f, min(width, height) / 9f)
        val gridPaint = GraphitePaint(
            color = Color(101, 216, 154, 80),
            style = GraphitePaint.Style.Stroke,
            strokeWidth = 1.5f,
        )
        val particlePaint = GraphitePaint(Color(101, 216, 154, 190))

        return GraphiteDisplayList.build {
            var x = -height
            while (x < width + height) {
                drawLine(
                    start = Offset(x, 0f),
                    end = Offset(x + height, height),
                    paint = gridPaint,
                )
                x += spacing
            }

            var y = 0f
            while (y <= height) {
                drawLine(
                    start = Offset(0f, y),
                    end = Offset(width, y),
                    paint = gridPaint,
                )
                y += spacing
            }

            repeat(PARTICLE_COUNT) { index ->
                val particleX = ((index * 83) % max(1, pixelSize.width)).toFloat()
                val particleY = ((index * 47) % max(1, pixelSize.height)).toFloat()
                val size = 2f + (index % 3)
                drawRect(
                    rect = Rect(
                        left = particleX,
                        top = particleY,
                        right = particleX + size,
                        bottom = particleY + size,
                    ),
                    paint = particlePaint,
                )
            }
        }
    }

    private fun buildForeground(pixelSize: GraphiteSize): GraphiteDisplayList {
        val extent = min(pixelSize.width, pixelSize.height).toFloat()
        val outerRadius = extent * 0.27f
        val innerRadius = extent * 0.12f
        val violet = GraphitePaint(
            color = Color(181, 140, 255, 235),
            style = GraphitePaint.Style.Stroke,
            strokeWidth = max(2f, extent * 0.008f),
        )
        val blue = GraphitePaint(Color(110, 168, 254, 220))
        val white = GraphitePaint(Color(255, 255, 255, 235))

        return GraphiteDisplayList.build {
            drawPath(
                path = Path().apply {
                    moveTo(0f, -outerRadius)
                    lineTo(outerRadius, 0f)
                    lineTo(0f, outerRadius)
                    lineTo(-outerRadius, 0f)
                    close()
                },
                paint = violet,
            )
            drawPath(
                path = Path().apply {
                    moveTo(-innerRadius, -innerRadius)
                    lineTo(innerRadius, -innerRadius)
                    lineTo(innerRadius, innerRadius)
                    lineTo(-innerRadius, innerRadius)
                    close()
                },
                paint = blue,
            )
            drawRect(
                rect = Rect(-7f, -7f, 7f, 7f),
                paint = white,
            )
            repeat(SATELLITE_COUNT) { index ->
                val directionX = if (index % 2 == 0) 1f else -1f
                val directionY = if (index < 2) 1f else -1f
                val centerX = directionX * outerRadius * 0.72f
                val centerY = directionY * outerRadius * 0.72f
                drawRect(
                    rect = Rect(
                        left = centerX - 5f,
                        top = centerY - 5f,
                        right = centerX + 5f,
                        bottom = centerY + 5f,
                    ),
                    paint = blue,
                )
            }
        }
    }

    internal class PreparedScene(
        internal val background: GraphiteDisplayList,
        internal val foreground: GraphiteDisplayList,
    )

    private const val PARTICLE_COUNT: Int = 42
    private const val SATELLITE_COUNT: Int = 4
}
