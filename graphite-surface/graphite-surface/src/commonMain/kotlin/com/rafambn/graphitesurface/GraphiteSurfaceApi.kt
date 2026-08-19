package com.rafambn.graphitesurface

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.IntSize

/** Experimental public API for the Graphite-backed Compose surface. */
@RequiresOptIn("GraphiteSurface is experimental and its API may change.")
public annotation class ExperimentalGraphiteSurfaceApi

/**
 * Draws the scene for a [GraphiteSurface], mirroring `GLSurfaceView.Renderer`.
 *
 * Callbacks run on the rendering context of the surface, never on a Compose
 * recomposition. The surface owns the frame lifecycle: it clears nothing on
 * your behalf, so the renderer decides the background.
 */
public interface GraphiteRenderer {
    /** Called once before the first frame is drawn. Create resources here. */
    public fun onSurfaceCreated()

    /** Called whenever the surface changes size, before the next frame. */
    public fun onSurfaceChanged(size: IntSize)

    /**
     * Called to draw a frame. Every drawing call is recorded and submitted
     * to the Graphite context after this returns.
     */
    public fun onDrawFrame(context: GraphiteDrawContext)
}

/**
 * Drawing surface for a frame, backed by Skia Graphite on iOS. Only path
 * drawing is exposed for now.
 */
public interface GraphiteDrawContext {
    /** Fills the whole surface with [color] (0xAARRGGBB). */
    public fun clear(color: Long)

    /** Pushes a copy of the current canvas state. */
    public fun save()

    /** Pops the last [save]. */
    public fun restore()

    /** Translates the canvas by [x], [y]. */
    public fun translate(x: Float, y: Float)

    /** Rotates the canvas by [degrees] around the current origin. */
    public fun rotate(degrees: Float)

    /** Starts a new path. */
    public fun beginPath()

    /** Moves the current path to [x], [y] without drawing. */
    public fun moveTo(x: Float, y: Float)

    /** Draws a straight line from the current point to [x], [y]. */
    public fun lineTo(x: Float, y: Float)

    /** Closes the current path with a line back to its start. */
    public fun closePath()

    /** Strokes and fills the current path with [color] (0xAARRGGBB). */
    public fun drawPath(color: Long, antiAlias: Boolean)
}

public enum class GraphiteRenderMode {
    /** The renderer is called continuously, like `RENDERMODE_CONTINUOUSLY`. */
    Continuously,

    /** Frames are only drawn when [GraphiteSurfaceController.requestRender] is called. */
    WhenDirty,
}

/**
 * Controls a [GraphiteSurface] from outside its composition. Wire it up the
 * same way you would any Compose view state:
 *
 * ```kotlin
 * val controller = remember { GraphiteSurfaceController() }
 * GraphiteSurface(renderer = renderer, controller = controller)
 * ```
 */
public class GraphiteSurfaceController {
    private var requestRenderHandler: (() -> Unit)? = null

    internal fun setRequestRenderHandler(handler: (() -> Unit)?) {
        requestRenderHandler = handler
    }

    /** Requests a single frame when the surface is in [GraphiteRenderMode.WhenDirty]. */
    public fun requestRender() {
        requestRenderHandler?.invoke()
    }
}

/**
 * Hosts a Graphite rendering surface inside Compose. The engine manages the
 * native render loop; the [renderer] decides what appears on screen.
 */
@Composable
@ExperimentalGraphiteSurfaceApi
public expect fun GraphiteSurface(
    renderer: GraphiteRenderer,
    modifier: Modifier = Modifier,
    renderMode: GraphiteRenderMode = GraphiteRenderMode.Continuously,
    controller: GraphiteSurfaceController? = null,
)
