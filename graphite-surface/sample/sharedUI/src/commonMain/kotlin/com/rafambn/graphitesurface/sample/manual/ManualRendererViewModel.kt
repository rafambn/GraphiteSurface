package com.rafambn.graphitesurface.sample.manual

import androidx.lifecycle.ViewModel
import com.rafambn.graphitesurface.GraphiteColor
import com.rafambn.graphitesurface.GraphitePresentationInfo
import com.rafambn.graphitesurface.GraphiteRenderMode
import com.rafambn.graphitesurface.GraphiteRenderer
import com.rafambn.graphitesurface.GraphiteEngine
import com.rafambn.graphitesurface.GraphiteTransform
import com.rafambn.graphitesurface.sample.RotationSpeed
import com.rafambn.graphitesurface.sample.loopingRotationDegrees
import com.rafambn.graphitesurface.sample.prepareGraphiteSampleScene
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

internal class ManualRendererViewModel : ViewModel() {
    private val mutableError = MutableStateFlow<Throwable?>(null)
    internal val error: StateFlow<Throwable?> = mutableError.asStateFlow()

    private val rotationSpeed = RotationSpeed()
    internal val renderer: GraphiteRenderer? = try {
        GraphiteRenderer(
            runtime = GraphiteEngine(recorderCount = 2),
            renderMode = GraphiteRenderMode.Manual,
            renderFrame = { frameTimeNanos, presentation ->
                renderFrame(frameTimeNanos, presentation)
            },
        )
    } catch (error: Throwable) {
        mutableError.value = error
        null
    }

    internal fun setRotationSpeed(speed: Float) {
        rotationSpeed.update(speed)
        renderer?.requestRender()
    }

    private suspend fun GraphiteEngine.renderFrame(
        frameTimeNanos: Long,
        presentation: GraphitePresentationInfo,
    ) {
        try {
            val displayList = prepareGraphiteSampleScene(presentation.pixelSize)
            val recording = recorders.first().record {
                draw(
                    displayList,
                    transform = GraphiteTransform.translation(
                        presentation.pixelSize.width / 2f,
                        presentation.pixelSize.height / 2f,
                    ) * GraphiteTransform.rotationDegrees(
                        loopingRotationDegrees(frameTimeNanos, rotationSpeed.read()),
                    ),
                )
            }
            val frame = createFrame(presentation, GraphiteColor.White) {
                insert(recording)
            }
            try {
                present(frame)
            } finally {
                frame.close()
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Throwable) {
            mutableError.value = error
        }
    }

    public override fun onCleared() {
        renderer?.runtime?.close()
    }
}
