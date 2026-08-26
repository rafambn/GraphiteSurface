#include <atomic>
#include <cstdint>
#include <cstdio>
#include <memory>
#include <pthread.h>

#include <emscripten.h>
#include <emscripten/html5_webgpu.h>
#include <emscripten/threading.h>
#include <webgpu/webgpu.h>
#include <webgpu/webgpu_cpp.h>

#include "include/core/SkCanvas.h"
#include "include/core/SkColor.h"
#include "include/core/SkColorSpace.h"
#include "include/core/SkImageInfo.h"
#include "include/core/SkPaint.h"
#include "include/gpu/graphite/BackendTexture.h"
#include "include/gpu/graphite/Context.h"
#include "include/gpu/graphite/ContextOptions.h"
#include "include/gpu/graphite/Recorder.h"
#include "include/gpu/graphite/Recording.h"
#include "include/gpu/graphite/Surface.h"
#include "include/gpu/graphite/dawn/DawnBackendContext.h"
#include "include/gpu/graphite/dawn/DawnGraphiteTypes.h"

namespace {

constexpr int kWidth = 960;
constexpr int kHeight = 540;
constexpr int kRecorderCount = 2;

enum Status : int {
  kIdle = 0,
  kRequestingDevice = 1,
  kContextReady = 2,
  kRecording = 3,
  kPresented = 4,
  kDeviceInitializationFailed = -1,
  kContextCreationFailed = -2,
  kPresentationTargetFailed = -3,
  kRecorderCreationFailed = -4,
  kThreadCreationFailed = -5,
  kRecorderWorkFailed = -6,
  kInsertRecordingFailed = -7,
  kSubmitFailed = -8,
};

struct RecorderJob {
  int index = 0;
  std::unique_ptr<skgpu::graphite::Recorder> recorder;
  std::unique_ptr<skgpu::graphite::Recording> recording;
  skgpu::graphite::TextureInfo textureInfo;
  std::atomic<int> state{0};
  std::atomic<int> localDeviceHandle{0};
  double startedAt = 0.0;
  double finishedAt = 0.0;
};

std::atomic<int> status{kIdle};
std::atomic<int> deviceError{0};
std::atomic<int> completedRecorders{0};
std::atomic<std::uintptr_t> deviceHandle{0};
std::unique_ptr<skgpu::graphite::Context> context;
std::unique_ptr<skgpu::graphite::Recorder> presentationRecorder;
sk_sp<SkSurface> presentationSurface;
WGPUTexture presentationTexture = nullptr;
RecorderJob jobs[kRecorderCount];
pthread_t recorderThreads[kRecorderCount];

EM_JS(void, requestDevice, (), {
  navigator.gpu.requestAdapter()
      .then((adapter) => {
        if (!adapter) throw new Error('No WebGPU adapter');
        return adapter.requestDevice();
      })
      .then((device) => {
        const canvas = document.getElementById('graphite-canvas');
        const canvasContext = canvas.getContext('webgpu');
        if (!canvasContext) throw new Error('No WebGPU canvas context');
        canvasContext.configure({
          device,
          format: 'bgra8unorm',
          alphaMode: 'premultiplied',
        });
        device.addEventListener('uncapturederror', (event) => {
          console.error(
              '[graphite emdawn gate] uncaptured WebGPU error',
              event.error?.message ?? String(event.error));
          _graphite_emdawn_device_error();
        });
        device.lost.then((info) => {
          console.error('[graphite emdawn gate] device lost', info);
          _graphite_emdawn_device_error();
        });
        Module['preinitializedWebGPUDevice'] = device;
        Module['graphiteEmdawnDevice'] = device;
        Module['graphiteEmdawnCanvasContext'] = canvasContext;
        _graphite_emdawn_device_ready();
      })
      .catch((error) => {
        console.error('[graphite emdawn gate] device request failed', error);
        _graphite_emdawn_device_failed();
      });
});

EM_JS(std::uintptr_t, importCurrentTexture, (std::uintptr_t devicePtr), {
  const canvasContext = Module['graphiteEmdawnCanvasContext'];
  if (!canvasContext) return 0;
  const texture = canvasContext.getCurrentTexture();
  if (WebGPU.importJsTexture) {
    return WebGPU.importJsTexture(texture, devicePtr);
  }
  return WebGPU.mgrTexture.create(texture);
});

EM_JS(int, hasLocalJsDevice, (std::uintptr_t devicePtr), {
  const objects = WebGPU.Internals?.jsObjects ?? WebGPU.mgrDevice?.objects;
  return Number(Boolean(objects?.[devicePtr]));
});

EM_JS(void, waitForSubmittedWorkDone, (), {
  Module['graphiteEmdawnDevice'].queue.onSubmittedWorkDone()
      .then(() => _graphite_emdawn_submit_complete())
      .catch((error) => {
        console.error(
            '[graphite emdawn gate] queue completion failed',
            error?.message ?? String(error));
        _graphite_emdawn_device_error();
      });
});

void fail(Status failure) {
  int current = status.load(std::memory_order_acquire);
  while (current >= 0 &&
         !status.compare_exchange_weak(current, failure, std::memory_order_acq_rel)) {
  }
}

void presentWhenComplete(int) {
  if (status.load(std::memory_order_acquire) < 0) {
    return;
  }

  const int completed = completedRecorders.fetch_add(1, std::memory_order_acq_rel) + 1;
  if (completed != kRecorderCount) {
    return;
  }

  for (const auto& job : jobs) {
    if (job.state.load(std::memory_order_acquire) != 2 || !job.recording) {
      fail(kRecorderWorkFailed);
      return;
    }
  }

  presentationTexture = reinterpret_cast<WGPUTexture>(importCurrentTexture(
      deviceHandle.load(std::memory_order_acquire)));
  if (!presentationTexture) {
    fail(kPresentationTargetFailed);
    return;
  }
  auto backendTexture = skgpu::graphite::BackendTextures::MakeDawn(presentationTexture);
  presentationSurface = SkSurfaces::WrapBackendTexture(
      presentationRecorder.get(),
      backendTexture,
      SkColorSpace::MakeSRGB(),
      nullptr);
  if (!presentationSurface) {
    fail(kPresentationTargetFailed);
    return;
  }

  presentationSurface->getCanvas()->clear(SK_ColorBLACK);
  auto clearRecording = presentationRecorder->snap();
  if (!clearRecording || !context->insertRecording({clearRecording.get()})) {
    fail(kInsertRecordingFailed);
    return;
  }

  for (const auto& job : jobs) {
    if (!context->insertRecording({job.recording.get(), presentationSurface.get()})) {
      fail(kInsertRecordingFailed);
      return;
    }
  }

  if (!context->submit(skgpu::graphite::SubmitInfo(skgpu::graphite::SyncToCpu::kNo))) {
    fail(kSubmitFailed);
    return;
  }
  waitForSubmittedWorkDone();
}

void* recordOnPthread(void* rawIndex) {
  const int index = static_cast<int>(reinterpret_cast<std::intptr_t>(rawIndex));
  auto& job = jobs[index];
  job.startedAt = emscripten_get_now();
  job.state.store(1, std::memory_order_release);

  const auto rawDevice = deviceHandle.load(std::memory_order_acquire);
  const bool localHandleExists = hasLocalJsDevice(rawDevice) != 0;
  job.localDeviceHandle.store(
      localHandleExists ? 1 : -1,
      std::memory_order_release);
  std::printf(
      "[graphite emdawn gate] recorder=%d device=%zu local-js-object=%s\n",
      index,
      rawDevice,
      localHandleExists ? "yes" : "no");

#if !defined(EMDAWN_PROXY_ENABLED)
  if (!localHandleExists) {
    job.state.store(-1, std::memory_order_release);
    fail(kRecorderWorkFailed);
    emscripten_async_run_in_main_runtime_thread(
        EM_FUNC_SIG_VI, presentWhenComplete, index);
    return nullptr;
  }
#endif

  const auto imageInfo = SkImageInfo::Make(
      kWidth,
      kHeight,
      kRGBA_8888_SkColorType,
      kPremul_SkAlphaType,
      SkColorSpace::MakeSRGB());
  SkCanvas* canvas = job.recorder->makeDeferredCanvas(imageInfo, job.textureInfo);
  if (!canvas) {
    job.state.store(-1, std::memory_order_release);
    fail(kRecorderWorkFailed);
    emscripten_async_run_in_main_runtime_thread(
        EM_FUNC_SIG_VI, presentWhenComplete, index);
    return nullptr;
  }

  SkPaint paint;
  paint.setAntiAlias(true);
  paint.setColor(index == 0 ? SK_ColorBLUE : SK_ColorGREEN);
  for (int i = 0; i < 20000; ++i) {
    const float x = static_cast<float>((i * 17 + index * 41) % kWidth);
    const float y = static_cast<float>((i * 29 + index * 13) % kHeight);
    canvas->drawCircle(x, y, 2.0f, paint);
  }

  job.recording = job.recorder->snap();
  job.finishedAt = emscripten_get_now();
  job.state.store(job.recording ? 2 : -1, std::memory_order_release);
  emscripten_async_run_in_main_runtime_thread(
      EM_FUNC_SIG_VI, presentWhenComplete, index);
  return nullptr;
}

void startRecorders(const skgpu::graphite::TextureInfo& textureInfo) {
  for (int index = 0; index < kRecorderCount; ++index) {
    auto& job = jobs[index];
    job.index = index;
    job.textureInfo = textureInfo;
    job.recorder = context->makeRecorder();
    if (!job.recorder) {
      fail(kRecorderCreationFailed);
      return;
    }
  }

  status.store(kRecording, std::memory_order_release);
  for (int index = 0; index < kRecorderCount; ++index) {
    const int result = pthread_create(
        &recorderThreads[index],
        nullptr,
        recordOnPthread,
        reinterpret_cast<void*>(static_cast<std::intptr_t>(index)));
    if (result != 0) {
      fail(kThreadCreationFailed);
      return;
    }
  }
}

}  // namespace

