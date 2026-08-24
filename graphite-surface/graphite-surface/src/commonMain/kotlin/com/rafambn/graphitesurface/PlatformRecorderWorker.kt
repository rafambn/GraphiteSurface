package com.rafambn.graphitesurface

internal expect class PlatformRecorderWorker(index: Int) {
    internal suspend fun process(commands: ByteArray): ByteArray
    internal fun close()
    internal suspend fun awaitClosed()
}
