package com.rafambn.graphitesurface

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.colorspace.ColorSpaces
import kotlin.math.roundToInt

/** Converts every Compose color to the command stream's canonical ARGB sRGB value. */
internal fun Color.toArgbLong(): Long {
    require(this != Color.Unspecified) { "color must be specified" }
    val srgb = convert(ColorSpaces.Srgb)
    val red = srgb.red.toByteComponent()
    val green = srgb.green.toByteComponent()
    val blue = srgb.blue.toByteComponent()
    val alpha = srgb.alpha.toByteComponent()
    return ((alpha shl 24) or (red shl 16) or (green shl 8) or blue).toUInt().toLong()
}

private fun Float.toByteComponent(): Int = (coerceIn(0f, 1f) * 255f).roundToInt()

internal fun Long.toComposeColor(): Color = Color(toInt())