extern "C" {

EMSCRIPTEN_KEEPALIVE void graphite_emdawn_device_ready() {
  WGPUDevice rawDevice = emscripten_webgpu_get_device();
  if (!rawDevice) {
    fail(kDeviceInitializationFailed);
    return;
  }
  deviceHandle.store(
      reinterpret_cast<std::uintptr_t>(rawDevice),
      std::memory_order_release);

  skgpu::graphite::DawnBackendContext backendContext{};
  backendContext.fDevice = wgpu::Device::Acquire(rawDevice);
  backendContext.fQueue = backendContext.fDevice.GetQueue();
  context = skgpu::graphite::ContextFactory::MakeDawn(backendContext, {});
  if (!context) {
    fail(kContextCreationFailed);
    return;
  }
  status.store(kContextReady, std::memory_order_release);

  presentationRecorder = context->makeRecorder();
  if (!presentationRecorder) {
    fail(kPresentationTargetFailed);
    return;
  }

  startRecorders(skgpu::graphite::TextureInfos::MakeDawn(
      skgpu::graphite::DawnTextureInfo(
          skgpu::graphite::SampleCount::k1,
          skgpu::Mipmapped::kNo,
          wgpu::TextureFormat::BGRA8Unorm,
          wgpu::TextureUsage::RenderAttachment,
          wgpu::TextureAspect::All)));
}

EMSCRIPTEN_KEEPALIVE void graphite_emdawn_submit_complete() {
  if (status.load(std::memory_order_acquire) >= 0 &&
      deviceError.load(std::memory_order_acquire) == 0) {
    status.store(kPresented, std::memory_order_release);
    std::printf("[graphite emdawn gate] presented two native recordings\n");
  }
}

EMSCRIPTEN_KEEPALIVE void graphite_emdawn_device_failed() {
  deviceError.store(1, std::memory_order_release);
  fail(kDeviceInitializationFailed);
}

EMSCRIPTEN_KEEPALIVE void graphite_emdawn_device_error() {
  deviceError.store(2, std::memory_order_release);
  fail(kDeviceInitializationFailed);
}

EMSCRIPTEN_KEEPALIVE int graphite_emdawn_status() {
  return status.load(std::memory_order_acquire);
}

EMSCRIPTEN_KEEPALIVE int graphite_emdawn_device_error_code() {
  return deviceError.load(std::memory_order_acquire);
}

EMSCRIPTEN_KEEPALIVE int graphite_emdawn_recorder_state(int index) {
  return index >= 0 && index < kRecorderCount
      ? jobs[index].state.load(std::memory_order_acquire)
      : -2;
}

EMSCRIPTEN_KEEPALIVE int graphite_emdawn_recorder_local_handle(int index) {
  return index >= 0 && index < kRecorderCount
      ? jobs[index].localDeviceHandle.load(std::memory_order_acquire)
      : 0;
}

EMSCRIPTEN_KEEPALIVE double graphite_emdawn_recorder_started(int index) {
  return index >= 0 && index < kRecorderCount ? jobs[index].startedAt : 0.0;
}

EMSCRIPTEN_KEEPALIVE double graphite_emdawn_recorder_finished(int index) {
  return index >= 0 && index < kRecorderCount ? jobs[index].finishedAt : 0.0;
}

}  // extern "C"

int main() {
  status.store(kRequestingDevice, std::memory_order_release);
  requestDevice();
  emscripten_exit_with_live_runtime();
  return 0;
}
