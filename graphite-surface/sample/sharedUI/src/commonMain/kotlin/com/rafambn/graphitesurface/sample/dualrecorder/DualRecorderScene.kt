package com.rafambn.graphitesurface.sample.dualrecorder

import com.rafambn.graphitesurface.GraphiteDisplayList
import com.rafambn.graphitesurface.GraphiteDrawStyle
import com.rafambn.graphitesurface.graphiteDisplayList
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.unit.IntSize
import kotlin.math.max
import kotlin.math.min

internal object DualRecorderScene {
    internal fun prepare(
        pixelSize: IntSize,
    ): PreparedScene {
        val background = buildBackground(pixelSize)
        return PreparedScene(
            background = background,
            foreground = buildForeground(pixelSize),
        )
    }

    private fun buildBackground(pixelSize: IntSize): GraphiteDisplayList {
        val width = pixelSize.width.toFloat()
        val height = pixelSize.height.toFloat()
        val spacing = max(32f, min(width, height) / 9f)
        val gridColor = Color(101, 216, 154, 80)
        val particleColor = Color(101, 216, 154, 190)

        return graphiteDisplayList {
            var x = -height
            while (x < width + height) {
                drawLine(
                    start = Offset(x, 0f),
                    end = Offset(x + height, height),
                    color = gridColor,
                    strokeWidth = 1.5f,
                )
                x += spacing
            }

            var y = 0f
            while (y <= height) {
                drawLine(
                    start = Offset(0f, y),
                    end = Offset(width, y),
                    color = gridColor,
                    strokeWidth = 1.5f,
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
                    color = particleColor,
                )
            }
        }
    }

    private fun buildForeground(pixelSize: IntSize): GraphiteDisplayList {
        val extent = min(pixelSize.width, pixelSize.height).toFloat()
        val outerRadius = extent * 0.27f
        val innerRadius = extent * 0.12f
        val violet = Color(181, 140, 255, 235)
        val blue = Color(110, 168, 254, 220)
        val white = Color(255, 255, 255, 235)

        return graphiteDisplayList {
            drawPath(
                path = Path().apply {
                    moveTo(0f, -outerRadius)
                    lineTo(outerRadius, 0f)
                    lineTo(0f, outerRadius)
                    lineTo(-outerRadius, 0f)
                    close()
                },
                color = violet,
                style = GraphiteDrawStyle.Stroke(max(2f, extent * 0.008f)),
            )
            drawPath(
                path = Path().apply {
                    moveTo(-innerRadius, -innerRadius)
                    lineTo(innerRadius, -innerRadius)
                    lineTo(innerRadius, innerRadius)
                    lineTo(-innerRadius, innerRadius)
                    close()
                },
                color = blue,
            )
            drawRect(
                rect = Rect(-7f, -7f, 7f, 7f),
                color = white,
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
                    color = blue,
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
