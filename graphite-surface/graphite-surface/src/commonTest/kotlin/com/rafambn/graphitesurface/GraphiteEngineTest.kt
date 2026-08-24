package com.rafambn.graphitesurface

import com.rafambn.scribe.Archivist
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest

class GraphiteEngineTest {
    @Test
    fun constructorRejectsInvalidWorkerAndSubmissionLimits() {
        assertFailsWith<IllegalArgumentException> { GraphiteEngine(recorderCount = 0) }
        assertFailsWith<IllegalArgumentException> { GraphiteEngine(recorderCount = 65) }
        assertFailsWith<IllegalArgumentException> { GraphiteEngine(recorderQueueCapacity = 0) }
        assertFailsWith<IllegalArgumentException> { GraphiteEngine(recorderQueueCapacity = 1025) }
        assertFailsWith<IllegalArgumentException> { GraphiteEngine(maxFramesInFlight = 0) }
        assertFailsWith<IllegalArgumentException> { GraphiteEngine(maxFramesInFlight = 9) }
    }

    @Test
    fun recorderBuildsReusableCommandsAsynchronously() = runTest {
        val runtime = GraphiteEngine(recorderCount = 2)
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
        } finally {
            runtime.close()
            runtime.awaitClosed()
        }
        assertEquals(GraphiteEngineState.Closed, runtime.state.value)
    }

    @Test
    fun presentationMailboxKeepsOnlyTheNewestFrame() = runTest {
        val runtime = GraphiteEngine()
        try {
            var requests = 0
            val attachmentId = requireNotNull(runtime.attachPresentation { requests += 1 })
            val presentation = requireNotNull(
                runtime.updatePresentation(attachmentId, GraphiteSize(640, 480), density = 2f),
            )
            val first = runtime.createFrame(presentation, GraphiteColor.Black)
            val second = runtime.createFrame(presentation, GraphiteColor.White)

            assertFalse(runtime.hasPendingFrame(attachmentId))
            assertEquals(GraphitePresentResult.Accepted, runtime.present(first))
            assertTrue(runtime.hasPendingFrame(attachmentId))
            assertEquals(GraphitePresentResult.ReplacedPending, runtime.present(second))
            val pending = runtime.takePendingFrame(attachmentId)
            assertEquals(GraphiteColor.White, pending?.clearColor)
            pending?.close()
            assertFalse(runtime.hasPendingFrame(attachmentId))
            assertEquals(2, requests)
            assertEquals(0, runtime.metricsSnapshot().pendingFrames)
        } finally {
            runtime.close()
            runtime.awaitClosed()
        }
    }

    @Test
    fun equivalentDisplayListsRegisterOncePerRuntimeWorker() = runTest {
        val first = GraphiteEngine()
        val second = GraphiteEngine()
        try {
            suspend fun recordTwice(runtime: GraphiteEngine) {
                val target = runtime.createRecordingTarget(GraphiteSize(8, 8))
                repeat(2) {
                    val displayList = GraphiteDisplayList.build {
                        drawCircle(
                            GraphitePoint(4f, 4f),
                            2f,
                            GraphitePaint(GraphiteColor.White),
                        )
                    }
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
            first.close()
            second.close()
            first.awaitClosed()
            second.awaitClosed()
        }
        assertEquals(1, first.metricsSnapshot().resources.released)
        assertEquals(1, second.metricsSnapshot().resources.released)
    }

    @Test
    fun retainedWorkSurvivesClosingRecordingAndFrameHandles() = runTest {
        val runtime = GraphiteEngine()
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
        val second = GraphiteDisplayList.build {
            drawCircle(GraphitePoint(4f, 4f), 2f, GraphitePaint(GraphiteColor.White))
        }
        val rootWriter = GraphiteCommandWriter(GraphiteCommandBufferLimit.Default.bytes)
        GraphiteEncoderImpl(rootWriter, cancellationProbe = {}).apply {
            draw(first)
            draw(second)
        }
        val root = rootWriter.finish()
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
        }
    }

    @Test
    fun cancellationDoesNotCorruptTheWorkerResourceCache() = runTest {
        val runtime = GraphiteEngine(recorderQueueCapacity = 4)
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
            assertIs<GraphiteEngineState.Ready>(runtime.state.value)
        } finally {
            runtime.close()
            runtime.awaitClosed()
        }
    }

    @Test
    fun runtimeIdentityIsValidatedBeforeRecording() = runTest {
        val first = GraphiteEngine()
        val second = GraphiteEngine()
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
        val runtime = GraphiteEngine(
            maxCommandBufferBytes = GraphiteCommandBufferLimit(32),
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
            assertIs<GraphiteEngineState.Ready>(runtime.state.value)
        } finally {
            runtime.close()
            runtime.awaitClosed()
        }
    }

    @Test
    fun runtimeDrainsArchivistDuringShutdown() = runTest {
        val entries = mutableListOf<Map<String, *>>()
        val runtime = GraphiteEngine(
            archivist = Archivist { entry -> entries += entry },
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
