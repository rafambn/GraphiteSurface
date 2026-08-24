package com.rafambn.graphitesurface.sample.dualrecorder

import com.rafambn.graphitesurface.GraphiteColor
import com.rafambn.graphitesurface.GraphiteDisplayList
import com.rafambn.graphitesurface.GraphitePaint
import com.rafambn.graphitesurface.GraphitePath
import com.rafambn.graphitesurface.GraphitePoint
import com.rafambn.graphitesurface.GraphiteRect
import com.rafambn.graphitesurface.GraphiteSize
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
            color = GraphiteColor.rgba(101, 216, 154, 80),
            style = GraphitePaint.Style.Stroke,
            strokeWidth = 1.5f,
        )
        val particlePaint = GraphitePaint(GraphiteColor.rgba(101, 216, 154, 190))

        return GraphiteDisplayList.build {
            var x = -height
            while (x < width + height) {
                drawLine(
                    start = GraphitePoint(x, 0f),
                    end = GraphitePoint(x + height, height),
                    paint = gridPaint,
                )
                x += spacing
            }

            var y = 0f
            while (y <= height) {
                drawLine(
                    start = GraphitePoint(0f, y),
                    end = GraphitePoint(width, y),
                    paint = gridPaint,
                )
                y += spacing
            }

            repeat(PARTICLE_COUNT) { index ->
                val particleX = ((index * 83) % max(1, pixelSize.width)).toFloat()
                val particleY = ((index * 47) % max(1, pixelSize.height)).toFloat()
                val size = 2f + (index % 3)
                drawRect(
                    rect = GraphiteRect(
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
            color = GraphiteColor.rgba(181, 140, 255, 235),
            style = GraphitePaint.Style.Stroke,
            strokeWidth = max(2f, extent * 0.008f),
        )
        val blue = GraphitePaint(GraphiteColor.rgba(110, 168, 254, 220))
        val white = GraphitePaint(GraphiteColor.rgba(255, 255, 255, 235))

        return GraphiteDisplayList.build {
            drawPath(
                path = GraphitePath.build {
                    moveTo(0f, -outerRadius)
                    lineTo(outerRadius, 0f)
                    lineTo(0f, outerRadius)
                    lineTo(-outerRadius, 0f)
                    close()
                },
                paint = violet,
            )
            drawPath(
                path = GraphitePath.build {
                    moveTo(-innerRadius, -innerRadius)
                    lineTo(innerRadius, -innerRadius)
                    lineTo(innerRadius, innerRadius)
                    lineTo(-innerRadius, innerRadius)
                    close()
                },
                paint = blue,
            )
            drawRect(
                rect = GraphiteRect(-7f, -7f, 7f, 7f),
                paint = white,
            )
            repeat(SATELLITE_COUNT) { index ->
                val directionX = if (index % 2 == 0) 1f else -1f
                val directionY = if (index < 2) 1f else -1f
                val centerX = directionX * outerRadius * 0.72f
                val centerY = directionY * outerRadius * 0.72f
                drawRect(
                    rect = GraphiteRect(
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
