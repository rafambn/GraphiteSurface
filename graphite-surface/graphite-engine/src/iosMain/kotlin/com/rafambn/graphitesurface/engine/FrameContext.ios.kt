package com.rafambn.graphitesurface.engine

import org.jetbrains.skia.Canvas
import org.jetbrains.skia.PathBuilder

internal class FrameContext(
    val canvas: Canvas,
) {
    var path: PathBuilder = PathBuilder()
}
