package com.rafambn.graphitesurface

internal fun unsupportedGraphiteSurfaceHost(platform: String): Nothing = error(
    "GraphiteSurface has no native GPU engine for $platform yet. " +
        "Use the iOS engine or provide a platform engine implementation."
)
