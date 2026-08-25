import * as Skia from './skiko.mjs';
import * as Graphite from './skiko-graphite.mjs';

let offscreen = null;
let gpuCanvas = null;
let gpuDevice = null;
let graphiteContext = 0;
let recorder = 0;
let retainedSurface = 0;
let retainedBackendTexture = 0;
let disposed = false;
let disposeRequested = false;
let fatalShutdown = false;
let rendering = false;
let pendingFrame = null;
let canvasWidth = 0;
let canvasHeight = 0;
let frameCount = 0;

const disposeManaged = (pointer, finalizer) => {
    if (!pointer) return;
    if (typeof finalizer !== 'function') throw new Error('Skia finalizer export is unavailable');
    Skia.org_jetbrains_skia_impl_Managed__invokeFinalizer(finalizer(), pointer);
};

const disposeSkia = (pointer, type) =>
    disposeManaged(pointer, Skia[`org_jetbrains_skia_${type}__1nGetFinalizer`]);

const disposeGraphite = (pointer, type) =>
    disposeManaged(pointer, Graphite[`org_jetbrains_skia_gpu_graphite_${type}__1nGetFinalizer`]);

const disposeSurface = (pointer) =>
    disposeManaged(pointer, Skia.org_jetbrains_skia_impl_RefCnt__getFinalizer);

const withFloatArray = (values, block) => {
    const pointer = Skia.malloc(values.length * 4);
    try {
        for (let index = 0; index < values.length; index += 1) {
            Skia.skia_memSetFloat(pointer + index * 4, values[index]);
        }
        return block(pointer);
    } finally {
        Skia.free(pointer);
    }
};

const createPaint = (color, stroke, strokeWidth, antiAlias) => {
    const paint = Skia.org_jetbrains_skia_Paint__1nMake();
    if (!paint) throw new Error('Skia failed to create a paint');
    Skia.org_jetbrains_skia_Paint__1nSetColor(paint, color | 0);
    Skia.org_jetbrains_skia_Paint__1nSetMode(paint, stroke ? 1 : 0);
    Skia.org_jetbrains_skia_Paint__1nSetStrokeWidth(paint, strokeWidth);
    Skia.org_jetbrains_skia_Paint__1nSetAntiAlias(paint, Boolean(antiAlias));
    return paint;
};

const buildPath = (verbs, points, weights, fillType) => {
    const builder = Skia.org_jetbrains_skia_PathBuilder__1nMake();
    if (!builder) throw new Error('Skia failed to create a path builder');
    Skia.org_jetbrains_skia_PathBuilder__1nSetFillType(builder, fillType);
    let pointIndex = 0;
    try {
        for (let verbIndex = 0; verbIndex < verbs.length; verbIndex += 1) {
            const verb = verbs[verbIndex];
            if (verb === 1) {
                Skia.org_jetbrains_skia_PathBuilder__1nMoveTo(
                    builder,
                    points[pointIndex++],
                    points[pointIndex++],
                );
            } else if (verb === 2) {
                Skia.org_jetbrains_skia_PathBuilder__1nLineTo(
                    builder,
                    points[pointIndex++],
                    points[pointIndex++],
                );
            } else if (verb === 3) {
                Skia.org_jetbrains_skia_PathBuilder__1nQuadTo(
                    builder,
                    points[pointIndex++],
                    points[pointIndex++],
                    points[pointIndex++],
                    points[pointIndex++],
                );
            } else if (verb === 4) {
                Skia.org_jetbrains_skia_PathBuilder__1nConicTo(
                    builder,
                    points[pointIndex++],
                    points[pointIndex++],
                    points[pointIndex++],
                    points[pointIndex++],
                    weights[verbIndex],
                );
            } else if (verb === 5) {
                Skia.org_jetbrains_skia_PathBuilder__1nCubicTo(
                    builder,
                    points[pointIndex++],
                    points[pointIndex++],
                    points[pointIndex++],
                    points[pointIndex++],
                    points[pointIndex++],
                    points[pointIndex++],
                );
            } else if (verb === 6) {
                Skia.org_jetbrains_skia_PathBuilder__1nClosePath(builder);
            } else {
                throw new Error(`Unknown Graphite path verb ${verb}`);
            }
        }
        return Skia.org_jetbrains_skia_PathBuilder__1nDetach(builder);
    } finally {
        disposeSkia(builder, 'PathBuilder');
    }
};

