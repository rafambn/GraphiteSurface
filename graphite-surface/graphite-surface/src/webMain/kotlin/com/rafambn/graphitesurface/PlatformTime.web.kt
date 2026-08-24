package com.rafambn.graphitesurface

import kotlinx.browser.window

internal actual fun platformMonotonicNanos(): Long = (window.performance.now() * 1_000_000.0).toLong()
