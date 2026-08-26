package com.rafambn.graphitesurface.sample.dualrecorder

import androidx.compose.ui.graphics.Color
import androidx.lifecycle.ViewModel
import com.rafambn.graphitesurface.GraphiteEngine
import com.rafambn.graphitesurface.GraphiteMetricsSnapshot
import com.rafambn.graphitesurface.GraphitePresentationInfo
import com.rafambn.graphitesurface.GraphiteRenderMode
import com.rafambn.graphitesurface.GraphiteRenderer
import com.rafambn.graphitesurface.GraphiteTransform
import com.rafambn.graphitesurface.sample.loopingRotationDegrees
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

internal class DualRecorderViewModel : ViewModel() {
    private val mutableError = MutableStateFlow<Throwable?>(null)
    internal val error: StateFlow<Throwable?> = mutableError.asStateFlow()

    private val mutableUiState = MutableStateFlow(DualRecorderUiState())
    internal val uiState: StateFlow<DualRecorderUiState> = mutableUiState.asStateFlow()

    private var renderedFrames = 0L
    internal val renderer: GraphiteRenderer? = try {
        GraphiteRenderer(
            runtime = GraphiteEngine(
                recorderCount = RECORDER_COUNT,
                recorderQueueCapacity = 4,
            ),
            renderMode = GraphiteRenderMode.Continuous,
            renderFrame = { frameTimeNanos, presentation ->
                renderFrame(frameTimeNanos, presentation)
            },
        ).also { renderer -> publishMetrics(renderer.runtime) }
    } catch (error: Throwable) {
        mutableError.value = error
        null
    }

    internal fun toggleRecorder(index: Int) {
        val current = mutableUiState.value
        val recorder = current.recorders[index]
        mutableUiState.value = current.copy(
            recorders = current.recorders.toMutableList().apply {
                this[index] = recorder.copy(enabled = !recorder.enabled)
            },
        )
        renderer?.runtime?.let(::publishMetrics)
    }

    private suspend fun GraphiteEngine.renderFrame(
        frameTimeNanos: Long,
        presentation: GraphitePresentationInfo,
    ) {
        try {
            val prepared = DualRecorderScene.prepare(
                pixelSize = presentation.pixelSize,
            )
            val rotation = loopingRotationDegrees(frameTimeNanos)
            val centerX = presentation.pixelSize.width / 2f
            val centerY = presentation.pixelSize.height / 2f
            val enabled = uiState.value.recorders.map { it.enabled }
            val (background, foreground) = coroutineScope {
                val background = if (enabled[0]) {
                    async {
                        recorders[0].record {
                            withTransform(
                                GraphiteTransform.translation(centerX, centerY) *
                                    GraphiteTransform.rotationDegrees(-rotation * 0.08f) *
                                    GraphiteTransform.translation(-centerX, -centerY),
                            ) { draw(prepared.background) }
                        }
                    }
                } else {
                    null
                }
                val foreground = if (enabled[1]) {
                    async {
                        recorders[1].record {
                            withTransform(
                                GraphiteTransform.translation(centerX, centerY) *
                                    GraphiteTransform.rotationDegrees(rotation),
                            ) { draw(prepared.foreground) }
                        }
                    }
                } else {
                    null
                }
                background?.await() to foreground?.await()
            }

            present(
                presentation = presentation,
                clearColor = Color(16, 17, 20),
            ) {
                background?.let(::insert)
                foreground?.let(::insert)
            }

            renderedFrames++
            if (renderedFrames % METRICS_REFRESH_FRAMES == 0L) {
                publishMetrics(this)
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Throwable) {
            mutableError.value = error
        }
    }

    private fun publishMetrics(runtime: GraphiteEngine) {
        val snapshot = runtime.diagnostics.snapshot()
        val enabled = uiState.value.recorders.map { it.enabled }
        mutableUiState.value = DualRecorderUiState(
            recorders = snapshot.recorders.map { metrics ->
                metrics.toUiState(enabled = enabled[metrics.index])
            },
        )
    }

    public override fun onCleared() {
        renderer?.runtime?.close()
    }

    private fun GraphiteMetricsSnapshot.Recorder.toUiState(
        enabled: Boolean,
    ): DualRecorderUiState.Recorder = DualRecorderUiState.Recorder(
        index = index,
        enabled = enabled,
        queueDepth = queueDepth,
        queueCapacity = queueCapacity,
        completed = completed,
        averageRecordingNanos = (completed + cancelled + failed).let { attempts ->
            if (attempts == 0L) 0L else totalRecordingNanos / attempts
        },
        maximumRecordingNanos = maximumRecordingNanos,
    )

    private companion object {
        const val RECORDER_COUNT: Int = 2
        const val METRICS_REFRESH_FRAMES: Long = 12
    }
}
