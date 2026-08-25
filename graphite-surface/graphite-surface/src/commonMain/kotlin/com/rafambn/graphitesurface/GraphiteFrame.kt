package com.rafambn.graphitesurface

/** Immutable ordered content for one presentation generation. */
class GraphiteFrame internal constructor(
    val presentation: GraphitePresentationInfo,
    val clearColor: GraphiteColor,
    internal val insertions: List<GraphiteFrameInsertion>,
)
