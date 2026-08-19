@file:OptIn(
    androidx.compose.ui.ExperimentalComposeUiApi::class,
    com.rafambn.graphitesurface.ExperimentalGraphiteSurfaceApi::class,
    kotlinx.cinterop.ExperimentalForeignApi::class,
)

package com.rafambn.graphitesurface

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.UIKitView
import com.rafambn.graphitesurface.enginebridge.GSEngineCreateView
import com.rafambn.graphitesurface.enginebridge.GSEngineDisposeView
import platform.Foundation.NSBundle
import platform.UIKit.UIView
import platform.posix.dlopen

@Composable
@ExperimentalGraphiteSurfaceApi
public actual fun GraphiteSurface(
    modifier: Modifier,
) {
    val adapter = remember { GraphiteSurfaceAdapter() }

    UIKitView(
        factory = { adapter.view },
        modifier = modifier,
        onRelease = { adapter.dispose() },
    )
}

private class GraphiteSurfaceAdapter {
    val view: UIView = run {
        val frameworkPath = NSBundle.mainBundle.bundlePath + "/Frameworks/GraphiteEngine.framework/GraphiteEngine"
        val handle = dlopen(frameworkPath, 1) ?: error("GraphiteSurface: failed to dlopen GraphiteEngine.framework")
        requireNotNull(GSEngineCreateView())
    }

    fun dispose() {
        GSEngineDisposeView(view)
    }
}