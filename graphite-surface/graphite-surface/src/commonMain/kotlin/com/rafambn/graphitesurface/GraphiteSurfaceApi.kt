package com.rafambn.graphitesurface

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/** Experimental public API for the Graphite-backed Compose surface. */
@RequiresOptIn("GraphiteSurface is experimental and its API may change.")
public annotation class ExperimentalGraphiteSurfaceApi

/**
 * Hosts the isolated Graphite engine inside Compose.
 *
 * The native host implementation is intentionally introduced separately from
 * the public contract so each platform can use its native surface correctly.
 */
@Composable
@ExperimentalGraphiteSurfaceApi
public expect fun GraphiteSurface(
    modifier: Modifier = Modifier,
)
