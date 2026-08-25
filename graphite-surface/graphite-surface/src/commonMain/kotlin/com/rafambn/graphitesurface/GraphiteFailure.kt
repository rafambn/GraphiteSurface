package com.rafambn.graphitesurface

/** Fatal runtime failure reported through [GraphiteEngineState.Failed]. */
data class GraphiteFailure(
    val kind: Kind,
    val stage: Stage,
    val message: String,
    val cause: Throwable? = null,
) {
    enum class Kind {
        InternalInvariant,
        BackendValidation,
        ResourceExhausted,
        BackendFailure,
        WorkerTerminated,
    }

    enum class Stage {
        Initialization,
        ResourceMaterialization,
        CommandValidation,
        Recording,
        RecordingInsertion,
        Submission,
        Presentation,
        GpuCompletion,
        WorkerExecution,
        Shutdown,
    }
}
