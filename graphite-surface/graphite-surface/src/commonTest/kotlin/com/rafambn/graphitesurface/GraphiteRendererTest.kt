package com.rafambn.graphitesurface

import androidx.compose.ui.unit.IntSize
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertSame
import kotlin.test.assertTrue
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.yield

class GraphiteRendererTest {
    @Test
    fun manualModeRendersWithTheCurrentPresentationAndCallerTime() = runTest {
        val runtime = GraphiteEngine()
        try {
            val attachmentId = requireNotNull(runtime.attachPresentation {})
            val presentation = requireNotNull(
                runtime.updatePresentation(attachmentId, IntSize(640, 480), density = 2f),
            )
            var observedRuntime: GraphiteEngine? = null
            var observedTime = -1L
            var observedPresentation: GraphitePresentationInfo? = null
            val renderer = GraphiteRenderer(
                runtime = runtime,
                renderMode = GraphiteRenderMode.Manual,
            ) { time, info ->
                observedRuntime = this
                observedTime = time
                observedPresentation = info
            }

            assertTrue(renderer.render(42L))
            assertSame(runtime, observedRuntime)
            assertEquals(42L, observedTime)
            assertEquals(presentation, observedPresentation)
        } finally {
            runtime.close()
            runtime.awaitClosed()
        }
    }

    @Test
    fun manualModeDoesNothingWhileDetached() = runTest {
        val runtime = GraphiteEngine()
        try {
            var renderCount = 0
            val renderer = GraphiteRenderer(runtime, GraphiteRenderMode.Manual) { _, _ ->
                renderCount += 1
            }

            assertFalse(renderer.render(0L))
            assertEquals(0, renderCount)
        } finally {
            runtime.close()
            runtime.awaitClosed()
        }
    }

    @Test
    fun manualRenderRejectsOtherModesAndNegativeTime() = runTest {
        val runtime = GraphiteEngine()
        try {
            val continuous = GraphiteRenderer(runtime) { _, _ -> }
            assertFailsWith<IllegalStateException> { continuous.render(0L) }
            val manual = GraphiteRenderer(runtime, GraphiteRenderMode.Manual) { _, _ -> }
            assertFailsWith<IllegalArgumentException> { manual.render(-1L) }
        } finally {
            runtime.close()
            runtime.awaitClosed()
        }
    }

    @Test
    fun onDemandRequestsAreCoalescedAndRejectedByOtherModes() = runTest {
        val runtime = GraphiteEngine()
        try {
            val renderer = GraphiteRenderer(runtime, GraphiteRenderMode.OnDemand) { _, _ -> }

            renderer.requestRender()
            renderer.requestRender()
            assertTrue(renderer.tryConsumeRenderRequest())
            assertFalse(renderer.tryConsumeRenderRequest())

            val continuous = GraphiteRenderer(runtime) { _, _ -> }
            assertFailsWith<IllegalStateException> { continuous.requestRender() }
        } finally {
            runtime.close()
            runtime.awaitClosed()
        }
    }

    @Test
    fun renderCallbacksNeverOverlap() = runTest {
        val runtime = GraphiteEngine()
        try {
            val attachmentId = requireNotNull(runtime.attachPresentation {})
            runtime.updatePresentation(attachmentId, IntSize(32, 32), density = 1f)
            val firstStarted = CompletableDeferred<Unit>()
            val releaseFirst = CompletableDeferred<Unit>()
            var activeCallbacks = 0
            var maximumActiveCallbacks = 0
            val renderer = GraphiteRenderer(runtime, GraphiteRenderMode.Manual) { time, _ ->
                activeCallbacks += 1
                maximumActiveCallbacks = maxOf(maximumActiveCallbacks, activeCallbacks)
                if (time == 1L) {
                    firstStarted.complete(Unit)
                    releaseFirst.await()
                }
                activeCallbacks -= 1
            }

            val first = launch { renderer.render(1L) }
            firstStarted.await()
            val second = launch { renderer.render(2L) }
            yield()
            assertEquals(1, maximumActiveCallbacks)
            releaseFirst.complete(Unit)
            first.join()
            second.join()
            assertEquals(1, maximumActiveCallbacks)
        } finally {
            runtime.close()
            runtime.awaitClosed()
        }
    }

    @Test
    fun scheduledFrameIsDiscardedAfterModeOrGenerationMismatch() = runTest {
        val runtime = GraphiteEngine()
        try {
            val attachmentId = requireNotNull(runtime.attachPresentation {})
            val firstPresentation = requireNotNull(
                runtime.updatePresentation(attachmentId, IntSize(32, 32), density = 1f),
            )
            var renderCount = 0
            val renderer = GraphiteRenderer(runtime, GraphiteRenderMode.Continuous) { _, _ ->
                renderCount += 1
            }

            assertTrue(
                renderer.renderScheduled(
                    frameTimeNanos = 1L,
                    mode = GraphiteRenderMode.Continuous,
                    presentation = firstPresentation,
                ),
            )
            assertFalse(
                renderer.renderScheduled(
                    frameTimeNanos = 2L,
                    mode = GraphiteRenderMode.OnDemand,
                    presentation = firstPresentation,
                ),
            )
            runtime.updatePresentation(attachmentId, IntSize(64, 64), density = 1f)
            assertFalse(
                renderer.renderScheduled(
                    frameTimeNanos = 3L,
                    mode = GraphiteRenderMode.Continuous,
                    presentation = firstPresentation,
                ),
            )
            assertEquals(1, renderCount)
        } finally {
            runtime.close()
            runtime.awaitClosed()
        }
    }
}
