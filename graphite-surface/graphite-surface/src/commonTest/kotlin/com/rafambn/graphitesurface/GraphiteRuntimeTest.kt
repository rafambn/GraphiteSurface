package com.rafambn.graphitesurface

import com.rafambn.scribe.Archivist
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest

class GraphiteRuntimeTest {
    @Test
    fun recorderBuildsReusableCommandsAsynchronously() = runTest {
        val runtime = GraphiteRuntime.create(GraphiteRuntimeConfig(recorderCount = 2))
        try {
            val target = runtime.createRecordingTarget(GraphiteSize(256, 256))
            val roads = GraphiteDisplayList.build {
                drawPath(
                    GraphitePath.build {
                        moveTo(0f, 0f)
                        lineTo(100f, 100f)
                    },
                    GraphitePaint(GraphiteColor.White, GraphitePaint.Style.Stroke, strokeWidth = 4f),
                )
            }

            val recording = runtime.recorders[1].record(target) {
                draw(roads, GraphiteTransform.translation(8f, 12f))
                drawCircle(GraphitePoint(20f, 30f), 5f, GraphitePaint(GraphiteColor.Black))
            }

            assertEquals(GraphiteSize(256, 256), recording.target.pixelSize)
            assertEquals(1, runtime.metricsSnapshot().recorders[1].completed)
            recording.close()
            roads.close()
        } finally {
            runtime.close()
            runtime.awaitClosed()
        }
        assertEquals(GraphiteRuntimeState.Closed, runtime.state.value)
    }

    @Test
    fun presentationMailboxKeepsOnlyTheNewestFrame() = runTest {
        val runtime = GraphiteRuntime.create()
        try {
            var requests = 0
            val attachmentId = requireNotNull(runtime.attachPresentation { requests += 1 })
            val presentation = requireNotNull(
                runtime.updatePresentation(attachmentId, GraphiteSize(640, 480), density = 2f),
            )
            val first = runtime.createFrame(presentation, GraphiteColor.Black)
            val second = runtime.createFrame(presentation, GraphiteColor.White)

            assertEquals(GraphitePresentResult.Accepted, runtime.present(first))
            assertEquals(GraphitePresentResult.ReplacedPending, runtime.present(second))
            val pending = runtime.takePendingFrame(attachmentId)
            assertEquals(GraphiteColor.White, pending?.clearColor)
            pending?.close()
            assertEquals(2, requests)
            assertEquals(0, runtime.metricsSnapshot().pendingFrames)
        } finally {
            runtime.close()
            runtime.awaitClosed()
        }
    }

    @Test
    fun sameDisplayListRegistersIndependentlyAndPublishesOncePerRuntimeWorker() = runTest {
        val displayList = GraphiteDisplayList.build {
            drawCircle(GraphitePoint(4f, 4f), 2f, GraphitePaint(GraphiteColor.White))
        }
        val first = GraphiteRuntime.create()
        val second = GraphiteRuntime.create()
        try {
            suspend fun recordTwice(runtime: GraphiteRuntime) {
                val target = runtime.createRecordingTarget(GraphiteSize(8, 8))
                repeat(2) {
                    runtime.recorders.single().record(target) { draw(displayList) }.close()
                }
            }

            recordTwice(first)
            recordTwice(second)

            val firstMetrics = first.metricsSnapshot().resources
            val secondMetrics = second.metricsSnapshot().resources
            assertEquals(1, firstMetrics.registered)
            assertEquals(1, firstMetrics.publications)
            assertEquals(1, firstMetrics.cacheHits)
            assertEquals(firstMetrics.registeredBytes, firstMetrics.publishedBytes)
            assertEquals(1, secondMetrics.registered)
            assertEquals(1, secondMetrics.publications)
            assertEquals(1, secondMetrics.cacheHits)
        } finally {
            displayList.close()
            first.close()
            second.close()
            first.awaitClosed()
            second.awaitClosed()
        }
        assertEquals(1, first.metricsSnapshot().resources.released)
        assertEquals(1, second.metricsSnapshot().resources.released)
    }

    @Test
    fun retainedWorkSurvivesClosingEveryCallerOwnedHandle() = runTest {
        val runtime = GraphiteRuntime.create()
        try {
            val attachmentId = requireNotNull(runtime.attachPresentation {})
            val presentation = requireNotNull(
                runtime.updatePresentation(attachmentId, GraphiteSize(16, 16), density = 1f),
            )
            val target = runtime.createRecordingTarget(presentation.pixelSize)
            val displayList = GraphiteDisplayList.build {
                drawRect(GraphiteRect(0f, 0f, 8f, 8f), GraphitePaint(GraphiteColor.White))
            }
            val recording = runtime.recorders.single().record(target) {
                draw(displayList)
                displayList.close()
            }
            val frame = runtime.createFrame(presentation) { insert(recording) }
            recording.close()

            assertEquals(GraphitePresentResult.Accepted, runtime.present(frame))
            frame.close()

            val pending = requireNotNull(runtime.takePendingFrame(attachmentId))
            try {
                val insertion = pending.insertions.single()
                insertion.recording.value.program.validate()
                assertEquals(target.pixelSize, insertion.targetSize)
            } finally {
                pending.close()
            }
            assertTrue(displayList.isClosed)
            assertTrue(recording.isClosed)
            assertTrue(frame.isClosed)
        } finally {
            runtime.close()
            runtime.awaitClosed()
        }
    }

