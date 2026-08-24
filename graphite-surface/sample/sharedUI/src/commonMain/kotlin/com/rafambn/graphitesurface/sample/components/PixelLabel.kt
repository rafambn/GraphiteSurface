package com.rafambn.graphitesurface.sample.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

@Composable
internal fun PixelLabel(
    text: String,
    color: Color,
    pixelSize: Dp,
    modifier: Modifier = Modifier,
) {
    val normalizedText = text.uppercase()
    val widthInPixels = (normalizedText.length * 6 - 1).coerceAtLeast(0)
    Canvas(
        modifier = modifier
            .width(pixelSize * widthInPixels)
            .height(pixelSize * 7),
    ) {
        val pixel = size.height / 7f
        normalizedText.forEachIndexed { characterIndex, character ->
            val glyph = pixelGlyphs[character] ?: return@forEachIndexed
            glyph.forEachIndexed { row, line ->
                line.forEachIndexed { column, value ->
                    if (value == '1') {
                        drawRect(
                            color = color,
                            topLeft = Offset(
                                x = (characterIndex * 6 + column) * pixel,
                                y = row * pixel,
                            ),
                            size = Size(pixel, pixel),
                        )
                    }
                }
            }
        }
    }
}

private val pixelGlyphs: Map<Char, List<String>> = mapOf(
    'A' to listOf("01110", "10001", "10001", "11111", "10001", "10001", "10001"),
    'C' to listOf("01111", "10000", "10000", "10000", "10000", "10000", "01111"),
    'D' to listOf("11110", "10001", "10001", "10001", "10001", "10001", "11110"),
    'E' to listOf("11111", "10000", "10000", "11110", "10000", "10000", "11111"),
    'G' to listOf("01111", "10000", "10000", "10111", "10001", "10001", "01111"),
    'I' to listOf("11111", "00100", "00100", "00100", "00100", "00100", "11111"),
    'L' to listOf("10000", "10000", "10000", "10000", "10000", "10000", "11111"),
    'M' to listOf("10001", "11011", "10101", "10101", "10001", "10001", "10001"),
    'N' to listOf("10001", "11001", "10101", "10011", "10001", "10001", "10001"),
    'O' to listOf("01110", "10001", "10001", "10001", "10001", "10001", "01110"),
    'R' to listOf("11110", "10001", "10001", "11110", "10100", "10010", "10001"),
    'S' to listOf("01111", "10000", "10000", "01110", "00001", "00001", "11110"),
    'T' to listOf("11111", "00100", "00100", "00100", "00100", "00100", "00100"),
    'U' to listOf("10001", "10001", "10001", "10001", "10001", "10001", "01110"),
)
