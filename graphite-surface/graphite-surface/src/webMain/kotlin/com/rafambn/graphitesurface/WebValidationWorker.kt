package com.rafambn.graphitesurface

internal expect class WebValidationWorker(index: Int) {
    internal fun process(
        commands: ByteArray,
        onSuccess: (ByteArray) -> Unit,
        onFailure: (String) -> Unit,
    )

    internal fun close()
}