const drawWithPaint = (canvas, command, operation) => {
    const length = command.length;
    const color = command[length - 4];
    const stroke = command[length - 3];
    const strokeWidth = command[length - 2];
    const antiAlias = command[length - 1];
    const paint = createPaint(color, stroke, strokeWidth, antiAlias);
    try {
        operation(paint);
    } finally {
        disposeSkia(paint, 'Paint');
    }
};

const executeCommands = (canvas, encoded) => {
    const commands = JSON.parse(encoded);
    if (!Array.isArray(commands)) throw new Error('Malformed Graphite frame commands');
    for (const command of commands) {
        switch (command[0]) {
            case 0:
                Skia.org_jetbrains_skia_Canvas__1nClear(canvas, command[1] | 0);
                break;
            case 1:
                Skia.org_jetbrains_skia_Canvas__1nSave(canvas);
                break;
            case 2:
                Skia.org_jetbrains_skia_Canvas__1nRestore(canvas);
                break;
            case 3:
                Skia.org_jetbrains_skia_Canvas__1nTranslate(canvas, command[1], command[2]);
                break;
            case 4:
                Skia.org_jetbrains_skia_Canvas__1nRotate(canvas, command[1], 0, 0);
                break;
            case 5: {
                const columnMajor = command[2];
                const rowMajor = [
                    columnMajor[0], columnMajor[4], columnMajor[8], columnMajor[12],
                    columnMajor[1], columnMajor[5], columnMajor[9], columnMajor[13],
                    columnMajor[2], columnMajor[6], columnMajor[10], columnMajor[14],
                    columnMajor[3], columnMajor[7], columnMajor[11], columnMajor[15],
                ];
                withFloatArray(rowMajor, (pointer) =>
                    Skia.org_jetbrains_skia_Canvas__1nConcat44(canvas, pointer));
                break;
            }
            case 6:
                Skia.org_jetbrains_skia_Canvas__1nClipRect(
                    canvas, command[1], command[2], command[3], command[4], 1, Boolean(command[5]),
                );
                break;
            case 7: {
                const path = buildPath(command[1], command[2], command[3], command[4]);
                const paint = createPaint(command[5], command[6], command[7], command[8]);
                try {
                    Skia.org_jetbrains_skia_Canvas__1nDrawPath(canvas, path, paint);
                } finally {
                    disposeSkia(path, 'Path');
                    disposeSkia(paint, 'Paint');
                }
                break;
            }
            case 8:
                drawWithPaint(canvas, command, (paint) =>
                    Skia.org_jetbrains_skia_Canvas__1nDrawRect(
                        canvas, command[1], command[2], command[3], command[4], paint,
                    ));
                break;
            case 9:
                drawWithPaint(canvas, command, (paint) => {
                    const radii = [
                        command[5], command[6], command[5], command[6],
                        command[5], command[6], command[5], command[6],
                    ];
                    withFloatArray(radii, (pointer) =>
                        Skia.org_jetbrains_skia_Canvas__1nDrawRRect(
                            canvas, command[1], command[2], command[3], command[4], pointer, 8, paint,
                        ));
                });
                break;
            case 10:
                drawWithPaint(canvas, command, (paint) =>
                    Skia.org_jetbrains_skia_Canvas__1nDrawOval(
                        canvas, command[1], command[2], command[3], command[4], paint,
                    ));
                break;
            case 11:
                drawWithPaint(canvas, command, (paint) => {
                    const x = command[1];
                    const y = command[2];
                    const radius = command[3];
                    Skia.org_jetbrains_skia_Canvas__1nDrawOval(
                        canvas, x - radius, y - radius, x + radius, y + radius, paint,
                    );
                });
                break;
            case 12: {
                const paint = createPaint(command[5], true, command[6], command[7]);
                try {
                    Skia.org_jetbrains_skia_Canvas__1nDrawLine(
                        canvas, command[1], command[2], command[3], command[4], paint,
                    );
                } finally {
                    disposeSkia(paint, 'Paint');
                }
                break;
            }
            default:
                throw new Error(`Unknown Graphite draw opcode ${command[0]}`);
        }
    }
};

