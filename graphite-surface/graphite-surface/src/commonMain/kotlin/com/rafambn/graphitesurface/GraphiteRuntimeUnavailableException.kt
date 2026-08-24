package com.rafambn.graphitesurface

/** The runtime is not in [GraphiteRuntimeState.Ready]. */
public class GraphiteRuntimeUnavailableException(public val runtimeState: GraphiteRuntimeState) :
    GraphiteException("Graphite runtime is unavailable: $runtimeState")
