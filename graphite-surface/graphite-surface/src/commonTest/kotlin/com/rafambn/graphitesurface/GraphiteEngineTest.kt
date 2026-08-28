package com.rafambn.graphitesurface

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
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
    fun rejectsASecondPresentationAttachment() = runTest {
        val runtime = GraphiteEngine()
        try {
            runtime.attachPresentation {}
            assertFailsWith<IllegalStateException> { runtime.attachPresentation {} }
        } finally {
            runtime.close()
            runtime.awaitClosed()
        }
    }

    @Test
    fun constructorRejectsInvalidWorkerLimits() {
        assertFailsWith<IllegalArgumentException> { GraphiteEngine(recorderCount = 0) }
        assertFailsWith<IllegalArgumentException> { GraphiteEngine(recorderCount = 65) }
        assertFailsWith<IllegalArgumentException> { GraphiteEngine(recorderQueueCapacity = 0) }
        assertFailsWith<IllegalArgumentException> { GraphiteEngine(recorderQueueCapacity = 1025) }
    }

    @Test
    fun recorderBuildsReusableCommandsAsynchronously() = runTest {
        val runtime = GraphiteEngine(recorderCount = 2)
        try {
            val roads = graphiteDisplayList {
                drawPath(
                    Path().apply {
                        moveTo(0f, 0f)
                        lineTo(100f, 100f)
                    },
                    Color.White,
                    GraphiteDrawStyle.Stroke(4f),
                )
            }

            val recording = runtime.recorders[1].record {
                withTransform(GraphiteTransform.translation(8f, 12f)) { draw(roads) }
                drawCircle(Offset(20f, 30f), 5f, Color.Black)
            }

            assertEquals(1, runtime.diagnostics.snapshot().recorders[1].completed)
            recording.program.validate()
        } finally {
            runtime.close()
            runtime.awaitClosed()
        }
        assertEquals(GraphiteEngineState.Closed, runtime.diagnostics.state.value)
    }

    @Test
    fun presentationMailboxKeepsOnlyTheNewestFrame() = runTest {
        val runtime = GraphiteEngine()
        try {
            var requests = 0
            val attachmentId = requireNotNull(runtime.attachPresentation { requests += 1 })
            val presentation = requireNotNull(
                runtime.updatePresentation(attachmentId, IntSize(640, 480), density = 2f),
            )

            assertFalse(runtime.hasPendingFrame(attachmentId))
            assertEquals(GraphitePresentResult.Accepted, runtime.present(presentation, Color.Black))
            assertTrue(runtime.hasPendingFrame(attachmentId))
            assertEquals(
                GraphitePresentResult.ReplacedPending,
                runtime.present(presentation, Color.White),
            )
            val pending = runtime.takePendingFrame(attachmentId)
            assertEquals(Color.White, pending?.clearColor)
            assertFalse(runtime.hasPendingFrame(attachmentId))
            assertEquals(2, requests)
            assertEquals(0, runtime.diagnostics.snapshot().pendingFrames)
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
                repeat(2) {
                    val displayList = graphiteDisplayList {
                        drawCircle(
                            Offset(4f, 4f),
                            2f,
                            Color.White,
                        )
                    }
                    runtime.recorders.single().record { draw(displayList) }
                }
            }

            recordTwice(first)
            recordTwice(second)

            val firstMetrics = first.diagnostics.snapshot().resources
            val secondMetrics = second.diagnostics.snapshot().resources
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
        assertEquals(1, first.diagnostics.snapshot().resources.released)
        assertEquals(1, second.diagnostics.snapshot().resources.released)
    }

    @Test
    fun pendingFrameKeepsImmutableRecordingCommands() = runTest {
        val runtime = GraphiteEngine()
        try {
            val attachmentId = requireNotNull(runtime.attachPresentation {})
            val presentation = requireNotNull(
                runtime.updatePresentation(attachmentId, IntSize(16, 16), density = 1f),
            )
            val displayList = graphiteDisplayList {
                drawRect(Rect(0f, 0f, 8f, 8f), Color.White)
            }
            val recording = runtime.recorders.single().record {
                draw(displayList)
            }
            assertEquals(
                GraphitePresentResult.Accepted,
                runtime.present(presentation) {
                    insert(recording, translation = IntOffset(2, 3))
                },
            )

            val pending = requireNotNull(runtime.takePendingFrame(attachmentId))
            val insertion = pending.insertions.single()
            assertEquals(GraphiteTransform.translation(2f, 3f), insertion.transform)
            insertion.program.validate()
        } finally {
            runtime.close()
            runtime.awaitClosed()
        }
    }

    @Test
    fun rendererDelegatesCompletedRecordingsToThePlatformContext() = runTest {
        val runtime = GraphiteEngine()
        try {
            val attachmentId = runtime.attachPresentation {}
            val presentation = requireNotNull(
                runtime.updatePresentation(attachmentId, IntSize(16, 16), density = 1f),
            )
            val recording = runtime.recorders.single().record {
                drawCircle(Offset(4f, 4f), 2f, Color.White)
            }
            var insertedRecording: PlatformRecording? = null
            var insertedProgram: GraphiteCommandProgram? = null
            var insertedTransform: GraphiteTransform? = null
            val context = object : GraphiteDrawContext {
                override fun insertRecording(
                    recording: PlatformRecording,
                    program: GraphiteCommandProgram,
                    transform: GraphiteTransform,
                    clip: IntRect?,
                ) {
                    insertedRecording = recording
                    insertedProgram = program
                    insertedTransform = transform
                }

                override fun clear(color: Long) = Unit
                override fun save() = Unit
                override fun restore() = Unit
                override fun translate(x: Float, y: Float) = Unit
                override fun rotate(degrees: Float) = Unit
                override fun drawPath(path: GraphitePathData, paint: GraphitePaintData) = Unit
            }
            val renderer = GraphiteEngineRenderer(runtime).also {
                it.bind(attachmentId, density = 1f)
            }

            val transform = GraphiteTransform.translation(3.5f, 7.25f)
            runtime.present(presentation) { insert(recording, transform = transform) }
            renderer.onDrawFrame(context)

            assertTrue(insertedRecording === recording.platformRecording)
            assertTrue(insertedProgram === recording.program)
            assertEquals(transform, insertedTransform)
        } finally {
            runtime.close()
            runtime.awaitClosed()
        }
    }

    @Test
    fun runtimeResourceIdsAreMonotonicWithinOneNamespace() = runTest {
        val first = graphiteDisplayList {}
        val second = graphiteDisplayList {
            drawCircle(Offset(4f, 4f), 2f, Color.White)
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
        val displayList = graphiteDisplayList {
            repeat(1_000) { index ->
                drawCircle(
                    Offset(index.toFloat(), index.toFloat()),
                    1f,
                    Color.White,
                )
            }
        }
        try {
            val jobs = List(32) {
                launch {
                    runtime.recorders.single().record { draw(displayList) }
                }
            }
            jobs.forEach { it.cancelAndJoin() }

            runtime.recorders.single().record { draw(displayList) }
            assertIs<GraphiteEngineState.Ready>(runtime.diagnostics.state.value)
        } finally {
            runtime.close()
            runtime.awaitClosed()
        }
    }

    @Test
    fun runtimeIdentityIsValidatedBeforeFrameInsertion() = runTest {
        val first = GraphiteEngine()
        val second = GraphiteEngine()
        try {
            val attachmentId = requireNotNull(second.attachPresentation {})
            val presentation = requireNotNull(
                second.updatePresentation(attachmentId, IntSize(1, 1), density = 1f),
            )
            val recording = first.recorders.single().record { }
            assertFailsWith<GraphitePresentationException> {
                second.present(presentation) { insert(recording) }
            }
        } finally {
            first.close()
            second.close()
            first.awaitClosed()
            second.awaitClosed()
        }
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
