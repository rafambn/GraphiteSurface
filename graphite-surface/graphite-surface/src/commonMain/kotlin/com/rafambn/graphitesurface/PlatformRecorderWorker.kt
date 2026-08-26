package com.rafambn.graphitesurface

import androidx.compose.ui.unit.IntSize

internal expect class PlatformRecorderWorker(index: Int) {
    internal suspend fun process(
        message: ByteArray,
        program: GraphiteCommandProgram,
        pixelSize: IntSize?,
    ): PlatformRecording
    internal fun close()
    internal suspend fun awaitClosed()
}
