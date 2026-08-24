const endpoint = Bun.argv[2];
const timeoutMs = Number(Bun.argv[3] ?? "30000");
const navigateTo = Bun.argv[4];
const viewport = Bun.argv[5]?.split("x").map(Number);

if (!endpoint) {
    throw new Error("Usage: bun run cdp-probe.ts <webSocketDebuggerUrl> [timeoutMs]");
}

const socket = new WebSocket(endpoint);
let nextId = 1;
const pending = new Map<number, {
    resolve: (value: unknown) => void;
    reject: (reason: unknown) => void;
}>();

function call(
    method: string,
    params: Record<string, unknown> = {},
    sessionId?: string,
): Promise<any> {
    const id = nextId++;
    socket.send(JSON.stringify({ id, method, params, sessionId }));
    return new Promise((resolve, reject) => pending.set(id, { resolve, reject }));
}

socket.addEventListener("message", event => {
    const message = JSON.parse(String(event.data));
    if (message.id) {
        const request = pending.get(message.id);
        if (!request) return;
        pending.delete(message.id);
        if (message.error) request.reject(new Error(message.error.message));
        else request.resolve(message.result);
        return;
    }

    if (message.method === "Target.attachedToTarget") {
        const sessionId = message.params.sessionId;
        void call("Runtime.enable", {}, sessionId)
            .then(() => call("Runtime.runIfWaitingForDebugger", {}, sessionId));
    } else if (message.method === "Runtime.consoleAPICalled") {
        const values = message.params.args.map((argument: any) => argument.value ?? argument.description);
        console.log(message.sessionId ? "[worker console]" : "[console]", ...values);
    } else if (message.method === "Runtime.exceptionThrown") {
        const details = message.params.exceptionDetails;
        console.error(
            message.sessionId ? "[worker exception]" : "[exception]",
            details.exception?.description ?? details.text,
            JSON.stringify(details.stackTrace ?? null),
        );
    }
});

await new Promise<void>((resolve, reject) => {
    socket.addEventListener("open", () => resolve(), { once: true });
    socket.addEventListener("error", event => reject(event), { once: true });
});

await call("Runtime.enable");
await call("Page.enable");
await call("Page.bringToFront");
if (viewport?.length === 2 && viewport.every(Number.isFinite)) {
    await call("Emulation.setDeviceMetricsOverride", {
        width: viewport[0],
        height: viewport[1],
        deviceScaleFactor: 1,
        mobile: false,
    });
}
await call("Target.setAutoAttach", {
    autoAttach: true,
    waitForDebuggerOnStart: false,
    flatten: true,
});
if (navigateTo) {
    await call("Page.navigate", { url: navigateTo });
}
await call("Runtime.evaluate", {
    expression: "window.__graphiteProbeRaf = 0; requestAnimationFrame(() => window.__graphiteProbeRaf += 1)",
});

const deadline = Date.now() + timeoutMs;
let lastState = "";
const screenshotHashes: string[] = [];
while (Date.now() < deadline) {
    const evaluation = await call("Runtime.evaluate", {
        expression: `({
            readyState: document.readyState,
            visibilityState: document.visibilityState,
            rafCount: window.__graphiteProbeRaf ?? 0,
            status: document.getElementById("status")?.textContent ?? null,
            metrics: document.getElementById("metrics")?.textContent ?? null,
            graphiteCanvasCount: document.querySelectorAll("canvas").length,
            graphiteCanvases: Array.from(document.querySelectorAll("canvas")).map(canvas => ({
                width: canvas.width,
                height: canvas.height,
                rect: canvas.getBoundingClientRect().toJSON(),
            })),
            shadowCanvas: (() => {
                const canvas = document.querySelector("#root > div > div")?.shadowRoot?.querySelector("canvas");
                return canvas ? {
                    width: canvas.width,
                    height: canvas.height,
                    rect: canvas.getBoundingClientRect().toJSON(),
                } : null;
            })(),
            workers: performance.getEntriesByType("resource")
                .map(entry => entry.name)
                .filter(name => name.includes("Worker") || name.includes("worker")),
            crossOriginIsolated,
            sharedArrayBuffer: typeof SharedArrayBuffer,
            webGpu: Boolean(navigator.gpu),
        })`,
        returnByValue: true,
    });
    const state = evaluation.result.value;
    const serialized = JSON.stringify(state);
    if (serialized !== lastState) {
        console.log(serialized);
        lastState = serialized;
    }
    if (state.status?.includes("presented") || state.status?.startsWith("Probe failed")) {
        break;
    }
    if (screenshotHashes.length < 2 && (screenshotHashes.length === 0 || Date.now() + 1000 >= deadline)) {
        const screenshot = await call("Page.captureScreenshot", { format: "png", fromSurface: true });
        const hash = new Bun.CryptoHasher("sha256")
            .update(Buffer.from(screenshot.data, "base64"))
            .digest("hex");
        screenshotHashes.push(hash);
    }
    await Bun.sleep(100);
}

console.log(JSON.stringify({ screenshotHashes }));

socket.close();
