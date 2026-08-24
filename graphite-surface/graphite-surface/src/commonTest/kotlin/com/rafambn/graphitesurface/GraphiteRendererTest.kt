package com.rafambn.graphitesurface

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.yield

class GraphiteRendererTest {
    @Test
    fun manualModeRendersWithTheCurrentPresentationAndCallerTime() = runTest {
        val runtime = GraphiteRuntime.create()
        try {
            val attachmentId = requireNotNull(runtime.attachPresentation {})
            val presentation = requireNotNull(
                runtime.updatePresentation(attachmentId, GraphiteSize(640, 480), density = 2f),
            )
            var observedTime = -1L
            var observedPresentation: GraphitePresentationInfo? = null
            val renderer = GraphiteRenderer(runtime, GraphiteRenderMode.Manual) { time, info ->
                observedTime = time
                observedPresentation = info
            }

            assertTrue(renderer.render(42L))
            assertEquals(42L, observedTime)
            assertEquals(presentation, observedPresentation)
        } finally {
            runtime.close()
            runtime.awaitClosed()
        }
    }

    @Test
    fun manualModeDoesNothingWhileDetached() = runTest {
        val runtime = GraphiteRuntime.create()
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
        val runtime = GraphiteRuntime.create()
        try {
            val renderer = GraphiteRenderer(runtime) { _, _ -> }
            assertFailsWith<IllegalStateException> { renderer.render(0L) }

            renderer.renderMode = GraphiteRenderMode.Manual
            assertFailsWith<IllegalArgumentException> { renderer.render(-1L) }
        } finally {
            runtime.close()
            runtime.awaitClosed()
        }
    }

    @Test
    fun onDemandRequestsAreCoalescedAndIgnoredByOtherModes() = runTest {
        val runtime = GraphiteRuntime.create()
        try {
            val renderer = GraphiteRenderer(runtime, GraphiteRenderMode.OnDemand) { _, _ -> }

            renderer.requestRender()
            renderer.requestRender()
            assertTrue(renderer.tryConsumeRenderRequest())
            assertFalse(renderer.tryConsumeRenderRequest())

            renderer.renderMode = GraphiteRenderMode.Continuous
            renderer.requestRender()
            assertFalse(renderer.tryConsumeRenderRequest())
        } finally {
            runtime.close()
            runtime.awaitClosed()
        }
    }

    @Test
    fun renderCallbacksNeverOverlap() = runTest {
        val runtime = GraphiteRuntime.create()
        try {
            val attachmentId = requireNotNull(runtime.attachPresentation {})
            runtime.updatePresentation(attachmentId, GraphiteSize(32, 32), density = 1f)
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
    fun scheduledFrameIsDiscardedAfterModeOrGenerationChanges() = runTest {
        val runtime = GraphiteRuntime.create()
        try {
            val attachmentId = requireNotNull(runtime.attachPresentation {})
            val firstPresentation = requireNotNull(
                runtime.updatePresentation(attachmentId, GraphiteSize(32, 32), density = 1f),
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
            renderer.renderMode = GraphiteRenderMode.OnDemand
            assertFalse(
                renderer.renderScheduled(
                    frameTimeNanos = 2L,
                    mode = GraphiteRenderMode.Continuous,
                    presentation = firstPresentation,
                ),
            )
            runtime.updatePresentation(attachmentId, GraphiteSize(64, 64), density = 1f)
            assertFalse(
                renderer.renderScheduled(
                    frameTimeNanos = 3L,
                    mode = GraphiteRenderMode.OnDemand,
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
