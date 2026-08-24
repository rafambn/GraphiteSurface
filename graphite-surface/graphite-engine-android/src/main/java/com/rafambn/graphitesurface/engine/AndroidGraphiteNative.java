package com.rafambn.graphitesurface.engine;

import android.view.Surface;

/** JNI boundary for the Android Vulkan/Skia Graphite engine. */
public final class AndroidGraphiteNative {
    static {
        System.loadLibrary("graphite-engine-android");
    }

    private AndroidGraphiteNative() {
    }

    public static native long create(boolean useHardwareBuffer);

    public static native boolean setSurface(long handle, Surface surface, int width, int height);

    public static native boolean beginFrame(long handle);

    public static native void setFrameTimeNanos(long handle, long frameTimeNanos);

    public static native boolean endFrame(long handle);

    public static native void dispose(long handle);

    public static native void clear(long handle, int color);

    public static native void save(long handle);

    public static native void restore(long handle);

    public static native void translate(long handle, float x, float y);

    public static native void rotate(long handle, float degrees);

    public static native void concat(long handle, float[] columnMajor);

    public static native void clipRect(
            long handle,
            float left,
            float top,
            float right,
            float bottom,
            boolean antiAlias);

    public static native void beginPath(long handle);

    public static native void moveTo(long handle, float x, float y);

    public static native void lineTo(long handle, float x, float y);

    public static native void closePath(long handle);

    public static native void drawPath(long handle, int color, boolean antiAlias);

    public static native void drawImmutablePath(
            long handle,
            byte[] verbs,
            float[] points,
            int color,
            boolean stroke,
            float strokeWidth,
            boolean antiAlias);

    public static native void drawRect(
            long handle,
            float left,
            float top,
            float right,
            float bottom,
            int color,
            boolean stroke,
            float strokeWidth,
            boolean antiAlias);

    public static native void drawRoundRect(
            long handle,
            float left,
            float top,
            float right,
            float bottom,
            float radiusX,
            float radiusY,
            int color,
            boolean stroke,
            float strokeWidth,
            boolean antiAlias);

    public static native void drawOval(
            long handle,
            float left,
            float top,
            float right,
            float bottom,
            int color,
            boolean stroke,
            float strokeWidth,
            boolean antiAlias);

    public static native void drawCircle(
            long handle,
            float x,
            float y,
            float radius,
            int color,
            boolean stroke,
            float strokeWidth,
            boolean antiAlias);

    public static native void drawLine(
            long handle,
            float x0,
            float y0,
            float x1,
            float y1,
            int color,
            float strokeWidth,
            boolean antiAlias);
}
