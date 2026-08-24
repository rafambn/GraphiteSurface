package com.rafambn.graphitesurface.sample

import com.rafambn.graphitesurface.GraphiteColor
import com.rafambn.graphitesurface.GraphiteDisplayList
import com.rafambn.graphitesurface.GraphitePaint
import com.rafambn.graphitesurface.GraphitePath
import com.rafambn.graphitesurface.GraphiteRecordingTarget
import com.rafambn.graphitesurface.GraphiteEngine
import com.rafambn.graphitesurface.GraphiteSize
import kotlin.math.min

internal object GraphiteSampleScene {
    internal fun prepare(
        runtime: GraphiteEngine,
        pixelSize: GraphiteSize,
    ): PreparedScene {
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
        return PreparedScene(
            target = runtime.createRecordingTarget(pixelSize),
            displayList = displayList,
        )
    }

    internal class PreparedScene(
        internal val target: GraphiteRecordingTarget,
        internal val displayList: GraphiteDisplayList,
    )
}
