package com.rafambn.graphitesurface.sample

internal const val DEFAULT_ROTATION_SPEED = 1f
internal const val MIN_ROTATION_SPEED = 0f
internal const val MAX_ROTATION_SPEED = 2f

internal fun loopingRotationDegrees(
    elapsedNanos: Long,
    rotationSpeed: Float = DEFAULT_ROTATION_SPEED,
): Float {
    val speed = rotationSpeed.coerceIn(MIN_ROTATION_SPEED, MAX_ROTATION_SPEED)
    val nanosWithinRotation = (
        elapsedNanos.coerceAtLeast(0L).toDouble() * speed
    ) % ROTATION_PERIOD_NANOS
    return (nanosWithinRotation * FULL_ROTATION_DEGREES / ROTATION_PERIOD_NANOS).toFloat()
}

private const val ROTATION_PERIOD_NANOS: Long = 4_000_000_000L
private const val FULL_ROTATION_DEGREES: Double = 360.0
