package com.rafambn.graphitesurface

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest

class GraphiteDisplayListTest {
    @Test
    fun buildsWithoutALiveRuntimeAndUsesAnIndependentLimit() = runTest {
        val runtime = GraphiteRuntime()
        runtime.close()
        runtime.awaitClosed()

        GraphiteDisplayList.build {}.close()

        assertFailsWith<GraphiteEncodingException.CommandBufferTooLarge> {
            GraphiteDisplayList.build(GraphiteCommandBufferLimit(Int.SIZE_BYTES * 2)) {
                drawRect(GraphiteRect(0f, 0f, 1f, 1f), GraphitePaint(GraphiteColor.White))
            }
        }
    }

    @Test
    fun closedDisplayListCannotBeUsedForNewWork() {
        val displayList = GraphiteDisplayList.build {}
        displayList.close()

        assertFailsWith<GraphiteEncodingException.ClosedResource> {
            GraphiteDisplayList.build { draw(displayList) }
        }
    }

    @Test
    fun recordingStoresAFixedSizeReferenceInsteadOfNestedCommandBytes() {
        val small = GraphiteDisplayList.build {
            drawCircle(GraphitePoint(0f, 0f), 1f, GraphitePaint(GraphiteColor.White))
        }
        val large = GraphiteDisplayList.build {
            repeat(4_000) { index ->
                drawCircle(
                    GraphitePoint(index.toFloat(), index.toFloat()),
                    1f,
                    GraphitePaint(GraphiteColor.White),
                )
            }
        }
        val smallRoot = programDrawing(small)
        val largeRoot = programDrawing(large)
        val largeReference = large.retainProgram()
        try {
            assertEquals(smallRoot.commands.size, largeRoot.commands.size)
            assertEquals(1, largeRoot.resources.size)
            assertTrue(largeReference.value.commands.size > 100_000)
            assertTrue(largeRoot.commands.size < 128)
        } finally {
            largeReference.close()
            smallRoot.close()
            largeRoot.close()
            small.close()
            large.close()
        }
    }

    @Test
    fun nestedListsValidateUpToTheMaximumDepth() {
        var displayList = GraphiteDisplayList.build {}
        repeat(63) {
            val child = displayList
            displayList = GraphiteDisplayList.build { draw(child) }
            child.close()
        }
        val validProgram = displayList.retainProgram()
        validProgram.value.validate()

        val tooDeep = GraphiteDisplayList.build { draw(displayList) }
        val invalidProgram = tooDeep.retainProgram()
        try {
            assertFailsWith<IllegalArgumentException> { invalidProgram.value.validate() }
        } finally {
            validProgram.close()
            invalidProgram.close()
            displayList.close()
            tooDeep.close()
        }
    }

    @Test
    fun malformedResourceIndexFailsValidation() {
        val writer = GraphiteCommandWriter(GraphiteCommandBufferLimit.Default.bytes)
        writer.command(GraphiteCommandOpcode.DrawDisplayList) { writeInt(1) }
        val program = writer.finish()
        try {
            assertFailsWith<IllegalStateException> { program.validate() }
        } finally {
            program.close()
        }
    }

    private fun programDrawing(displayList: GraphiteDisplayList): GraphiteCommandProgram {
        val writer = GraphiteCommandWriter(GraphiteCommandBufferLimit.Default.bytes)
        return try {
            GraphiteEncoderImpl(writer, cancellationProbe = {}).draw(displayList)
            writer.finish()
        } catch (error: Throwable) {
            writer.close()
            throw error
        }
    }
}
