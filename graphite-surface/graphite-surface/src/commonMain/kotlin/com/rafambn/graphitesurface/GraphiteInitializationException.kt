package com.rafambn.graphitesurface

/** Unexpected failure while creating the runtime or one of its workers. */
public class GraphiteInitializationException(
    public val stage: GraphiteFailure.Stage,
    cause: Throwable,
) : GraphiteException("Graphite initialization failed at $stage", cause)
