package com.rafambn.graphitesurface

import kotlin.jvm.JvmInline

/** An unpremultiplied sRGB color packed as `0xRRGGBBAA`. */
@JvmInline
public value class GraphiteColor(public val rgba: UInt) {
    public companion object {
        public val Transparent: GraphiteColor = GraphiteColor(0x00000000u)
        public val Black: GraphiteColor = GraphiteColor(0x000000FFu)
        public val White: GraphiteColor = GraphiteColor(0xFFFFFFFFu)
        public val Red: GraphiteColor = GraphiteColor(0xFF0000FFu)

        public fun rgba(red: Int, green: Int, blue: Int, alpha: Int = 255): GraphiteColor {
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
