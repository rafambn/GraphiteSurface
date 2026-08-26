package com.rafambn.graphitesurface

import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotEquals

class GraphiteComposeGraphicsTest {
    @Test
    fun roundRectSnapshotsEveryComposeCornerAsAPath() {
        val writer = GraphiteCommandWriter(GraphiteCommandBufferLimit.Default.bytes)
        GraphiteEncoderImpl(writer, cancellationProbe = {}).drawRoundRect(
            roundRect = RoundRect(
                left = 0f,
                top = 0f,
                right = 20f,
                bottom = 30f,
                topLeftCornerRadius = CornerRadius(1f, 2f),
                topRightCornerRadius = CornerRadius(3f, 4f),
                bottomRightCornerRadius = CornerRadius(5f, 6f),
                bottomLeftCornerRadius = CornerRadius(7f, 8f),
            ),
            color = Color.Red,
        )

        val reader = GraphiteCommandReader(writer.finish().commands)
        reader.readInt()
        reader.readInt()
        assertEquals(GraphiteCommandOpcode.DrawPath, GraphiteCommandOpcode.fromCode(reader.readByte()))
    }

    @Test
    fun pathIsSnapshottedWhileTheDslCallRuns() {
        val path = Path().apply {
            fillType = PathFillType.EvenOdd
            moveTo(0f, 0f)
            lineTo(10f, 10f)
            quadraticTo(12f, 4f, 20f, 10f)
            cubicTo(22f, 12f, 24f, 14f, 30f, 20f)
            close()
        }
        val writer = GraphiteCommandWriter(GraphiteCommandBufferLimit.Default.bytes)
        GraphiteEncoderImpl(writer, cancellationProbe = {}).drawPath(path, Color.White)
        path.reset()

        val program = writer.finish()
        GraphiteCommandBuffer.validate(program.commands)
        val reader = GraphiteCommandReader(program.commands)
        reader.readInt()
        reader.readInt()
        assertEquals(GraphiteCommandOpcode.DrawPath, GraphiteCommandOpcode.fromCode(reader.readByte()))
        val payload = GraphiteCommandReader(reader.readBytes(reader.readInt()))
        val snapshot = payload.readPath()
        assertContentEquals(
            byteArrayOf(
                GraphitePathData.VERB_MOVE,
                GraphitePathData.VERB_LINE,
                GraphitePathData.VERB_QUADRATIC,
                GraphitePathData.VERB_CUBIC,
                GraphitePathData.VERB_LINE,
                GraphitePathData.VERB_CLOSE,
            ),
            snapshot.verbs,
        )
        assertEquals(GraphitePathData.FILL_EVEN_ODD, snapshot.fillType)
        assertEquals(16, snapshot.points.size)
        payload.readPaint()
        payload.requireFinished()
    }

    @Test
    fun pathProtocolPreservesConicsAndFillRule() {
        val path = GraphitePathData(
            verbs = byteArrayOf(
                GraphitePathData.VERB_MOVE,
                GraphitePathData.VERB_CONIC,
                GraphitePathData.VERB_CLOSE,
            ),
            points = floatArrayOf(0f, 0f, 10f, 20f, 30f, 0f),
            weights = floatArrayOf(0f, 0.70710677f, 0f),
            fillType = GraphitePathData.FILL_EVEN_ODD,
        )
        val writer = GraphiteCommandWriter(GraphiteCommandBufferLimit.Default.bytes)
        writer.command(GraphiteCommandOpcode.DrawPath) {
            writePath(path)
            writePaint(GraphitePaintData(Color.Red, strokeWidth = null, antiAlias = true))
        }
        val program = writer.finish()
        GraphiteCommandBuffer.validate(program.commands)
        val reader = GraphiteCommandReader(program.commands)
        reader.readInt()
        reader.readInt()
        reader.readByte()
        val payload = GraphiteCommandReader(reader.readBytes(reader.readInt()))
        val decoded = payload.readPath()
        assertContentEquals(path.verbs, decoded.verbs)
        assertContentEquals(path.points, decoded.points)
        assertContentEquals(path.weights, decoded.weights)
        assertEquals(path.fillType, decoded.fillType)
    }

    @Test
    fun colorsUseCanonicalArgbSrgbBytes() {
        val writer = GraphiteCommandWriter(GraphiteCommandBufferLimit.Default.bytes)
        writer.command(GraphiteCommandOpcode.DrawRect) {
            writeRect(Rect(0f, 0f, 1f, 1f))
            writePaint(
                GraphitePaintData(
                    color = Color(1, 2, 3, 4),
                    strokeWidth = null,
                    antiAlias = true,
                )
            )
        }
        val program = writer.finish()
        val reader = GraphiteCommandReader(program.commands)
        reader.readInt()
        reader.readInt()
        reader.readByte()
        val payload = GraphiteCommandReader(reader.readBytes(reader.readInt()))
        payload.readRect()
        val paint = payload.readPaint()
        assertEquals(Color(1, 2, 3, 4), paint.color)
        assertNotEquals(Color.White, paint.color)
        assertEquals(0xFFFFFFFFL, Color.White.toArgbLong())
        assertFailsWith<IllegalArgumentException> { Color.Unspecified.toArgbLong() }
    }

    @Test
    fun strokeProtocolPreservesEveryCapAndJoin() {
        listOf(StrokeCap.Butt, StrokeCap.Round, StrokeCap.Square).forEach { cap ->
            listOf(StrokeJoin.Miter, StrokeJoin.Round, StrokeJoin.Bevel).forEach { join ->
                val writer = GraphiteCommandWriter(GraphiteCommandBufferLimit.Default.bytes)
                writer.command(GraphiteCommandOpcode.DrawRect) {
                    writeRect(Rect(0f, 0f, 1f, 1f))
                    writePaint(
                        GraphitePaintData(
                            color = Color.Red,
                            strokeWidth = 3f,
                            strokeCap = cap,
                            strokeJoin = join,
                            strokeMiter = 7f,
                            antiAlias = true,
                        )
                    )
                }

                val reader = GraphiteCommandReader(writer.finish().commands)
                reader.readInt()
                reader.readInt()
                reader.readByte()
                val payload = GraphiteCommandReader(reader.readBytes(reader.readInt()))
                payload.readRect()
                val paint = payload.readPaint()

                assertEquals(cap, paint.strokeCap)
                assertEquals(join, paint.strokeJoin)
                assertEquals(7f, paint.strokeMiter)
            }
        }
    }

    @Test
    fun offsetsMustBeFinite() {
        val writer = GraphiteCommandWriter(GraphiteCommandBufferLimit.Default.bytes)
        val encoder = GraphiteEncoderImpl(writer, cancellationProbe = {})
        assertFailsWith<IllegalArgumentException> {
            encoder.drawCircle(Offset(Float.NaN, 0f), 1f, Color.White)
        }
        assertFailsWith<IllegalArgumentException> {
            encoder.drawLine(Offset.Zero, Offset(Float.POSITIVE_INFINITY, 0f), Color.White)
        }
    }
}
