package com.rafambn.graphitesurface

internal class GraphiteWorkerPublication(
    internal val id: Long,
    internal val commands: ByteArray,
    internal val resourceIds: LongArray,
)
