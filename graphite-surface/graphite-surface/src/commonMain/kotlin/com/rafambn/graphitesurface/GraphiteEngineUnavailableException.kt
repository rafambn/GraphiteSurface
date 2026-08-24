package com.rafambn.graphitesurface

/** The runtime is not in [GraphiteEngineState.Ready]. */
public class GraphiteEngineUnavailableException(public val runtimeState: GraphiteEngineState) :
    GraphiteException("Graphite runtime is unavailable: $runtimeState")
