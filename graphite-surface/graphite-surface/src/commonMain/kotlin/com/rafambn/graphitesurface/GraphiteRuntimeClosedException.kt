package com.rafambn.graphitesurface

/** A runtime operation was attempted after shutdown started. */
public class GraphiteRuntimeClosedException : GraphiteException("Graphite runtime is closed")
