package com.rafambn.graphitesurface

import androidx.compose.ui.unit.IntSize

internal actual class PlatformRecorderWorker actual constructor(
    @Suppress("UNUSED_PARAMETER") index: Int,
) {
    internal actual suspend fun process(
        @Suppress("UNUSED_PARAMETER") message: ByteArray,
        @Suppress("UNUSED_PARAMETER") program: GraphiteCommandProgram,
        @Suppress("UNUSED_PARAMETER") pixelSize: IntSize?,
    ): PlatformRecording = PlatformRecording()

    internal actual fun close() = Unit

    internal actual suspend fun awaitClosed() = Unit
}
