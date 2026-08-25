# Compose graphics types migration plan

## Status

Implemented on 2026-08-25. The migration was explicitly requested and completed
in the current tree. The document remains as its design and verification
record. A later API simplification removed `GraphitePaint`, `GraphiteSize`,
`GraphiteIntOffset`, and `GraphiteIntRect`. `GraphiteTransform` remains the one
compact command value because Compose `Matrix` is mutable.

The goal is to remove public Graphite value types that duplicate public Compose
graphics types. The command stream remains the boundary between application
code and Graphite workers:

```text
Compose types passed to the Graphite DSL
    -> Graphite command compiler snapshots and encodes them
    -> portable command bytes cross the worker boundary
    -> graphite-engine rebuilds native Skiko objects and draws them
```

This migration does not expose `org.jetbrains.skia` types, use the internal
`SkiaBackedPath`, or send native handles between threads or workers.

## Target API

The intended call site uses public Compose types:

```kotlin
val path = Path().apply {
    moveTo(0f, -100f)
    lineTo(75f, 50f)
    lineTo(-75f, 50f)
    close()
}

val displayList = GraphiteDisplayList.build {
    drawPath(path, GraphitePaint(Color.Red))
}
```

`GraphiteEncoder` remains a thin command compiler. It does not retain `Path`,
`Color`, or another mutable Compose object. Every argument is read and encoded
synchronously before the DSL call returns.

On the render side, `graphite-engine` decodes the portable representation and
uses Skiko APIs such as `PathBuilder` to construct native objects locally.

## Type decisions

| Current type | Target | Decision |
| --- | --- | --- |
| `GraphitePoint` | `Offset` | Replace. Encode `x` and `y` immediately. |
| `GraphiteRect` | `Rect` | Replace. Encode its four edges immediately. |
| `GraphiteColor` | `Color` | Replace. Convert to the command protocol's canonical color representation while encoding. |
| `GraphitePath` | `Path` | Replace after the command protocol supports every required path segment. |
| `GraphitePathBuilder` | `Path` construction APIs | Remove with `GraphitePath`. |
| `GraphitePathVerb` | Internal command opcodes | Remove after the new path encoding is established. |
| `GraphiteTransform` | Possibly `Matrix` | Decide separately. `Matrix` is mutable and its composition semantics must match exactly. |
| `GraphitePaint` | Keep initially | It is a compact immutable description of the supported subset. Compose `Paint` is mutable and exposes features the engine may not support. |
| `GraphiteEncoder` | Keep | It is the DSL and command compiler, not a duplicate geometry value. |
| `GraphiteDisplayList` | Keep | It represents the immutable compiled command program. |

Do not make `GraphiteEncoder` implement Compose `Canvas` during this migration.
The `Canvas` contract is broader than the current renderer, while Graphite also
has commands such as display-list insertion that do not belong to that API.

## Protocol rules

The project owns the command format. Do not use Skia's serialized path bytes as
the wire format because that would couple recordings to a native library
version and implementation detail.

The path encoding must represent:

- move;
- line;
- quadratic curve;
- conic curve and its weight;
- cubic curve;
- close;
- fill type;
- multiple contours.

Use stable Graphite-owned numeric opcodes. Preserve conics directly when the
Compose iterator exposes them; do not approximate them unless the chosen
Compose version makes direct preservation impossible. If approximation becomes
necessary, specify and test one fixed tolerance before implementation.

Bump `GraphiteCommandBuffer.Version` whenever the byte layout changes. Land the
encoder, validator, and every platform decoder together so the repository never
contains a mixed protocol version.

Validation must reject unknown opcodes, truncated payloads, invalid counts,
non-finite coordinates, invalid conic weights, excessive nesting, and command
buffers above their configured limit. Large path iteration must retain the
existing cancellation behavior by probing at bounded intervals.

## Migration sequence

### 1. Establish the baseline

- Inventory every public signature, sample, test, and document that refers to
  the types in the table above.
- Add or confirm golden tests for the current command header, paths, colors,
  rectangles, and transforms.
- Record the targeted compile and test tasks for every supported platform.
- Treat existing unrelated JS or Wasm infrastructure failures separately; do
  not silently weaken the migration gates because of them.

Completion criteria:

- Every affected public API and protocol reader is accounted for.
- The current rendering behavior has tests that can detect semantic drift.

### 2. Teach the compiler to encode Compose `Path`

- Add internal encoding from public `Path` through its public iterator API.
- Encode the complete segment set and fill type into the portable command
  stream.
- Decode the new representation inside `graphite-engine` and rebuild the
  platform path with Skiko `PathBuilder`.
- Keep `GraphitePath` temporarily so both inputs can be compared during this
  phase.
- Ensure path mutation after `drawPath` returns cannot alter the compiled
  display list or recording.

Completion criteria:

- Move, line, quadratic, conic, cubic, close, fill type, and multi-contour tests
  pass on every supported decoder.
- Equivalent old and new paths produce equivalent rendering for the old
  move/line/close subset.
- Only command bytes and immutable command resources cross worker boundaries.

### 3. Replace the public path API

- Change `GraphiteEncoder.drawPath` to accept Compose `Path`.
- Migrate samples, tests, and documentation to Compose path construction.
- Prefer a direct breaking replacement because the project is pre-release and
  the stated objective is API simplification. Add a temporary overload only if
  compatibility becomes an explicit requirement at implementation time.

