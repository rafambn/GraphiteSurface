package com.rafambn.graphitesurface.engine

/** Collects portable draw commands that are executed by the browser render Worker. */
public class WebGraphiteDrawContext internal constructor() {
    private val commands: StringBuilder = StringBuilder("[")
    private var firstCommand: Boolean = true
    private var pathVerbs: MutableList<Int> = mutableListOf()
    private var pathPoints: MutableList<Float> = mutableListOf()

    public fun clear(color: Long) = command(0, color)
    public fun save() = command(1)
    public fun restore() = command(2)
    public fun translate(x: Float, y: Float) = command(3, x, y)
    public fun rotate(degrees: Float) = command(4, degrees)

    public fun concat(columnMajor: FloatArray) {
        require(columnMajor.size == 16)
        commandWithArrays(5, emptyList(), columnMajor.asList())
    }

    public fun clipRect(left: Float, top: Float, right: Float, bottom: Float, antiAlias: Boolean) =
        command(6, left, top, right, bottom, antiAlias.asInt())

    public fun beginPath() {
        pathVerbs = mutableListOf()
        pathPoints = mutableListOf()
    }

    public fun moveTo(x: Float, y: Float) {
        pathVerbs += 1
        pathPoints += x
        pathPoints += y
    }

    public fun lineTo(x: Float, y: Float) {
        pathVerbs += 2
        pathPoints += x
        pathPoints += y
    }

    public fun closePath() {
        pathVerbs += 3
    }

    public fun drawPath(color: Long, antiAlias: Boolean) {
        drawPath(
            verbs = pathVerbs.map(Int::toByte).toByteArray(),
            points = pathPoints.toFloatArray(),
            color = color,
            stroke = false,
            strokeWidth = 1f,
            antiAlias = antiAlias,
        )
    }

    public fun drawPath(
        verbs: ByteArray,
        points: FloatArray,
        color: Long,
        stroke: Boolean,
        strokeWidth: Float,
        antiAlias: Boolean,
    ) = commandWithArrays(
        opcode = 7,
        integers = verbs.map { it.toInt() },
        floats = points.asList(),
        color,
        stroke.asInt(),
        strokeWidth,
        antiAlias.asInt(),
    )

    public fun drawRect(
        left: Float, top: Float, right: Float, bottom: Float,
        color: Long, stroke: Boolean, strokeWidth: Float, antiAlias: Boolean,
    ) = command(8, left, top, right, bottom, color, stroke.asInt(), strokeWidth, antiAlias.asInt())

    public fun drawRoundRect(
        left: Float, top: Float, right: Float, bottom: Float,
        radiusX: Float, radiusY: Float, color: Long, stroke: Boolean,
        strokeWidth: Float, antiAlias: Boolean,
    ) = command(
        9, left, top, right, bottom, radiusX, radiusY,
        color, stroke.asInt(), strokeWidth, antiAlias.asInt(),
    )

    public fun drawOval(
        left: Float, top: Float, right: Float, bottom: Float,
        color: Long, stroke: Boolean, strokeWidth: Float, antiAlias: Boolean,
    ) = command(10, left, top, right, bottom, color, stroke.asInt(), strokeWidth, antiAlias.asInt())

    public fun drawCircle(
        x: Float, y: Float, radius: Float, color: Long,
        stroke: Boolean, strokeWidth: Float, antiAlias: Boolean,
    ) = command(11, x, y, radius, color, stroke.asInt(), strokeWidth, antiAlias.asInt())

    public fun drawLine(
        x0: Float, y0: Float, x1: Float, y1: Float,
        color: Long, strokeWidth: Float, antiAlias: Boolean,
    ) = command(12, x0, y0, x1, y1, color, strokeWidth, antiAlias.asInt())

    internal fun finish(): String = commands.append(']').toString()

    private fun command(opcode: Int, vararg values: Any) {
        beginCommand(opcode)
        values.forEach { value -> commands.append(',').append(value) }
        commands.append(']')
    }

    private fun commandWithArrays(
        opcode: Int,
        integers: List<Int>,
        floats: List<Float>,
        vararg values: Any,
    ) {
        beginCommand(opcode)
        commands.append(',')
        appendArray(integers)
        commands.append(',')
        appendArray(floats)
        values.forEach { value -> commands.append(',').append(value) }
        commands.append(']')
    }

    private fun beginCommand(opcode: Int) {
        if (firstCommand) firstCommand = false else commands.append(',')
        commands.append('[').append(opcode)
    }

    private fun appendArray(values: Collection<Number>) {
        commands.append('[')
        values.forEachIndexed { index, value ->
            if (index != 0) commands.append(',')
            commands.append(value)
        }
        commands.append(']')
    }
}

private fun Boolean.asInt(): Int = if (this) 1 else 0
