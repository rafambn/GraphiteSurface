package com.rafambn.graphitesurface

import kotlin.jvm.JvmInline

/** An unpremultiplied sRGB color packed as `0xRRGGBBAA`. */
@JvmInline
value class GraphiteColor(val rgba: UInt) {
    companion object {
        val Transparent: GraphiteColor = GraphiteColor(0x00000000u)
        val Black: GraphiteColor = GraphiteColor(0x000000FFu)
        val White: GraphiteColor = GraphiteColor(0xFFFFFFFFu)
        val Red: GraphiteColor = GraphiteColor(0xFF0000FFu)

        fun rgba(red: Int, green: Int, blue: Int, alpha: Int = 255): GraphiteColor {
            require(red in 0..255) { "red must be in 0..255" }
            require(green in 0..255) { "green must be in 0..255" }
            require(blue in 0..255) { "blue must be in 0..255" }
            require(alpha in 0..255) { "alpha must be in 0..255" }
            return GraphiteColor(
                (red.toUInt() shl 24) or
                    (green.toUInt() shl 16) or
                    (blue.toUInt() shl 8) or
                    alpha.toUInt(),
            )
        }
    }
}
