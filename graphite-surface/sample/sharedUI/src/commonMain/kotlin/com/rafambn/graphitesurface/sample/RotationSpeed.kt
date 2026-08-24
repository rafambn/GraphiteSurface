@file:OptIn(kotlin.concurrent.atomics.ExperimentalAtomicApi::class)

package com.rafambn.graphitesurface.sample

import kotlin.concurrent.atomics.AtomicReference

internal class RotationSpeed {
    private val value = AtomicReference(DEFAULT_ROTATION_SPEED)

    internal fun update(speed: Float) {
        value.store(speed.coerceIn(MIN_ROTATION_SPEED, MAX_ROTATION_SPEED))
    }

    internal fun read(): Float = value.load()
}
