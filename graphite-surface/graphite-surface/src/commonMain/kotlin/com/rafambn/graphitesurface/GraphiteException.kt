package com.rafambn.graphitesurface

/** Base class for failures produced by GraphiteSurface rather than user code. */
open class GraphiteException(message: String, cause: Throwable? = null) :
    RuntimeException(message, cause)
