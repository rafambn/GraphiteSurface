package com.rafambn.graphitesurface

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember

/** Creates and remembers the state used to request on-demand frames. */
@Composable
public fun rememberGraphiteSurfaceState(): GraphiteSurfaceState =
    remember { GraphiteSurfaceState() }
