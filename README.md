# GraphiteSurface

Workspace for exposing Skia Graphite through a Compose Multiplatform surface.

## Layout

- `skiko-fork/skiko`: full Skiko fork tracked as a Git submodule. The upstream remote is `upstream`.
- `skiko-fork/skiko/skiko/skiko-graphite`: Graphite `Context`, `Recorder`, `Recording`, and surface bindings from Skiko.
- `graphite-surface/graphite-surface`: Compose Multiplatform composable and public API.
- `graphite-surface/sample`: platform samples.
- `build-logic`: shared Gradle conventions.

## Checkout

```bash
git clone --recurse-submodules https://github.com/rafambn/GraphiteSurface.git
```

The Skiko submodule is pinned by the root repository. To update it from JetBrains:

```bash
git -C skiko-fork/skiko fetch upstream
git -C skiko-fork/skiko switch master
git -C skiko-fork/skiko merge upstream/master
git add skiko-fork/skiko
```

The Graphite binding is currently an experimental Skiko module. Android-specific work will remain in the surface project until the upstream module supports the required Android backend.
