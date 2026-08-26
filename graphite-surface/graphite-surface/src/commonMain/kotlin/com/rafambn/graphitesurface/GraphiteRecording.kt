package com.rafambn.graphitesurface

/** Immutable recorder result that may be inserted into more than one frame. */
class GraphiteRecording internal constructor(
    internal val runtimeToken: Any,
    internal val program: GraphiteCommandProgram,
    internal val platformRecording: PlatformRecording,
)
