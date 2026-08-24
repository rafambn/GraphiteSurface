@file:OptIn(kotlin.concurrent.atomics.ExperimentalAtomicApi::class)

package com.rafambn.graphitesurface

import kotlin.concurrent.atomics.AtomicLong
import kotlin.concurrent.atomics.AtomicReference

internal class GraphiteRuntimeRenderer(private val runtime: GraphiteRuntime) :
    GraphitePresentationRenderer {
    private val attachmentId: AtomicLong = AtomicLong(0)
    private val density: AtomicReference<Float> = AtomicReference(1f)

    internal fun bind(id: Long, density: Float) {
        this.density.store(density)
        attachmentId.store(id)
    }

    internal fun unbind(expectedId: Long?) {
        if (expectedId == null) return
        attachmentId.compareAndSet(expectedId, 0)
    }

    override fun onSurfaceCreated() = Unit

    override fun onSurfaceChanged(size: GraphiteSize) {
        val id = attachmentId.load()
        if (id != 0L) runtime.updatePresentation(id, size, density.load())
    }

    override fun hasPendingFrame(): Boolean {
        val id = attachmentId.load()
        return id != 0L && runtime.hasPendingFrame(id)
    }

    override fun onDrawFrame(context: GraphiteDrawContext) {
        val id = attachmentId.load()
        if (id == 0L) return
        val frame = runtime.takePendingFrame(id) ?: return
        try {
            context.clear(frame.clearColor.toArgbLong())
            frame.insertions.forEach { insertion ->
                context.save()
                try {
                    context.translate(
                        insertion.translation.x.toFloat(),
                        insertion.translation.y.toFloat(),
                    )
                    insertion.clip?.let { clip ->
                        context.clipRect(
                            GraphiteRect(
                                clip.left.toFloat(),
                                clip.top.toFloat(),
                                clip.right.toFloat(),
                                clip.bottom.toFloat(),
                            ),
                            antiAlias = false,
                        )
                    }
                    context.executeGraphiteCommands(insertion.recording.value.program)
                } finally {
                    context.restore()
                }
            }
        } catch (error: Throwable) {
            runtime.failFromRenderWorker(error)
        } finally {
            frame.close()
        }
    }

    override fun onSurfaceError(error: Throwable) {
        runtime.failFromRenderWorker(error)
    }
}
