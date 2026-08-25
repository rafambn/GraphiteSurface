package com.rafambn.graphitesurface

/** Unexpected failure while creating the runtime or one of its workers. */
class GraphiteInitializationException(
    val stage: GraphiteFailure.Stage,
    cause: Throwable,
) : GraphiteException("Graphite initialization failed at $stage", cause)
