package com.rafambn.graphitesurface

/** Platform capability report attached to unsupported-platform failures. */
data class GraphiteSupportReport(
    val platform: String,
    val missingCapabilities: List<String>,
    val details: String,
)
