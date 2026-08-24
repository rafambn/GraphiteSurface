package com.rafambn.graphitesurface.sample

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rafambn.graphitesurface.GraphiteColor
import com.rafambn.graphitesurface.GraphitePresentResult
import com.rafambn.graphitesurface.GraphitePresentationState
import com.rafambn.graphitesurface.GraphiteRuntime
import com.rafambn.graphitesurface.GraphiteRuntimeConfig
import com.rafambn.graphitesurface.GraphiteTransform
import kotlin.coroutines.cancellation.CancellationException
import kotlin.concurrent.atomics.AtomicBoolean
import kotlin.concurrent.atomics.AtomicLong
import kotlin.concurrent.atomics.AtomicReference
import kotlin.concurrent.atomics.ExperimentalAtomicApi
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

@OptIn(ExperimentalAtomicApi::class)
internal class GraphiteSampleViewModel(
    private val runtimeFactory: suspend () -> GraphiteRuntime = {
        GraphiteRuntime.create(GraphiteRuntimeConfig(recorderCount = 2))
    },
) : ViewModel() {
    private val mutableUiState: MutableStateFlow<GraphiteSampleUiState> =
        MutableStateFlow(GraphiteSampleUiState.Initializing)
    private val cleared: AtomicBoolean = AtomicBoolean(false)
    private val animationStartNanos: AtomicLong = AtomicLong(ANIMATION_NOT_STARTED)
    private val runtime: AtomicReference<GraphiteRuntime?> = AtomicReference(null)
    private val scene: GraphiteSampleScene = GraphiteSampleScene()

    internal val uiState: StateFlow<GraphiteSampleUiState> = mutableUiState.asStateFlow()

    init {
        viewModelScope.launch {
            var createdRuntime: GraphiteRuntime? = null
            try {
                createdRuntime = runtimeFactory()
                coroutineContext.ensureActive()
                if (!cleared.load() && runtime.compareAndSet(null, createdRuntime)) {
                    if (cleared.load()) {
                        runtime.compareAndSet(createdRuntime, null)
                        createdRuntime.close()
                    } else {
                        mutableUiState.value = GraphiteSampleUiState.Ready(createdRuntime)
                    }
                    createdRuntime = null
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Throwable) {
                mutableUiState.value = GraphiteSampleUiState.Failed(error)
            } finally {
                createdRuntime?.close()
            }
        }
    }

    internal suspend fun renderFrame(frameTimeNanos: Long) {
        try {
            renderFrameOrThrow(frameTimeNanos)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Throwable) {
            scene.close()
            mutableUiState.value = GraphiteSampleUiState.Failed(error)
        }
    }

    private suspend fun renderFrameOrThrow(frameTimeNanos: Long) {
        val activeRuntime = runtime.load() ?: return
        val attached = activeRuntime.presentation.value as? GraphitePresentationState.Attached
        if (attached == null) {
            scene.close()
            return
        }
        val activePresentation = attached.info
        val resources = scene.prepare(
            runtime = activeRuntime,
            generation = activePresentation.generation,
            pixelSize = activePresentation.pixelSize,
        )
        animationStartNanos.compareAndSet(ANIMATION_NOT_STARTED, frameTimeNanos)
        val elapsedNanos = (frameTimeNanos - animationStartNanos.load()).coerceAtLeast(0L)

        val recording = activeRuntime.recorders.first().record(resources.target) {
            draw(
                resources.displayList,
                transform = GraphiteTransform.translation(
                    activePresentation.pixelSize.width / 2f,
                    activePresentation.pixelSize.height / 2f,
                ) * GraphiteTransform.rotationDegrees(
                    loopingRotationDegrees(elapsedNanos),
                ),
            )
        }
        try {
            val frame = activeRuntime.createFrame(activePresentation, GraphiteColor.White) {
                insert(recording)
            }
            try {
                val result = activeRuntime.present(frame)
                if (result == GraphitePresentResult.StalePresentation) {
                    scene.close()
                }
            } finally {
                frame.close()
            }
        } finally {
            recording.close()
        }
    }

    public override fun onCleared() {
        cleared.store(true)
        scene.close()
        runtime.exchange(null)?.close()
    }

    private companion object {
        const val ANIMATION_NOT_STARTED: Long = Long.MIN_VALUE
    }
}

internal fun loopingRotationDegrees(elapsedNanos: Long): Float {
    val nanosWithinRotation = elapsedNanos.coerceAtLeast(0L) % ROTATION_PERIOD_NANOS
    return (nanosWithinRotation.toDouble() * FULL_ROTATION_DEGREES / ROTATION_PERIOD_NANOS).toFloat()
}

private const val ROTATION_PERIOD_NANOS: Long = 4_000_000_000L
private const val FULL_ROTATION_DEGREES: Double = 360.0
