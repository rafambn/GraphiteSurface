@file:OptIn(kotlin.js.ExperimentalWasmJsInterop::class)

package com.rafambn.graphitesurface

import kotlin.js.JsAny
import kotlin.js.js
import kotlin.js.unsafeCast

internal actual class WebValidationWorker actual constructor(index: Int) {
    private val worker = createWorker(index)

    internal actual fun process(
        commands: ByteArray,
        onSuccess: (ByteArray) -> Unit,
        onFailure: (String) -> Unit,
    ) {
        postToWorker(
            worker = worker,
            commands = commands.unsafeCast<JsAny>(),
            onSuccess = { onSuccess(it.unsafeCast<ByteArray>()) },
            onFailure = onFailure,
        )
    }

    internal actual fun close() {
        terminateWorker(worker)
    }
}

private fun createWorker(index: Int): JsAny = js(
    """
    (function(index) {
      const source = `
        const resources = new Map();
        const validateCommands = function(bytes, resourceCount) {
          const view = new DataView(bytes.buffer, bytes.byteOffset, bytes.byteLength);
          const readInt = function(offset) {
            if (offset < 0 || offset + 4 > view.byteLength) {
              throw new Error('Graphite command buffer is truncated');
            }
            return view.getInt32(offset, true);
          };
          if (view.byteLength < 8 || readInt(0) !== 0x47534631 || readInt(4) !== 1) {
            throw new Error('invalid Graphite command-buffer header');
          }
          let offset = 8;
          let saveDepth = 0;
          while (offset < view.byteLength) {
            if (offset + 5 > view.byteLength) throw new Error('truncated Graphite command header');
            const opcode = view.getUint8(offset);
            if (opcode < 1 || opcode > 11) throw new Error('unknown Graphite command opcode');
            const payloadSize = readInt(offset + 1);
            if (payloadSize < 0) throw new Error('negative Graphite command payload');
            const payloadOffset = offset + 5;
            const nextOffset = payloadOffset + payloadSize;
            if (nextOffset > view.byteLength) throw new Error('truncated Graphite command payload');
            if (opcode === 1) saveDepth += 1;
            if (opcode === 2) {
              if (saveDepth === 0) throw new Error('Graphite restore has no matching save');
              saveDepth -= 1;
            }
            if (opcode === 5) {
              if (payloadSize !== 4) throw new Error('invalid Graphite display-list payload');
              const resourceIndex = readInt(payloadOffset);
              if (resourceIndex < 0 || resourceIndex >= resourceCount) {
                throw new Error('invalid Graphite display-list resource index');
              }
            }
            offset = nextOffset;
          }
          if (saveDepth !== 0) throw new Error('unbalanced Graphite save and restore commands');
        };
        const processMessage = function(bytes) {
          if (!(bytes instanceof Int8Array) && !(bytes instanceof Uint8Array)) {
            throw new Error('Graphite worker message is not a byte array');
          }
          const view = new DataView(bytes.buffer, bytes.byteOffset, bytes.byteLength);
          let offset = 0;
          const requireBytes = function(size) {
            if (size < 0 || offset + size > view.byteLength) {
              throw new Error('Graphite worker message is truncated');
            }
          };
          const readInt = function() {
            requireBytes(4);
            const value = view.getInt32(offset, true);
            offset += 4;
            return value;
          };
          const readCount = function() {
            const value = readInt();
            if (value < 0) throw new Error('negative Graphite worker-message count');
            return value;
          };
          const readId = function() {
            requireBytes(8);
            const low = view.getUint32(offset, true);
            const high = view.getUint32(offset + 4, true);
            offset += 8;
            if (low === 0 && high === 0) throw new Error('invalid Graphite resource ID');
            return high + ':' + low;
          };
          if (readInt() !== 0x47535731 || readInt() !== 1) {
            throw new Error('invalid Graphite worker-message header');
          }
          const publicationCount = readCount();
          for (let publication = 0; publication < publicationCount; publication += 1) {
            const resourceId = readId();
            if (resources.has(resourceId)) throw new Error('Graphite resource was published twice');
            const dependencyCount = readCount();
            for (let dependency = 0; dependency < dependencyCount; dependency += 1) {
              if (!resources.has(readId())) throw new Error('Graphite resource dependency is missing');
            }
            const commandSize = readCount();
            requireBytes(commandSize);
            validateCommands(
              new Int8Array(bytes.buffer, bytes.byteOffset + offset, commandSize),
              dependencyCount,
            );
            offset += commandSize;
            resources.set(resourceId, true);
          }
          const rootResourceCount = readCount();
          for (let resource = 0; resource < rootResourceCount; resource += 1) {
            if (!resources.has(readId())) throw new Error('Graphite root resource is missing');
          }
          const rootCommandSize = readCount();
          requireBytes(rootCommandSize);
          validateCommands(
            new Int8Array(bytes.buffer, bytes.byteOffset + offset, rootCommandSize),
            rootResourceCount,
          );
          offset += rootCommandSize;
          if (offset !== view.byteLength) throw new Error('Graphite worker message has trailing bytes');
        };
        self.onmessage = function(event) {
          const id = event.data.id;
          try {
            const bytes = event.data.bytes;
            processMessage(bytes);
            self.postMessage({ id: id, bytes: bytes }, [bytes.buffer]);
          } catch (error) {
            self.postMessage({ id: id, error: String(error && error.message || error) });
          }
        };
      `;
      const url = URL.createObjectURL(new Blob([source], { type: 'text/javascript' }));
      const worker = new Worker(url, { name: 'GraphiteRecorder-' + index });
      URL.revokeObjectURL(url);
      worker.__graphiteNextId = 1;
      worker.__graphitePending = new Map();
      worker.onmessage = function(event) {
        const pending = worker.__graphitePending.get(event.data.id);
        if (!pending) return;
        worker.__graphitePending.delete(event.data.id);
        if (event.data.error) pending.failure(event.data.error);
        else pending.success(event.data.bytes);
      };
      worker.onerror = function(event) {
        const message = String(event.message || 'recorder Worker terminated unexpectedly');
        for (const pending of worker.__graphitePending.values()) pending.failure(message);
        worker.__graphitePending.clear();
      };
      return worker;
    })(arguments[0])
    """,
)

private fun postToWorker(
    worker: JsAny,
    commands: JsAny,
    onSuccess: (JsAny) -> Unit,
    onFailure: (String) -> Unit,
) {
    js(
        """
        (function(worker, commands, onSuccess, onFailure) {
          const id = worker.__graphiteNextId++;
          worker.__graphitePending.set(id, { success: onSuccess, failure: onFailure });
          worker.postMessage({ id: id, bytes: commands }, [commands.buffer]);
        })(arguments[0], arguments[1], arguments[2], arguments[3])
        """,
    )
}

private fun terminateWorker(worker: JsAny) {
    js(
        """
        (function(worker) {
          for (const pending of worker.__graphitePending.values()) {
            pending.failure('recorder Worker was closed');
          }
          worker.__graphitePending.clear();
          worker.terminate();
        })(arguments[0])
        """,
    )
}
