package com.rafambn.graphitesurface

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember

/** Creates and remembers the state used to request on-demand frames. */
@Composable
internal fun rememberGraphiteSurfaceState(): GraphiteSurfaceState =
    remember { GraphiteSurfaceState() }
