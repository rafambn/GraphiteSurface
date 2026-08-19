@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package com.rafambn.graphitesurface

import com.rafambn.graphitesurface.engine.GraphiteEngineGraphiteEngineView_iosKt
import platform.UIKit.UIView

internal class GSGraphiteDrawContext(
    private val view: UIView,
) : GraphiteDrawContext {
    override fun clear(color: Long) {
        GraphiteEngineGraphiteEngineView_iosKt.gsClearView(view, color.toUInt())
    }

    override fun save() {
        GraphiteEngineGraphiteEngineView_iosKt.gsSaveView(view)
    }

    override fun restore() {
        GraphiteEngineGraphiteEngineView_iosKt.gsRestoreView(view)
    }

    override fun translate(x: Float, y: Float) {
        GraphiteEngineGraphiteEngineView_iosKt.gsTranslateView(view, x, y)
    }

    override fun rotate(degrees: Float) {
        GraphiteEngineGraphiteEngineView_iosKt.gsRotateView(view, degrees)
    }

    override fun beginPath() {
        GraphiteEngineGraphiteEngineView_iosKt.gsBeginPathView(view)
    }

    override fun moveTo(x: Float, y: Float) {
        GraphiteEngineGraphiteEngineView_iosKt.gsMoveToView(view, x, y)
    }

    override fun lineTo(x: Float, y: Float) {
        GraphiteEngineGraphiteEngineView_iosKt.gsLineToView(view, x, y)
    }

    override fun closePath() {
        GraphiteEngineGraphiteEngineView_iosKt.gsClosePathView(view)
    }

    override fun drawPath(color: Long, antiAlias: Boolean) {
        GraphiteEngineGraphiteEngineView_iosKt.gsDrawPathView(
            view,
            color.toUInt(),
            if (antiAlias) 1 else 0,
        )
    }
}
