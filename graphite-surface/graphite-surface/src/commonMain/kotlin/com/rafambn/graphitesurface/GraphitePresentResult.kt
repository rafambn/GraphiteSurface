package com.rafambn.graphitesurface

/** Immediate result of placing a frame in the latest-wins presentation mailbox. */
public enum class GraphitePresentResult {
    Accepted,
    ReplacedPending,
    NoPresentation,
    StalePresentation,
    RuntimeUnavailable,
}
