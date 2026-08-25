package com.rafambn.graphitesurface

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest

class GraphiteOptimizationBenchmarkTest {
    @Test
    fun reportsRetainedCommandCosts() = runTest {
        val path = Path().apply {
            moveTo(0f, 0f)
            lineTo(50f, 100f)
            lineTo(100f, 0f)
            close()
        }
        val paint = GraphitePaint(Color.White)
        val displayList = GraphiteDisplayList.build {
            repeat(4_000) { drawPath(path, paint) }
        }
        val nested = GraphiteDisplayList.build { draw(displayList) }

        repeat(WARM_UP_ITERATIONS) {
            encode { drawPath(path, paint) }
            encode { draw(displayList) }
        }

        val directNanos = averageNanos {
            encode { drawPath(path, paint) }
        }
        val retainedOnceNanos = averageNanos {
            encode { draw(displayList) }
        }
        val retainedHundredNanos = averageNanos {
            encode { repeat(100) { draw(displayList) } }
        }
        val nestedNanos = averageNanos {
            encode { draw(nested) }
        }

        val root = encode { draw(displayList) }
        val repeatedRoot = encode { repeat(100) { draw(displayList) } }
        val runtime = GraphiteEngine()
        try {
            val firstStarted = System.nanoTime()
            runtime.recorders.single().record { draw(displayList) }
            val firstUseNanos = System.nanoTime() - firstStarted
            val firstMetrics = runtime.metricsSnapshot().resources

            val cachedStarted = System.nanoTime()
            runtime.recorders.single().record { draw(displayList) }
            val cachedUseNanos = System.nanoTime() - cachedStarted
            val cachedMetrics = runtime.metricsSnapshot().resources

            assertEquals(96, root.commands.size)
            assertEquals(8_808, repeatedRoot.commands.size)
            assertEquals(1, repeatedRoot.resources.size)
            assertEquals(1, firstMetrics.publications)
            assertEquals(1, cachedMetrics.publications)
            assertEquals(1, cachedMetrics.cacheHits)
            assertTrue(cachedMetrics.workerMessageBytes > firstMetrics.workerMessageBytes)

            println(
                "GRAPHITE_BENCHMARK " +
                    "display_list_bytes=${displayList.program.commands.size} " +
                    "root_once_bytes=${root.commands.size} " +
                    "root_hundred_bytes=${repeatedRoot.commands.size} " +
                    "first_worker_message_bytes=${firstMetrics.workerMessageBytes} " +
                    "cached_worker_message_bytes=" +
                    "${cachedMetrics.workerMessageBytes - firstMetrics.workerMessageBytes} " +
                    "direct_average_nanos=$directNanos " +
                    "retained_once_average_nanos=$retainedOnceNanos " +
                    "retained_hundred_average_nanos=$retainedHundredNanos " +
                    "nested_average_nanos=$nestedNanos " +
                    "first_use_nanos=$firstUseNanos " +
                    "cached_use_nanos=$cachedUseNanos " +
                    "validation_nanos=${cachedMetrics.totalValidationNanos} " +
                    "preparation_nanos=${cachedMetrics.totalPreparationNanos}",
            )
        } finally {
            runtime.close()
            runtime.awaitClosed()
        }
    }

    private fun encode(block: GraphiteEncoder.() -> Unit): GraphiteCommandProgram {
        val writer = GraphiteCommandWriter(GraphiteCommandBufferLimit.Default.bytes)
        GraphiteEncoderImpl(writer, cancellationProbe = {}).block()
        return writer.finish()
    }

    private inline fun averageNanos(block: () -> Unit): Long {
        val started = System.nanoTime()
        repeat(MEASURE_ITERATIONS) { block() }
        return (System.nanoTime() - started) / MEASURE_ITERATIONS
    }

    private companion object {
        const val WARM_UP_ITERATIONS: Int = 50
        const val MEASURE_ITERATIONS: Int = 500
    }
}
