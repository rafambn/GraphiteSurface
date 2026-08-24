#include <atomic>
#include <cstdint>
#include <cstdio>
#include <pthread.h>

#include <emscripten.h>
#include <webgpu/webgpu.h>

namespace {

std::atomic<std::uintptr_t> deviceHandle{0};
std::atomic<int> renderStatus{0};
std::atomic<int> recorderStatuses[2]{{0}, {0}};
pthread_t recorderThreads[2];

EM_JS(void, requestDevice, (), {
  navigator.gpu.requestAdapter()
      .then((adapter) => {
        if (!adapter) throw new Error('No WebGPU adapter');
        return adapter.requestDevice();
      })
      .then((device) => {
        Module['preinitializedWebGPUDevice'] = device;
        _emdawn_device_ready();
      })
      .catch((error) => {
        console.error('[emdawn gate] device request failed', error);
        _emdawn_device_failed();
      });
});

EM_JS(int, hasLocalJsDevice, (std::uintptr_t handle), {
  return Number(Boolean(WebGPU.Internals.jsObjects[handle]));
});

void* recorderMain(void* rawIndex) {
  const auto index = static_cast<int>(reinterpret_cast<std::intptr_t>(rawIndex));
  const auto handle = deviceHandle.load(std::memory_order_acquire);
  auto* device = reinterpret_cast<WGPUDevice>(handle);

  recorderStatuses[index].store(1, std::memory_order_release);
  const bool localHandleExists = hasLocalJsDevice(handle) != 0;
  recorderStatuses[index].store(localHandleExists ? 2 : -1, std::memory_order_release);
  std::printf(
      "[emdawn gate] recorder=%d handle=%zu local-js-object=%s\n",
      index,
      handle,
      localHandleExists ? "yes" : "no");

  if (!localHandleExists) {
    return nullptr;
  }

  WGPUBufferDescriptor descriptor = WGPU_BUFFER_DESCRIPTOR_INIT;
  descriptor.usage = WGPUBufferUsage_CopyDst;
  descriptor.size = 16;
  WGPUBuffer buffer = wgpuDeviceCreateBuffer(device, &descriptor);
  if (buffer == nullptr) {
    recorderStatuses[index].store(-2, std::memory_order_release);
    return nullptr;
  }

  wgpuBufferRelease(buffer);
  recorderStatuses[index].store(3, std::memory_order_release);
  return nullptr;
}

void* renderMain(void*) {
  renderStatus.store(1, std::memory_order_release);
  requestDevice();
  emscripten_exit_with_live_runtime();
  return nullptr;
}

}  // namespace

extern "C" {

EMSCRIPTEN_KEEPALIVE void emdawn_device_ready() {
  WGPUDevice device = emscripten_webgpu_get_device();
  const auto handle = reinterpret_cast<std::uintptr_t>(device);
  deviceHandle.store(handle, std::memory_order_release);
  renderStatus.store(hasLocalJsDevice(handle) ? 2 : -1, std::memory_order_release);
  std::printf("[emdawn gate] render handle=%zu local-js-object=%s\n",
              handle,
              hasLocalJsDevice(handle) ? "yes" : "no");

  for (int index = 0; index < 2; ++index) {
    const int result = pthread_create(
        &recorderThreads[index],
        nullptr,
        recorderMain,
        reinterpret_cast<void*>(static_cast<std::intptr_t>(index)));
    if (result != 0) {
      recorderStatuses[index].store(-3, std::memory_order_release);
    }
  }
}

EMSCRIPTEN_KEEPALIVE void emdawn_device_failed() {
  renderStatus.store(-2, std::memory_order_release);
}

EMSCRIPTEN_KEEPALIVE int emdawn_render_status() {
  return renderStatus.load(std::memory_order_acquire);
}

EMSCRIPTEN_KEEPALIVE int emdawn_recorder_status(int index) {
  if (index < 0 || index > 1) return -4;
  return recorderStatuses[index].load(std::memory_order_acquire);
}

}  // extern "C"

int main() {
  pthread_t renderThread;
  const int result = pthread_create(&renderThread, nullptr, renderMain, nullptr);
  if (result != 0) {
    renderStatus.store(-3, std::memory_order_release);
    return result;
  }
  return 0;
}
