package com.rafambn.graphitesurface

/** Immediate result of placing compiled frame commands in the latest-wins mailbox. */
enum class GraphitePresentResult {
    Accepted,
    ReplacedPending,
    NoPresentation,
    StalePresentation,
    RuntimeUnavailable,
}
