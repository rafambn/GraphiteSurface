package com.rafambn.graphitesurface.engine

/** Collects portable draw commands that are executed by the browser render Worker. */
class WebGraphiteDrawContext internal constructor() {
    private val commands = StringBuilder("[")
    private var firstCommand = true
    fun clear(color: Long) = command(0, color)
    fun save() = command(1)
    fun restore() = command(2)
    fun translate(x: Float, y: Float) = command(3, x, y)
    fun rotate(degrees: Float) = command(4, degrees)

    fun concat(columnMajor: FloatArray) {
        require(columnMajor.size == 16)
        commandWithArrays(5, emptyList(), columnMajor.asList())
    }

    fun clipRect(left: Float, top: Float, right: Float, bottom: Float, antiAlias: Boolean) =
        command(6, left, top, right, bottom, antiAlias.asInt())

    fun drawPath(
        verbs: ByteArray,
        points: FloatArray,
        weights: FloatArray,
        fillType: Int,
        color: Long,
        stroke: Boolean,
        strokeWidth: Float,
        strokeCap: Int,
        strokeJoin: Int,
        strokeMiter: Float,
        antiAlias: Boolean,
    ) {
        beginCommand(7)
        commands.append(',')
        appendArray(verbs.map { it.toInt() })
        commands.append(',')
        appendArray(points.asList())
        commands.append(',')
        appendArray(weights.asList())
        commands.append(',').append(fillType)
        commands.append(',').append(color)
        commands.append(',').append(stroke.asInt())
        commands.append(',').append(strokeWidth)
        commands.append(',').append(strokeCap)
        commands.append(',').append(strokeJoin)
        commands.append(',').append(strokeMiter)
        commands.append(',').append(antiAlias.asInt())
        commands.append(']')
    }

    fun drawRect(
        left: Float, top: Float, right: Float, bottom: Float,
        color: Long, stroke: Boolean, strokeWidth: Float, antiAlias: Boolean,
    ) = command(8, left, top, right, bottom, color, stroke.asInt(), strokeWidth, antiAlias.asInt())

    fun drawRoundRect(
        left: Float, top: Float, right: Float, bottom: Float,
        radiusX: Float, radiusY: Float, color: Long, stroke: Boolean,
        strokeWidth: Float, antiAlias: Boolean,
    ) = command(
        9, left, top, right, bottom, radiusX, radiusY,
        color, stroke.asInt(), strokeWidth, antiAlias.asInt(),
    )

    fun drawOval(
        left: Float, top: Float, right: Float, bottom: Float,
        color: Long, stroke: Boolean, strokeWidth: Float, antiAlias: Boolean,
    ) = command(10, left, top, right, bottom, color, stroke.asInt(), strokeWidth, antiAlias.asInt())

    fun drawCircle(
        x: Float, y: Float, radius: Float, color: Long,
        stroke: Boolean, strokeWidth: Float, antiAlias: Boolean,
    ) = command(11, x, y, radius, color, stroke.asInt(), strokeWidth, antiAlias.asInt())

    fun drawLine(
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
