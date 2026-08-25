package com.rafambn.graphitesurface

/** The runtime is not in [GraphiteEngineState.Ready]. */
class GraphiteEngineUnavailableException(val runtimeState: GraphiteEngineState) :
    GraphiteException("Graphite runtime is unavailable: $runtimeState")
