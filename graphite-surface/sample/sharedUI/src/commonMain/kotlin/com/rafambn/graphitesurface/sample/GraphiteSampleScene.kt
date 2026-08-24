package com.rafambn.graphitesurface.sample

import com.rafambn.graphitesurface.GraphiteColor
import com.rafambn.graphitesurface.GraphiteDisplayList
import com.rafambn.graphitesurface.GraphitePaint
import com.rafambn.graphitesurface.GraphitePath
import com.rafambn.graphitesurface.GraphiteRecordingTarget
import com.rafambn.graphitesurface.GraphiteRuntime
import com.rafambn.graphitesurface.GraphiteSize
import kotlin.math.min

internal class GraphiteSampleScene : AutoCloseable {
    internal var current: Resources? = null
        private set

    internal fun prepare(
        runtime: GraphiteRuntime,
        generation: Long,
        pixelSize: GraphiteSize,
    ): Resources {
        current?.takeIf { it.generation == generation }?.let { return it }
        close()
        val extent = min(pixelSize.width, pixelSize.height).toFloat()
        val halfWidth = extent * 0.35f
        val displayList = GraphiteDisplayList.build {
            drawPath(
                GraphitePath.build {
                    moveTo(0f, -halfWidth * 4f / 3f)
                    lineTo(halfWidth, halfWidth * 2f / 3f)
                    lineTo(-halfWidth, halfWidth * 2f / 3f)
                    close()
                },
                GraphitePaint(GraphiteColor.Red),
            )
        }
        return try {
            Resources(
                generation = generation,
                target = runtime.createRecordingTarget(pixelSize),
                displayList = displayList,
            ).also { current = it }
        } catch (error: Throwable) {
            displayList.close()
            throw error
        }
    }

    override fun close() {
        current?.displayList?.close()
        current = null
    }

    internal data class Resources(
        internal val generation: Long,
        internal val target: GraphiteRecordingTarget,
        internal val displayList: GraphiteDisplayList,
    )
}
