package com.rafambn.graphitesurface.sample.ondemand

import androidx.lifecycle.ViewModel
import com.rafambn.graphitesurface.GraphiteColor
import com.rafambn.graphitesurface.GraphitePresentResult
import com.rafambn.graphitesurface.GraphitePresentationInfo
import com.rafambn.graphitesurface.GraphiteRenderMode
import com.rafambn.graphitesurface.GraphiteRenderer
import com.rafambn.graphitesurface.GraphiteEngine
import com.rafambn.graphitesurface.GraphiteTransform
import com.rafambn.graphitesurface.sample.GraphiteSampleScene
import com.rafambn.graphitesurface.sample.RotationSpeed
import com.rafambn.graphitesurface.sample.loopingRotationDegrees
import kotlin.concurrent.atomics.AtomicLong
import kotlin.concurrent.atomics.ExperimentalAtomicApi
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

@OptIn(ExperimentalAtomicApi::class)
internal class OnDemandRendererViewModel : ViewModel() {
    private val mutableError = MutableStateFlow<Throwable?>(null)
    internal val error: StateFlow<Throwable?> = mutableError.asStateFlow()

    private val animationStartNanos = AtomicLong(ANIMATION_NOT_STARTED)
    private val rotationSpeed = RotationSpeed()
    private val scene = GraphiteSampleScene()

    internal val renderer: GraphiteRenderer? = try {
        GraphiteRenderer(
            runtime = GraphiteEngine(recorderCount = 2),
            renderMode = GraphiteRenderMode.OnDemand,
            renderFrame = ::renderFrame,
        )
    } catch (error: Throwable) {
        mutableError.value = error
        null
    }

    internal fun setRotationSpeed(speed: Float) {
        rotationSpeed.update(speed)
        renderer?.requestRender()
    }

    private suspend fun renderFrame(
        runtime: GraphiteEngine,
        frameTimeNanos: Long,
        presentation: GraphitePresentationInfo,
    ) {
        try {
            renderFrameOrThrow(runtime, frameTimeNanos, presentation)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Throwable) {
            scene.close()
            mutableError.value = error
        }
    }

    private suspend fun renderFrameOrThrow(
        runtime: GraphiteEngine,
        frameTimeNanos: Long,
        presentation: GraphitePresentationInfo,
    ) {
        val prepared = scene.prepare(
            runtime = runtime,
            pixelSize = presentation.pixelSize,
        )
        animationStartNanos.compareAndSet(ANIMATION_NOT_STARTED, frameTimeNanos)
        val elapsedNanos = (frameTimeNanos - animationStartNanos.load()).coerceAtLeast(0L)
        val recording = runtime.recorders.first().record(prepared.target) {
            draw(
                prepared.displayList,
                transform = GraphiteTransform.translation(
                    presentation.pixelSize.width / 2f,
                    presentation.pixelSize.height / 2f,
                ) * GraphiteTransform.rotationDegrees(
                    loopingRotationDegrees(elapsedNanos, rotationSpeed.read()),
                ),
            )
        }
        try {
            val frame = runtime.createFrame(presentation, GraphiteColor.White) {
                insert(recording)
            }
            try {
                if (runtime.present(frame) == GraphitePresentResult.StalePresentation) {
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
        scene.close()
        renderer?.runtime?.close()
    }

    private companion object {
        const val ANIMATION_NOT_STARTED: Long = Long.MIN_VALUE
    }
}
