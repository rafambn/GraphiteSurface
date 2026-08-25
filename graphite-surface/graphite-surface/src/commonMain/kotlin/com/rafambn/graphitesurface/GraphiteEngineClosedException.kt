package com.rafambn.graphitesurface

/** A runtime operation was attempted after shutdown started. */
class GraphiteEngineClosedException : GraphiteException("Graphite runtime is closed")
