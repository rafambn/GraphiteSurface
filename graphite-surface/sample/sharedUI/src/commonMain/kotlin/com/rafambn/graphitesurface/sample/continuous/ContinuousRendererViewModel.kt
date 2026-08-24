package com.rafambn.graphitesurface.sample.continuous

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rafambn.graphitesurface.GraphiteColor
import com.rafambn.graphitesurface.GraphitePresentResult
import com.rafambn.graphitesurface.GraphitePresentationInfo
import com.rafambn.graphitesurface.GraphiteRenderMode
import com.rafambn.graphitesurface.GraphiteRenderer
import com.rafambn.graphitesurface.GraphiteRuntime
import com.rafambn.graphitesurface.GraphiteRuntimeConfig
import com.rafambn.graphitesurface.GraphiteTransform
import com.rafambn.graphitesurface.sample.GraphiteSampleScene
import com.rafambn.graphitesurface.sample.components.RendererScreenState
import com.rafambn.graphitesurface.sample.loopingRotationDegrees
import kotlin.concurrent.atomics.AtomicBoolean
import kotlin.concurrent.atomics.AtomicLong
import kotlin.concurrent.atomics.AtomicReference
import kotlin.concurrent.atomics.ExperimentalAtomicApi
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

@OptIn(ExperimentalAtomicApi::class)
internal class ContinuousRendererViewModel(
    private val runtimeFactory: suspend () -> GraphiteRuntime = {
        GraphiteRuntime.create(GraphiteRuntimeConfig(recorderCount = 2))
    },
) : ViewModel() {
    private val mutableUiState = MutableStateFlow<RendererScreenState>(
        RendererScreenState.Initializing,
    )
    internal val uiState: StateFlow<RendererScreenState> = mutableUiState.asStateFlow()

    private val cleared = AtomicBoolean(false)
    private val animationStartNanos = AtomicLong(ANIMATION_NOT_STARTED)
    private val runtime = AtomicReference<GraphiteRuntime?>(null)
    private val scene = GraphiteSampleScene()

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
                        mutableUiState.value = RendererScreenState.Ready(
                            GraphiteRenderer(
                                runtime = createdRuntime,
                                renderMode = GraphiteRenderMode.Continuous,
                                renderFrame = ::renderFrame,
                            ),
                        )
                    }
                    createdRuntime = null
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Throwable) {
                mutableUiState.value = RendererScreenState.Failed(error)
            } finally {
                createdRuntime?.close()
            }
        }
    }

    private suspend fun renderFrame(
        frameTimeNanos: Long,
        presentation: GraphitePresentationInfo,
    ) {
        try {
            renderFrameOrThrow(frameTimeNanos, presentation)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Throwable) {
            scene.close()
            mutableUiState.value = RendererScreenState.Failed(error)
        }
    }

    private suspend fun renderFrameOrThrow(
        frameTimeNanos: Long,
        presentation: GraphitePresentationInfo,
    ) {
        val activeRuntime = runtime.load() ?: return
        val resources = scene.prepare(
            runtime = activeRuntime,
            generation = presentation.generation,
            pixelSize = presentation.pixelSize,
        )
        animationStartNanos.compareAndSet(ANIMATION_NOT_STARTED, frameTimeNanos)
        val elapsedNanos = (frameTimeNanos - animationStartNanos.load()).coerceAtLeast(0L)
        val recording = activeRuntime.recorders.first().record(resources.target) {
            draw(
                resources.displayList,
                transform = GraphiteTransform.translation(
                    presentation.pixelSize.width / 2f,
                    presentation.pixelSize.height / 2f,
                ) * GraphiteTransform.rotationDegrees(loopingRotationDegrees(elapsedNanos)),
            )
        }
        try {
            val frame = activeRuntime.createFrame(presentation, GraphiteColor.White) {
                insert(recording)
            }
            try {
                if (activeRuntime.present(frame) == GraphitePresentResult.StalePresentation) {
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
