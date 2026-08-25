package com.rafambn.graphitesurface

/** Unexpected failure while creating the runtime or one of its workers. */
class GraphiteInitializationException(
    cause: Throwable,
) : GraphiteException("Graphite initialization failed", cause)
