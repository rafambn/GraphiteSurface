package com.rafambn.graphitesurface

/** Required GPU or worker capabilities are unavailable. */
class GraphiteUnsupportedPlatformException(val report: GraphiteSupportReport) :
    GraphiteException("Graphite is unsupported on ${report.platform}: ${report.details}")
