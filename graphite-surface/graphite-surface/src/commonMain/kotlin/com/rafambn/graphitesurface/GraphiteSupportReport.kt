package com.rafambn.graphitesurface

/** Platform capability report attached to unsupported-platform failures. */
public data class GraphiteSupportReport(
    public val platform: String,
    public val missingCapabilities: List<String>,
    public val details: String,
)
