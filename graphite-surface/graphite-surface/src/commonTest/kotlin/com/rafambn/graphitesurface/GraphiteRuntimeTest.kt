package com.rafambn.graphitesurface

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlinx.coroutines.test.runTest
import com.rafambn.scribe.Archivist

class GraphiteRuntimeTest {
    @Test
    fun recorderBuildsReusableCommandsAsynchronously() = runTest {
        val runtime = GraphiteRuntime.create(GraphiteRuntimeConfig(recorderCount = 2))
        try {
            val target = runtime.createRecordingTarget(GraphiteSize(256, 256))
            val roads = runtime.createDisplayList {
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
            assertEquals(GraphiteColor.White, runtime.takePendingFrame(attachmentId)?.clearColor)
            assertEquals(2, requests)
            assertEquals(0, runtime.metricsSnapshot().pendingFrames)
        } finally {
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
}
