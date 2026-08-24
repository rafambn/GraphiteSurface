package com.rafambn.graphitesurface

/** Immutable ordered content for one presentation generation. */
public class GraphiteFrame internal constructor(
    public val presentation: GraphitePresentationInfo,
    public val clearColor: GraphiteColor,
    internal val insertions: List<GraphiteFrameInsertion>,
)
