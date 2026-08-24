package com.rafambn.graphitesurface

/** Cheap logical target used to record commands while a surface is detached. */
public class GraphiteRecordingTarget internal constructor(
    public val pixelSize: GraphiteSize,
    internal val runtimeToken: Any,
)
