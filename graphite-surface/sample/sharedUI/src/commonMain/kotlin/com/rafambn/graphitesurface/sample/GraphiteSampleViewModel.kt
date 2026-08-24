package com.rafambn.graphitesurface.sample

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rafambn.graphitesurface.GraphiteColor
import com.rafambn.graphitesurface.GraphitePresentResult
import com.rafambn.graphitesurface.GraphitePresentationState
import com.rafambn.graphitesurface.GraphiteRuntime
import com.rafambn.graphitesurface.GraphiteRuntimeConfig
import com.rafambn.graphitesurface.GraphiteRuntimeUnavailableException
import com.rafambn.graphitesurface.GraphiteTransform
import kotlin.coroutines.cancellation.CancellationException
import kotlin.concurrent.atomics.AtomicBoolean
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

        try {
            val recording = activeRuntime.recorders.first().record(resources.target) {
                draw(
                    resources.displayList,
                    transform = GraphiteTransform.translation(
                        activePresentation.pixelSize.width / 2f,
                        activePresentation.pixelSize.height / 2f,
                    ) * GraphiteTransform.rotationDegrees(
                        (frameTimeNanos / NANOS_PER_SECOND * DEGREES_PER_SECOND).toFloat(),
                    ),
                )
            }
            try {
                val frame = activeRuntime.createFrame(activePresentation, GraphiteColor.White) {
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
        } catch (error: GraphiteRuntimeUnavailableException) {
            scene.close()
            mutableUiState.value = GraphiteSampleUiState.Failed(error)
        }
    }

    public override fun onCleared() {
        cleared.store(true)
        scene.close()
        runtime.exchange(null)?.close()
    }

    private companion object {
        const val NANOS_PER_SECOND: Double = 1_000_000_000.0
        const val DEGREES_PER_SECOND: Double = 90.0
    }
}
