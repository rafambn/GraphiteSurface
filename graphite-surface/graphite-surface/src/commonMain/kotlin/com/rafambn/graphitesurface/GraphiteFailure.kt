package com.rafambn.graphitesurface

/** Fatal runtime failure reported through [GraphiteRuntimeState.Failed]. */
public data class GraphiteFailure(
    public val kind: Kind,
    public val stage: Stage,
    public val message: String,
    public val cause: Throwable? = null,
) {
    public enum class Kind {
        InternalInvariant,
        BackendValidation,
        ResourceExhausted,
        BackendFailure,
        WorkerTerminated,
    }

    public enum class Stage {
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
