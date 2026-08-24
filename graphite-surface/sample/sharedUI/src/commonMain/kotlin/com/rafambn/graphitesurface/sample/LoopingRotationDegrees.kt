package com.rafambn.graphitesurface.sample

internal fun loopingRotationDegrees(elapsedNanos: Long): Float {
    val nanosWithinRotation = elapsedNanos.coerceAtLeast(0L) % ROTATION_PERIOD_NANOS
    return (nanosWithinRotation.toDouble() * FULL_ROTATION_DEGREES / ROTATION_PERIOD_NANOS).toFloat()
}

private const val ROTATION_PERIOD_NANOS: Long = 4_000_000_000L
private const val FULL_ROTATION_DEGREES: Double = 360.0
