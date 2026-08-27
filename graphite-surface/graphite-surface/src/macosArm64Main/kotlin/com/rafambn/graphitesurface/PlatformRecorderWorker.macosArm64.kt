package com.rafambn.graphitesurface

import androidx.compose.ui.unit.IntSize

internal actual class PlatformRecorderWorker actual constructor(
    @Suppress("UNUSED_PARAMETER") index: Int,
) {
    init {
        unsupportedGraphiteSurfaceHost("macOS Arm64")
    }

    internal actual suspend fun process(
        message: ByteArray,
        program: GraphiteCommandProgram,
        pixelSize: IntSize?,
    ): PlatformRecording = unsupportedGraphiteSurfaceHost("macOS Arm64")

    internal actual fun close() = Unit

    internal actual suspend fun awaitClosed() = Unit
}
