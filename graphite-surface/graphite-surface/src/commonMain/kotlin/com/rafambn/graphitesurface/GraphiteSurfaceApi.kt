package com.rafambn.graphitesurface

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/** Experimental public API for the Graphite-backed Compose surface. */
@RequiresOptIn("GraphiteSurface is experimental and its API may change.")
public annotation class ExperimentalGraphiteSurfaceApi

/** Contract implemented by a renderer hosted by [GraphiteSurface]. */
@ExperimentalGraphiteSurfaceApi
public interface GraphiteRenderer {
    public fun onSurfaceCreated(session: GraphiteSession)

    public fun onSurfaceChanged(width: Int, height: Int)

    public fun onDrawFrame()

    public fun onSurfaceDestroyed() = Unit
}

/** Opaque session owned by the platform Graphite host. */
@ExperimentalGraphiteSurfaceApi
public interface GraphiteSession {
    public val width: Int
    public val height: Int
    public fun requestRedraw()
}

/**
 * Hosts a Graphite renderer inside Compose.
 *
 * The native host implementation is intentionally introduced separately from
 * the public contract so each platform can use its native surface correctly.
 */
@Composable
@ExperimentalGraphiteSurfaceApi
public expect fun GraphiteSurface(
    modifier: Modifier = Modifier,
    renderer: GraphiteRenderer,
)