Completion criteria:

- Every public example uses Compose `Path`.
- No public API mentions a Skiko-native path type.
- Browser recordings remain transferable byte programs rather than shared
  object graphs.

### 4. Delete the duplicate path model

- Remove `GraphitePath`, `GraphitePathBuilder`, and `GraphitePathVerb`.
- Remove adapters and decoder branches that exist only for the old model.
- Decode directly into the engine's local native builder or a minimal internal
  portable representation when validation requires one.

Completion criteria:

- The repository contains no references to the deleted path types.
- No compatibility wrapper preserves the removed API without an explicit
  requirement.

### 5. Replace simple value types

Migrate one value category per change so failures remain attributable:

1. `GraphitePoint` to `Offset`.
2. `GraphiteRect` to `Rect`.
3. `GraphiteColor` to `Color`.

For `Color`, define one canonical wire representation. If the protocol remains
8-bit sRGB, convert every Compose color to sRGB and define the rounding rule
before packing it. Test transparent, partially transparent, extended-sRGB, and
wide-gamut inputs. Unsupported values must not be interpreted differently by
different platforms.

Completion criteria:

- The three Graphite value types and their adapters are deleted.
- Equal semantic inputs produce deterministic command bytes on every target.
- Rect edge ordering and empty/inverted rectangle behavior are explicit and
  tested.

### 6. Evaluate transforms independently

Do not replace `GraphiteTransform` merely for consistency. First verify:

- row-major versus column-major storage;
- multiplication order;
- translation, scale, rotation, and pivot behavior;
- the 2D subset accepted by each backend;
- whether a mutable Compose `Matrix` can be snapshotted without allocation or
  ambiguity at every encoder call.

Replace it only if the public API becomes simpler without changing transform
semantics. Otherwise retain `GraphiteTransform` and document why it is a
deliberate command value rather than an accidental duplicate.

### 7. Re-evaluate paint after geometry migration

Keep `GraphitePaint` during the main migration. Afterward, compare its supported
fields with Compose `Paint`.

Replacing it is acceptable only if the compiler can snapshot every supported
field and reject every unsupported field explicitly. Never retain a mutable
Compose paint object for later worker execution, and never silently discard a
shader, path effect, color filter, blend mode, or stroke property.

The default decision is to retain the smaller immutable `GraphitePaint`.

### 8. Finish documentation and cleanup

- Update the README, API KDoc, samples, and `SURFACE_AND_NATIVE_THREADS.md`.
- State that Compose objects are compiler inputs, not objects owned by the
  runtime or transported to a worker.
- State that `org.jetbrains.skia.*` imports are restricted to
  `graphite-engine` implementation source sets.
- Remove obsolete adapters, tests, and terminology after the replacement APIs
  are proven.
- Add a short breaking-change note listing deleted Graphite types and their
  Compose replacements.

## Verification matrix

Unit and protocol tests must cover:

- every path segment, conic weight, fill type, and multiple contours;
- empty paths, closed paths, very large paths, and command-size limits;
- malformed and truncated path payloads;
- mutation of `Path`, `Matrix`, or another mutable input after an encoder call;
- exact color conversion and packing;
- rectangle edge and empty-rectangle semantics;
- deterministic command hashing and display-list comparison;
- rejection of unsupported protocol versions;
- equivalent rendering through direct recording and `GraphiteDisplayList`.

Platform verification must include the repository's targeted JVM, Android, JS,
Wasm, iOS, sample, and browser-worker tasks. A visual browser smoke test should
exercise curves, fill rules, transforms, and display-list reuse because a
common-source test alone cannot prove worker reconstruction.

## Guardrails

- No public or common API may depend on `org.jetbrains.skia.Path`,
  `PathBuilder`, or another managed native object.
- Do not access `SkiaBackedPath`; it is an internal Compose implementation
  detail.
- Never retain mutable Compose inputs past the DSL call that receives them.
- Never send native handles or mutable Compose objects to another worker.
- Never silently ignore an unsupported graphics feature. Reject it while
  compiling the command.
- Keep encoding deterministic so content hashes and display-list comparison do
  not depend on platform object identity.
- Keep protocol limits and cancellation checks effective for adversarially
  large paths.
- Keep Skiko version differences contained within `graphite-engine`.

## Rollback strategy

Each numbered phase should be a separate, reversible commit. Do not delete an
old type until its Compose replacement passes the cross-platform round-trip
tests. If a platform cannot reconstruct the new protocol, revert that phase
instead of keeping two permanent wire formats.

The command-buffer version change is atomic: encoder and all decoders advance
together. Rolling back that commit restores the previous version everywhere.

## Definition of done

The migration is complete when:

- public drawing APIs use Compose `Path`, `Offset`, `Rect`, and `Color`;
- `GraphitePath`, `GraphitePathBuilder`, `GraphitePathVerb`, `GraphitePoint`,
  `GraphiteRect`, and `GraphiteColor` no longer exist;
- the decision on `GraphiteTransform` is implemented and documented;
- `GraphitePaint` remains only by deliberate decision or has a fully validated
  replacement;
- `GraphiteEncoder` is visibly a synchronous command compiler;
- native Skiko objects are created only inside `graphite-engine`;
- worker communication remains portable command bytes and immutable resource
  data;
- the full targeted verification matrix passes; and
- architecture documentation matches the implemented ownership and thread
  model.
