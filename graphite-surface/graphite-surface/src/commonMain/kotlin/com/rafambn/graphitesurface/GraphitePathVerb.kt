package com.rafambn.graphitesurface

internal enum class GraphitePathVerb(internal val code: Byte) {
    Move(1),
    Line(2),
    Close(3),
}
