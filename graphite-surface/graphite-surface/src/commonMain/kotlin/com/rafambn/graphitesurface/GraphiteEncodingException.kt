package com.rafambn.graphitesurface

/** Failure while constructing a portable drawing command buffer. */
public sealed class GraphiteEncodingException(message: String) : GraphiteException(message) {
    public class CommandBufferTooLarge(public val limitBytes: Int) :
        GraphiteEncodingException("command buffer exceeds the $limitBytes-byte limit")

    public class ClosedResource(public val resource: String) :
        GraphiteEncodingException("$resource is closed")
}
