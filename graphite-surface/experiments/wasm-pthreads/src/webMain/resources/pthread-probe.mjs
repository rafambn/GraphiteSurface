const statusElement = document.getElementById("status");
const metricsElement = document.getElementById("metrics");
let loadedWasm;

function mark(phase) {
    statusElement.textContent = phase;
    navigator.sendBeacon(`/__probe?phase=${encodeURIComponent(phase)}`);
}

const statusNames = new Map([
    [0, "idle"],
    [1, "starting render pthread"],
    [2, "requesting WebGPU on render pthread"],
    [3, "two recorder pthreads active"],
    [4, "presented"],
    [-1, "render pthread creation failed"],
    [-2, "WebGPU device initialization failed"],
    [-3, "Graphite Context creation failed"],
    [-4, "presentation target creation failed"],
    [-5, "recorder pthread failed"],
    [-6, "recorder work failed"],
]);

function nativeExport(name) {
    const value = loadedWasm._[name];
    if (typeof value !== "function") {
        throw new Error(`Missing native experiment export: ${name}`);
    }
    return value;
}

async function loadGraphiteSideModule(skiko) {
    const startLoad = nativeExport("graphite_pthread_load_side_module");
    const readLoadStatus = nativeExport("graphite_pthread_side_module_status");
    const startResult = startLoad();
    if (startResult < 0) {
        throw new Error(`Graphite side module load failed with ${startResult}`);
    }

    while (true) {
        const status = readLoadStatus();
        if (status === 2) break;
        if (status < 0) throw new Error("emscripten_dlopen failed for Graphite side module");
        await new Promise(resolve => setTimeout(resolve, 10));
    }

    await skiko.loadSkikoExtension("skiko-graphite.wasm");
}

function updateMetrics(status, started, finished, deviceError) {
    const overlap = Math.max(
        0,
        Math.min(finished[0], finished[1]) - Math.max(started[0], started[1]),
    );
    statusElement.textContent = `Native status ${status}: ${statusNames.get(status) ?? "unknown"}`;
    metricsElement.textContent = JSON.stringify({
        status,
        recorder0: { startedAtMs: started[0], finishedAtMs: finished[0] },
        recorder1: { startedAtMs: started[1], finishedAtMs: finished[1] },
        overlapMs: overlap,
        deviceError,
        crossOriginIsolated,
    }, null, 2);
}

try {
    mark("probe script started");
    mark(`crossOriginIsolated=${crossOriginIsolated}`);
    if (!crossOriginIsolated) {
        throw new Error("crossOriginIsolated is false; COOP/COEP headers are required");
    }

    mark("importing Skiko main module");
    const skiko = await import("./skiko.mjs");
    loadedWasm = skiko.loadedWasm;
    mark("Skiko main module imported");
    await skiko.awaitSkiko;
    mark("loading Graphite side module through emscripten_dlopen");
    await loadGraphiteSideModule(skiko);
    mark("Skiko runtime initialized");
    const start = nativeExport("graphite_pthread_experiment_start");
    const readStatus = nativeExport("graphite_pthread_experiment_status");
    const readStarted = nativeExport("graphite_pthread_experiment_recorder_started");
    const readFinished = nativeExport("graphite_pthread_experiment_recorder_finished");
    const readDeviceError = nativeExport("graphite_pthread_experiment_device_error");

    const startResult = start();
    if (startResult !== 0) {
        throw new Error(`pthread_create returned ${startResult}`);
    }

    const timer = setInterval(() => {
        const status = readStatus();
        const started = [readStarted(0), readStarted(1)];
        const finished = [readFinished(0), readFinished(1)];
        updateMetrics(status, started, finished, readDeviceError());
        if (status < 0 || status === 4) {
            clearInterval(timer);
        }
    }, 50);
} catch (error) {
    statusElement.textContent = `Probe failed: ${error?.message ?? error}`;
    navigator.sendBeacon(`/__probe?phase=${encodeURIComponent(`failed: ${error?.stack ?? error}`)}`);
    console.error(error);
}
