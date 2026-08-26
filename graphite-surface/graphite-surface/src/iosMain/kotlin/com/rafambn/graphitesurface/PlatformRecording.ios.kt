@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package com.rafambn.graphitesurface

import com.rafambn.graphitesurface.engine.GraphiteEngineGraphiteEngineView_iosKt
import kotlin.experimental.ExperimentalNativeApi
import kotlin.native.ref.createCleaner

internal actual class PlatformRecording(
    internal val handle: ULong,
) {
    @OptIn(ExperimentalNativeApi::class)
    @Suppress("unused")
    private val cleaner = createCleaner(handle) { recording ->
        if (recording != 0uL) {
            GraphiteEngineGraphiteEngineView_iosKt.gsDisposeRecordingRecording(recording)
        }
    }
}
