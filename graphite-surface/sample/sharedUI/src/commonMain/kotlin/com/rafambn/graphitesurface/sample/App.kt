@file:OptIn(com.rafambn.graphitesurface.ExperimentalGraphiteSurfaceApi::class)

package com.rafambn.graphitesurface.sample

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import com.rafambn.graphitesurface.GraphiteColor
import com.rafambn.graphitesurface.GraphitePaint
import com.rafambn.graphitesurface.GraphitePath
import com.rafambn.graphitesurface.GraphitePresentResult
import com.rafambn.graphitesurface.GraphitePresentationState
import com.rafambn.graphitesurface.GraphiteRuntime
import com.rafambn.graphitesurface.GraphiteRuntimeConfig
import com.rafambn.graphitesurface.GraphiteRuntimeUnavailableException
import com.rafambn.graphitesurface.GraphiteSurface
import com.rafambn.graphitesurface.GraphiteTransform
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.first
import kotlin.math.min

@Composable
fun App() {
    var runtime by remember { mutableStateOf<GraphiteRuntime?>(null) }

    LaunchedEffect(Unit) {
        runtime = GraphiteRuntime.create(GraphiteRuntimeConfig(recorderCount = 2))
    }
    DisposableEffect(Unit) {
        onDispose { runtime?.close() }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        runtime?.let { currentRuntime ->
            GraphiteSurface(
                runtime = currentRuntime,
                modifier = Modifier.fillMaxSize(),
            )
            AnimatedTriangle(currentRuntime)
        }
    }
}

@Composable
private fun AnimatedTriangle(runtime: GraphiteRuntime) {
    LaunchedEffect(runtime) {
        try {
            while (true) {
                val presentation = runtime.presentation
                    .filterIsInstance<GraphitePresentationState.Attached>()
                    .first()
                    .info
                val extent = min(presentation.pixelSize.width, presentation.pixelSize.height).toFloat()
                val halfWidth = extent * 0.35f
                val triangle = runtime.createDisplayList {
                    drawPath(
                        GraphitePath.build {
                            moveTo(0f, -halfWidth * 4f / 3f)
                            lineTo(halfWidth, halfWidth * 2f / 3f)
                            lineTo(-halfWidth, halfWidth * 2f / 3f)
                            close()
                        },
                        GraphitePaint(GraphiteColor.Red),
                    )
                }
                try {
                    while (runtime.presentation.value == GraphitePresentationState.Attached(presentation)) {
                        val frameTime = withFrameNanos { it }
                        val target = runtime.createRecordingTarget(presentation.pixelSize)
                        val recording = runtime.recorders.first().record(target) {
                            withTransform(
                                GraphiteTransform.translation(
                                    presentation.pixelSize.width / 2f,
                                    presentation.pixelSize.height / 2f,
                                ) * GraphiteTransform.rotationDegrees(
                                    (frameTime / 1_000_000_000.0 * 90.0).toFloat(),
                                ),
                            ) {
                                draw(triangle)
                            }
                        }
                        val frame = runtime.createFrame(presentation, GraphiteColor.White) {
                            insert(recording)
                        }
                        recording.close()
                        val result = runtime.present(frame)
                        frame.close()
                        if (result == GraphitePresentResult.StalePresentation) break
                    }
                } finally {
                    triangle.close()
                }
            }
        } catch (_: GraphiteRuntimeUnavailableException) {
            // A fatal worker failure is already represented by runtime.state and runtime.events.
        }
    }
}
