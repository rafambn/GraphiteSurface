package com.rafambn.graphitesurface

/** Required GPU or worker capabilities are unavailable. */
public class GraphiteUnsupportedPlatformException(public val report: GraphiteSupportReport) :
    GraphiteException("Graphite is unsupported on ${report.platform}: ${report.details}")
