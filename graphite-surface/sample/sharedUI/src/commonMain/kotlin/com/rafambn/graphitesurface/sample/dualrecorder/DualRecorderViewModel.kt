package com.rafambn.graphitesurface.sample.dualrecorder

import androidx.lifecycle.ViewModel
import com.rafambn.graphitesurface.GraphiteColor
import com.rafambn.graphitesurface.GraphiteMetricsSnapshot
import com.rafambn.graphitesurface.GraphitePresentationInfo
import com.rafambn.graphitesurface.GraphiteRecording
import com.rafambn.graphitesurface.GraphiteRenderMode
import com.rafambn.graphitesurface.GraphiteRenderer
import com.rafambn.graphitesurface.GraphiteEngine
import com.rafambn.graphitesurface.GraphiteTransform
import com.rafambn.graphitesurface.sample.loopingRotationDegrees
import kotlin.concurrent.atomics.AtomicBoolean
import kotlin.concurrent.atomics.AtomicLong
import kotlin.concurrent.atomics.AtomicReference
import kotlin.concurrent.atomics.ExperimentalAtomicApi
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

@OptIn(ExperimentalAtomicApi::class)
internal class DualRecorderViewModel : ViewModel() {
    private val mutableError = MutableStateFlow<Throwable?>(null)
    internal val error: StateFlow<Throwable?> = mutableError.asStateFlow()

    private val mutableUiState = MutableStateFlow(DualRecorderUiState())
    internal val uiState: StateFlow<DualRecorderUiState> = mutableUiState.asStateFlow()

    private val recorderEnabled = listOf(AtomicBoolean(true), AtomicBoolean(true))
    private val animationStartNanos = AtomicLong(ANIMATION_NOT_STARTED)
    private val renderedFrames = AtomicLong(0)
    internal val renderer: GraphiteRenderer? = try {
        GraphiteRenderer(
            runtime = GraphiteEngine(
                recorderCount = RECORDER_COUNT,
                recorderQueueCapacity = 4,
            ),
            renderMode = GraphiteRenderMode.Continuous,
            renderFrame = ::renderFrame,
        ).also { renderer -> publishMetrics(renderer.runtime) }
    } catch (error: Throwable) {
        mutableError.value = error
        null
    }

    internal fun toggleRecorder(index: Int) {
        val enabled = recorderEnabled.getOrNull(index) ?: return
        enabled.store(!enabled.load())
        renderer?.runtime?.let(::publishMetrics)
    }

    private suspend fun renderFrame(
        runtime: GraphiteEngine,
        frameTimeNanos: Long,
        presentation: GraphitePresentationInfo,
    ) {
        try {
            val prepared = DualRecorderScene.prepare(
                runtime = runtime,
                pixelSize = presentation.pixelSize,
            )
            animationStartNanos.compareAndSet(ANIMATION_NOT_STARTED, frameTimeNanos)
            val elapsedNanos = (frameTimeNanos - animationStartNanos.load()).coerceAtLeast(0L)
            val rotation = loopingRotationDegrees(elapsedNanos)
            val centerX = presentation.pixelSize.width / 2f
            val centerY = presentation.pixelSize.height / 2f
            val recordings = listOf(
                AtomicReference<GraphiteRecording?>(null),
                AtomicReference<GraphiteRecording?>(null),
            )

            try {
                coroutineScope {
                    if (recorderEnabled[0].load()) {
                        launch {
                            recordings[0].store(
                                runtime.recorders[0].record(prepared.target) {
                                    draw(
                                        displayList = prepared.background,
                                        transform = GraphiteTransform.translation(centerX, centerY) *
                                            GraphiteTransform.rotationDegrees(-rotation * 0.08f) *
                                            GraphiteTransform.translation(-centerX, -centerY),
                                    )
                                },
                            )
                        }
                    }
                    if (recorderEnabled[1].load()) {
                        launch {
                            recordings[1].store(
                                runtime.recorders[1].record(prepared.target) {
                                    draw(
                                        displayList = prepared.foreground,
                                        transform = GraphiteTransform.translation(centerX, centerY) *
                                            GraphiteTransform.rotationDegrees(rotation),
                                    )
                                },
                            )
                        }
                    }
                }

                val frame = runtime.createFrame(
                    presentation = presentation,
                    clearColor = GraphiteColor.rgba(16, 17, 20),
                ) {
                    recordings.forEach { slot -> slot.load()?.let(::insert) }
                }
                try {
                    runtime.present(frame)
                } finally {
                    frame.close()
                }
            } finally {
                recordings.forEach { slot -> slot.exchange(null)?.close() }
            }

            if (renderedFrames.addAndFetch(1) % METRICS_REFRESH_FRAMES == 0L) {
                publishMetrics(runtime)
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Throwable) {
            mutableError.value = error
        }
    }

    private fun publishMetrics(runtime: GraphiteEngine) {
        val snapshot = runtime.metricsSnapshot()
        mutableUiState.value = DualRecorderUiState(
            recorders = snapshot.recorders.map { metrics ->
                metrics.toUiState(enabled = recorderEnabled[metrics.index].load())
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
        const val ANIMATION_NOT_STARTED: Long = Long.MIN_VALUE
    }
}
