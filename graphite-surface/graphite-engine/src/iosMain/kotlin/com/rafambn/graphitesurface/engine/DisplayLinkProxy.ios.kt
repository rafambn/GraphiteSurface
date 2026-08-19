@file:OptIn(
    kotlinx.cinterop.BetaInteropApi::class,
    kotlinx.cinterop.ExperimentalForeignApi::class,
)

package com.rafambn.graphitesurface.engine

import platform.darwin.NSObject

internal class DisplayLinkProxy(
    private val callback: () -> Unit,
) : NSObject() {
    @kotlinx.cinterop.ObjCAction
    fun handleDisplayLinkTick() {
        callback()
    }
}
