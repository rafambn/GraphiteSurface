package com.rafambn.graphitesurface

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotEquals
import kotlin.test.assertSame
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest

class GraphiteDisplayListTest {
    @Test
    fun buildsWithoutALiveRuntimeAndUsesAnIndependentLimit() = runTest {
        val runtime = GraphiteEngine()
        runtime.close()
        runtime.awaitClosed()

        GraphiteDisplayList.build {}

        assertFailsWith<GraphiteEncodingException.CommandBufferTooLarge> {
            GraphiteDisplayList.build(GraphiteCommandBufferLimit(Int.SIZE_BYTES * 2)) {
                drawRect(GraphiteRect(0f, 0f, 1f, 1f), GraphitePaint(GraphiteColor.White))
            }
        }
    }

    @Test
    fun displayListCanBeReusedForNewWork() {
        val displayList = GraphiteDisplayList.build {}
        val first = programDrawing(displayList)
        val second = programDrawing(displayList)

        assertSame(displayList.program, first.resources.single())
        assertSame(displayList.program, second.resources.single())
    }

    @Test
    fun equivalentDisplayListsShareOneResourceReference() {
        val first = GraphiteDisplayList.build {
            drawCircle(GraphitePoint(4f, 4f), 2f, GraphitePaint(GraphiteColor.White))
        }
        val second = GraphiteDisplayList.build {
            drawCircle(GraphitePoint(4f, 4f), 2f, GraphitePaint(GraphiteColor.White))
        }
        val writer = GraphiteCommandWriter(GraphiteCommandBufferLimit.Default.bytes)
        GraphiteEncoderImpl(writer, cancellationProbe = {}).apply {
            draw(first)
            draw(second)
        }

        assertEquals(1, writer.finish().resources.size)
    }

    @Test
    fun hashCollisionDoesNotMakeDifferentProgramsEqual() {
        val first = GraphiteCommandProgram(byteArrayOf(0, 31), emptyList())
        val second = GraphiteCommandProgram(byteArrayOf(1, 0), emptyList())

        assertEquals(first.hashCode(), second.hashCode())
        assertNotEquals(first, second)
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
        assertEquals(smallRoot.commands.size, largeRoot.commands.size)
        assertEquals(1, largeRoot.resources.size)
        assertTrue(large.program.commands.size > 100_000)
        assertTrue(largeRoot.commands.size < 128)
    }

    @Test
    fun nestedListsValidateUpToTheMaximumDepth() {
        var displayList = GraphiteDisplayList.build {}
        repeat(63) {
            val child = displayList
            displayList = GraphiteDisplayList.build { draw(child) }
        }
        displayList.program.validate()

        val tooDeep = GraphiteDisplayList.build { draw(displayList) }
        assertFailsWith<IllegalArgumentException> { tooDeep.program.validate() }
    }

    @Test
    fun malformedResourceIndexFailsValidation() {
        val writer = GraphiteCommandWriter(GraphiteCommandBufferLimit.Default.bytes)
        writer.command(GraphiteCommandOpcode.DrawDisplayList) { writeInt(1) }
        val program = writer.finish()
        assertFailsWith<IllegalStateException> { program.validate() }
    }

    private fun programDrawing(displayList: GraphiteDisplayList): GraphiteCommandProgram {
        val writer = GraphiteCommandWriter(GraphiteCommandBufferLimit.Default.bytes)
        GraphiteEncoderImpl(writer, cancellationProbe = {}).draw(displayList)
        return writer.finish()
    }
}