const initialize = async (canvas) => {
    offscreen = canvas;
    const adapter = await navigator.gpu.requestAdapter();
    if (!adapter) throw new Error('WebGPU adapter creation returned null');
    const device = await adapter.requestDevice();
    gpuDevice = device;
    void device.lost.then((info) => {
        if (!disposed && !disposeRequested) {
            fail(new Error(`WebGPU device lost (${info.reason}): ${info.message || 'no details'}`));
        }
    });
    await Skia.awaitSkiko;
    // Emscripten 4.0.7's legacy WebGPU bridge still asks for the removed
    // maxInterStageShaderComponents limit. Keep this compatibility view local
    // to Emscripten; the canvas continues to use the real GPUDevice.
    const emscriptenLimits = new Proxy(device.limits, {
        get(target, property) {
            if (property === 'maxInterStageShaderComponents') {
                return target.maxInterStageShaderVariables * 4;
            }
            return Reflect.get(target, property, target);
        },
    });
    const boundDeviceMethods = new Map();
    const emscriptenDevice = new Proxy(device, {
        get(target, property) {
            if (property === 'limits') return emscriptenLimits;
            const value = Reflect.get(target, property, target);
            if (typeof value !== 'function') return value;
            let bound = boundDeviceMethods.get(property);
            if (!bound) {
                bound = value.bind(target);
                boundDeviceMethods.set(property, bound);
            }
            return bound;
        },
    });
    Skia.setWebGPUDevice(emscriptenDevice);
    gpuCanvas = offscreen.getContext('webgpu');
    if (!gpuCanvas) throw new Error('OffscreenCanvas does not expose a WebGPU context');
    gpuCanvas.configure({
        device,
        format: navigator.gpu.getPreferredCanvasFormat(),
        alphaMode: 'premultiplied',
    });
    graphiteContext = Graphite.org_jetbrains_skia_gpu_graphite_GraphiteContext__1nMakeDawn();
    if (!graphiteContext) throw new Error('Failed to create the Skia Graphite Dawn context');
    recorder = Graphite.org_jetbrains_skia_gpu_graphite_GraphiteContext__1nMakeRecorder(graphiteContext);
    if (!recorder) throw new Error('Failed to create the Skia Graphite presentation recorder');
};

