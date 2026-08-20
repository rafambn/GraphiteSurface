package com.rafambn.graphitesurface

import com.rafambn.graphitesurface.engine.WebGraphiteDrawContext as EngineDrawContext

internal class WebGraphiteDrawContext(
    private val delegate: EngineDrawContext,
) : GraphiteDrawContext {
    override fun clear(color: Long) {
        delegate.clear(color)
    }

    override fun save() {
        delegate.save()
    }

    override fun restore() {
        delegate.restore()
    }

    override fun translate(x: Float, y: Float) {
        delegate.translate(x, y)
    }

    override fun rotate(degrees: Float) {
        delegate.rotate(degrees)
    }

    override fun beginPath() {
        delegate.beginPath()
    }

    override fun moveTo(x: Float, y: Float) {
        delegate.moveTo(x, y)
    }

    override fun lineTo(x: Float, y: Float) {
        delegate.lineTo(x, y)
    }

    override fun closePath() {
        delegate.closePath()
    }

    override fun drawPath(color: Long, antiAlias: Boolean) {
        delegate.drawPath(color, antiAlias)
    }
}
