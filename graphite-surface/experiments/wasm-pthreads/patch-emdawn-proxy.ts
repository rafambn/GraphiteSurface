const libraryPath = Bun.argv[2];

if (!libraryPath) {
    throw new Error("Usage: bun run patch-emdawn-proxy.ts <library_webgpu.js>");
}

const markers = [
    "// Add and set __i53abi to true for functions with 64-bit value in their",
    "// Inverted index used by EnumerateFeatures/HasFeature",
];
const proxyMarker = "// GraphiteSurface pthread compatibility: keep JS WebGPU objects on one owner.";
const source = await Bun.file(libraryPath).text();

if (source.includes(proxyMarker)) {
    console.log(`Emdawn proxy already present in ${libraryPath}`);
    process.exit(0);
}

const marker = markers.find(candidate => source.includes(candidate));
if (!marker) {
    throw new Error(`Unsupported Emdawn library: insertion marker not found in ${libraryPath}`);
}

const proxy = `${proxyMarker}
for (const key of Object.keys(LibraryWebGPU)) {
  if (typeof LibraryWebGPU[key] === 'function' && !(key + '__proxy' in LibraryWebGPU)) {
    LibraryWebGPU[key + '__proxy'] = 'sync';
  }
}

`;

await Bun.write(libraryPath, source.replace(marker, proxy + marker));
console.log(`Added synchronous pthread proxying to ${libraryPath}`);
