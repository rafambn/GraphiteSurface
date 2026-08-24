package com.rafambn.graphitesurface

internal class GraphiteRecordingContent(
    internal val program: GraphiteCommandProgram,
) : AutoCloseable {
    override fun close() {
        program.close()
    }
}
