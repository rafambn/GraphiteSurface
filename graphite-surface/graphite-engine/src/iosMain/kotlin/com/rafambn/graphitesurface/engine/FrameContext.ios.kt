package com.rafambn.graphitesurface.engine

import org.jetbrains.skia.Canvas
import org.jetbrains.skia.PathBuilder

internal class FrameContext(
    val canvas: Canvas,
    val width: Int,
    val height: Int,
) {
    var path = PathBuilder()
}
