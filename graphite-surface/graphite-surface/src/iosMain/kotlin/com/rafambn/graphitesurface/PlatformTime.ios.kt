@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package com.rafambn.graphitesurface

import kotlinx.cinterop.alloc
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.useContents
import platform.posix.CLOCK_MONOTONIC
import platform.posix.clock_gettime
import platform.posix.timespec

internal actual fun platformMonotonicNanos(): Long = memScoped {
    val time = alloc<timespec>()
    check(clock_gettime(CLOCK_MONOTONIC, time.ptr) == 0) { "clock_gettime failed" }
    time.useContents { tv_sec * NANOS_PER_SECOND + tv_nsec }
}

private const val NANOS_PER_SECOND: Long = 1_000_000_000L
