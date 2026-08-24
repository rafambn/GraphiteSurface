@file:OptIn(kotlin.js.ExperimentalWasmJsInterop::class)

package com.rafambn.graphitesurface

import kotlin.js.JsAny
import kotlin.js.js
import kotlin.js.unsafeCast
import org.khronos.webgl.Int8Array
import org.khronos.webgl.get

internal actual class WebValidationWorker actual constructor(index: Int) {
    private val worker: JsAny = createWorker(index)

    internal actual fun process(
        commands: ByteArray,
        onSuccess: (ByteArray) -> Unit,
        onFailure: (String) -> Unit,
    ) {
        val transferable = Int8Array(commands.size)
        commands.forEachIndexed { index, value -> setInt8(transferable, index, value) }
        postToWorker(
            worker = worker,
            commands = transferable,
            onSuccess = { value ->
                val result = value.unsafeCast<Int8Array>()
                onSuccess(ByteArray(result.length) { result[it] })
            },
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
        const validate = function(bytes, depth) {
          if (!(bytes instanceof Int8Array) && !(bytes instanceof Uint8Array)) {
            throw new Error('Graphite commands are not a byte array');
          }
          if (depth <= 0) throw new Error('Graphite display-list nesting is too deep');
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
              if (payloadSize < 4) throw new Error('truncated Graphite display list');
              const nestedSize = readInt(payloadOffset);
              if (nestedSize < 0 || nestedSize + 4 !== payloadSize) {
                throw new Error('invalid Graphite display-list payload');
              }
              validate(new Int8Array(bytes.buffer, bytes.byteOffset + payloadOffset + 4, nestedSize), depth - 1);
            }
            offset = nextOffset;
          }
          if (saveDepth !== 0) throw new Error('unbalanced Graphite save and restore commands');
        };
        self.onmessage = function(event) {
          const id = event.data.id;
          try {
            const bytes = event.data.bytes;
            validate(bytes, 64);
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
    })(index)
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
        })(worker, commands, onSuccess, onFailure)
        """,
    )
}

@JsFun("(array, index, value) => { array[index] = value; }")
private external fun setInt8(array: Int8Array, index: Int, value: Byte)

private fun terminateWorker(worker: JsAny) {
    js(
        """
        for (const pending of worker.__graphitePending.values()) {
          pending.failure('recorder Worker was closed');
        }
        worker.__graphitePending.clear();
        worker.terminate();
        """,
    )
}
