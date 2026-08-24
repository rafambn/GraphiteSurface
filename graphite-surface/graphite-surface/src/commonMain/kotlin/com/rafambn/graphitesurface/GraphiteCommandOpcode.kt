package com.rafambn.graphitesurface

internal enum class GraphiteCommandOpcode(internal val code: Int) {
    Save(1),
    Restore(2),
    Transform(3),
    ClipRect(4),
    DrawDisplayList(5),
    DrawRect(6),
    DrawRoundRect(7),
    DrawOval(8),
    DrawCircle(9),
    DrawLine(10),
    DrawPath(11),
    ;

    internal companion object {
        internal fun fromCode(code: Int): GraphiteCommandOpcode? = entries.firstOrNull { it.code == code }
    }
}