    @Test
    fun runtimeResourceIdsAreMonotonicWithinOneNamespace() = runTest {
        val first = GraphiteDisplayList.build {}
        val second = GraphiteDisplayList.build {}
        val rootWriter = GraphiteCommandWriter(GraphiteCommandBufferLimit.Default.bytes)
        val root = try {
            GraphiteEncoderImpl(rootWriter, cancellationProbe = {}).apply {
                draw(first)
                draw(second)
            }
            rootWriter.finish()
        } catch (error: Throwable) {
            rootWriter.close()
            throw error
        }
        val registry = GraphiteResourceRegistry()
        try {
            val message = registry.prepare(root, workerIndex = 0)
            val reader = GraphiteCommandReader(message)
            assertEquals(GraphiteWorkerMessage.Magic, reader.readInt())
            assertEquals(GraphiteWorkerMessage.Version, reader.readInt())
            assertEquals(2, reader.readInt())
            val firstId = reader.readLong()
            reader.skipPublication()
            val secondId = reader.readLong()
            assertTrue(firstId > 0)
            assertTrue(secondId > firstId)
        } finally {
            registry.close()
            root.close()
            first.close()
            second.close()
        }
    }

    @Test
    fun cancellationDoesNotCorruptTheWorkerResourceCache() = runTest {
        val runtime = GraphiteRuntime.create(
            GraphiteRuntimeConfig(recorderQueueCapacity = 4),
        )
        val displayList = GraphiteDisplayList.build {
            repeat(1_000) { index ->
                drawCircle(
                    GraphitePoint(index.toFloat(), index.toFloat()),
                    1f,
                    GraphitePaint(GraphiteColor.White),
                )
            }
        }
        try {
            val target = runtime.createRecordingTarget(GraphiteSize(8, 8))
            val jobs = List(32) {
                launch {
                    runtime.recorders.single().record(target) { draw(displayList) }.close()
                }
            }
            jobs.forEach { it.cancelAndJoin() }

            runtime.recorders.single().record(target) { draw(displayList) }.close()
            assertIs<GraphiteRuntimeState.Ready>(runtime.state.value)
        } finally {
            displayList.close()
            runtime.close()
            runtime.awaitClosed()
        }
    }

    @Test
    fun runtimeIdentityIsValidatedBeforeRecording() = runTest {
        val first = GraphiteRuntime.create()
        val second = GraphiteRuntime.create()
        try {
            val foreignTarget = first.createRecordingTarget(GraphiteSize(1, 1))
            assertFailsWith<GraphitePresentationException> {
                second.recorders.single().record(foreignTarget) { }
            }
        } finally {
            first.close()
            second.close()
            first.awaitClosed()
            second.awaitClosed()
        }
    }

    @Test
    fun commandLimitFailsOnlyTheEncodingOperation() = runTest {
        val runtime = GraphiteRuntime.create(
            GraphiteRuntimeConfig(maxCommandBufferBytes = GraphiteCommandBufferLimit(32)),
        )
        try {
            val target = runtime.createRecordingTarget(GraphiteSize(1, 1))
            assertFailsWith<GraphiteEncodingException.CommandBufferTooLarge> {
                runtime.recorders.single().record(target) {
                    drawRect(
                        GraphiteRect(0f, 0f, 1f, 1f),
                        GraphitePaint(GraphiteColor.White),
                    )
                }
            }
            assertIs<GraphiteRuntimeState.Ready>(runtime.state.value)
        } finally {
            runtime.close()
            runtime.awaitClosed()
        }
    }

    @Test
    fun runtimeDrainsArchivistDuringShutdown() = runTest {
        val entries = mutableListOf<Map<String, *>>()
        val runtime = GraphiteRuntime.create(
            GraphiteRuntimeConfig(
                archivist = Archivist { entry -> entries += entry },
            ),
        )

        runtime.close()
        runtime.awaitClosed()

        assertEquals(3, entries.size)
    }

    @Test
    fun transformCompositionUsesColumnMajorSemantics() {
        val transform = GraphiteTransform.translation(10f, 20f) * GraphiteTransform.scale(2f)

        assertEquals(2f, transform[0, 0])
        assertEquals(2f, transform[1, 1])
        assertEquals(10f, transform[3, 0])
        assertEquals(20f, transform[3, 1])
    }

    private fun GraphiteCommandReader.skipPublication() {
        repeat(readInt()) { readLong() }
        readBytes(readInt())
    }
}
