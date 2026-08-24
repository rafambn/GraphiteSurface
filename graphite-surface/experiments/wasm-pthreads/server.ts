import { extname, resolve, sep } from "node:path";

const root = resolve(Bun.argv[2] ?? "build/pthreadExperiment/js");
const port = Number(Bun.argv[3] ?? "8080");

const contentTypes: Record<string, string> = {
    ".html": "text/html; charset=utf-8",
    ".js": "text/javascript; charset=utf-8",
    ".mjs": "text/javascript; charset=utf-8",
    ".wasm": "application/wasm",
    ".map": "application/json; charset=utf-8",
};

const isolationHeaders = {
    "Cross-Origin-Opener-Policy": "same-origin",
    "Cross-Origin-Embedder-Policy": "require-corp",
    "Cross-Origin-Resource-Policy": "same-origin",
};

Bun.serve({
    hostname: "127.0.0.1",
    port,
    async fetch(request) {
        const url = new URL(request.url);
        if (url.pathname === "/__probe") {
            console.log(`[probe] ${url.searchParams.get("phase") ?? "unknown"}`);
            return new Response(null, { status: 204, headers: isolationHeaders });
        }
        const pathname = decodeURIComponent(url.pathname === "/" ? "/index.html" : url.pathname);
        const filePath = resolve(root, `.${pathname}`);
        if (filePath !== root && !filePath.startsWith(`${root}${sep}`)) {
            return new Response("Forbidden", { status: 403, headers: isolationHeaders });
        }

        const file = Bun.file(filePath);
        if (!(await file.exists())) {
            return new Response("Not found", { status: 404, headers: isolationHeaders });
        }

        return new Response(file, {
            headers: {
                ...isolationHeaders,
                "Content-Type": contentTypes[extname(filePath)] ?? "application/octet-stream",
            },
        });
    },
});

console.log(`Serving ${root} at http://127.0.0.1:${port}`);