const render = async (width, height, commands) => {
    if (disposed || !graphiteContext || !recorder) return;
    frameCount += 1;
    if (width !== canvasWidth || height !== canvasHeight) {
        offscreen.width = width;
        offscreen.height = height;
        canvasWidth = width;
        canvasHeight = height;
    }
    const textureHandle = Skia.addWebGPUTexture(gpuCanvas.getCurrentTexture());
    let backendTexture = Graphite.org_jetbrains_skia_gpu_graphite_BackendTexture__1nMakeDawn(textureHandle);
    if (!backendTexture) throw new Error('Failed to wrap the WebGPU swapchain texture');
    let surface = Graphite.org_jetbrains_skia_gpu_graphite_SurfaceFactory__1nWrapBackendTexture(
        recorder,
        backendTexture,
        0,
        0,
    );
    if (!surface) {
        disposeGraphite(backendTexture, 'BackendTexture');
        throw new Error('Failed to create the Skia Graphite presentation surface');
    }

    try {
        const canvas = Skia.org_jetbrains_skia_Surface__1nGetCanvas(surface);
        executeCommands(canvas, commands);
        const recording = Graphite.org_jetbrains_skia_gpu_graphite_Recorder__1nSnap(recorder);
        if (!recording) throw new Error('Failed to snap the presentation recording');
        try {
            Graphite.org_jetbrains_skia_gpu_graphite_GraphiteContext__1nInsertRecording(
                graphiteContext,
                recording,
            );
            Graphite.org_jetbrains_skia_gpu_graphite_GraphiteContext__1nSubmit(graphiteContext, false);
        } finally {
            disposeGraphite(recording, 'Recording');
        }

        const previousSurface = retainedSurface;
        const previousBackendTexture = retainedBackendTexture;
        retainedSurface = surface;
        retainedBackendTexture = backendTexture;
        surface = 0;
        backendTexture = 0;
        disposeSurface(previousSurface);
        disposeGraphite(previousBackendTexture, 'BackendTexture');

        // Graphite's WebGPU upload buffers are remapped asynchronously. Yield
        // until this submission completes before accepting another frame so
        // those callbacks can run and the staging buffers can be reused.
        await gpuDevice.queue.onSubmittedWorkDone();
        Graphite.org_jetbrains_skia_gpu_graphite_GraphiteContext__1nCheckAsyncWorkCompletion(
            graphiteContext,
        );
        let completionPolls = 0;
        while (
            Graphite.org_jetbrains_skia_gpu_graphite_GraphiteContext__1nHasUnfinishedGpuWork(
                graphiteContext,
            ) && completionPolls < 4
        ) {
            completionPolls += 1;
            await new Promise((resolve) => setTimeout(resolve, 0));
            Graphite.org_jetbrains_skia_gpu_graphite_GraphiteContext__1nCheckAsyncWorkCompletion(
                graphiteContext,
            );
        }
        if (Graphite.org_jetbrains_skia_gpu_graphite_GraphiteContext__1nHasUnfinishedGpuWork(
            graphiteContext,
        )) {
            throw new Error('Graphite GPU completion callback did not settle');
        }
    } finally {
        disposeSurface(surface);
        disposeGraphite(backendTexture, 'BackendTexture');
    }
};

const finishDispose = () => {
    if (disposed) return;
    disposed = true;
    if (!fatalShutdown) {
        disposeSurface(retainedSurface);
        disposeGraphite(retainedBackendTexture, 'BackendTexture');
        disposeGraphite(recorder, 'Recorder');
        disposeGraphite(graphiteContext, 'GraphiteContext');
    }
    retainedSurface = 0;
    retainedBackendTexture = 0;
    recorder = 0;
    graphiteContext = 0;
    gpuDevice = null;
    postMessage({ type: 'disposed' });
    self.close();
};

const requestDispose = () => {
    disposeRequested = true;
    pendingFrame = null;
    if (!rendering) finishDispose();
};

const fail = (error) => {
    if (disposed || disposeRequested) return;
    fatalShutdown = true;
    postMessage({
        type: 'error',
        message: `${error?.message || error}; frame=${frameCount}`,
    });
    requestDispose();
};

const pumpFrames = async () => {
    if (rendering || disposed || disposeRequested) return;
    rendering = true;
    try {
        while (pendingFrame && !disposeRequested) {
            const frame = pendingFrame;
            pendingFrame = null;
            await render(frame.width, frame.height, frame.commands);
        }
    } catch (error) {
        fail(error);
    } finally {
        rendering = false;
        if (disposeRequested) finishDispose();
        else if (pendingFrame) void pumpFrames();
    }
};

self.onmessage = (event) => {
    const message = event.data || {};
    if (message.type === 'init') {
        void initialize(message.canvas).then(
            () => postMessage({ type: 'ready' }),
            fail,
        );
    } else if (message.type === 'frame') {
        if (disposed || disposeRequested) return;
        pendingFrame = message;
        void pumpFrames();
    } else if (message.type === 'dispose') {
        requestDispose();
    }
};
