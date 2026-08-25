package com.rafambn.graphitesurface

/** Failure while constructing a portable drawing command buffer. */
sealed class GraphiteEncodingException(message: String) : GraphiteException(message) {
    class CommandBufferTooLarge(val limitBytes: Int) :
        GraphiteEncodingException("command buffer exceeds the $limitBytes-byte limit")
}
