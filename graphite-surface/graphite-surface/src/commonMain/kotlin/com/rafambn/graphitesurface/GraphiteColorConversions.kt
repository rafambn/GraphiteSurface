package com.rafambn.graphitesurface

internal fun GraphiteColor.toArgbLong(): Long {
    val red = (rgba shr 24) and 0xFFu
    val green = (rgba shr 16) and 0xFFu
    val blue = (rgba shr 8) and 0xFFu
    val alpha = rgba and 0xFFu
    return ((alpha shl 24) or (red shl 16) or (green shl 8) or blue).toLong()
}
