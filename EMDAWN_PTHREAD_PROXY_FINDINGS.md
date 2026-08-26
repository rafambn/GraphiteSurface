# Emdawn, WebGPU e Graphite em pthreads

Este documento registra por que um `WGPUDevice` criado por Emdawn não funciona
diretamente em vários pthreads e qual solução prática existe hoje.

## Resumo

O heap Wasm compartilhado permite mover handles C++ de Graphite entre pthreads.
Ele não compartilha objetos JavaScript. Cada Worker mantém seu próprio realm e,
portanto, sua própria tabela `WebGPU.Internals.jsObjects`.

A solução prática é manter um único dono JavaScript do `GPUDevice` e executar
nele todas as chamadas do binding WebGPU. Os pthreads continuam executando o
trabalho nativo de cada `Recorder` em paralelo. Quando Graphite chama uma função
`wgpu*`, o Emscripten envia essa chamada para a thread dona, espera a resposta e
devolve o handle nativo ao pthread.

```text
pthread Recorder 0 -- trabalho Graphite --\
                                          +--> proxy síncrono --> GPU owner
pthread Recorder 1 -- trabalho Graphite --/                       - GPUDevice JS
                                                                  - Emdawn jsObjects
                                                                  - Context
                                                                  - canvas
```

Isso não elimina `-pthread`, `SharedArrayBuffer`, COOP ou COEP. Também não cria
um único binário com fallback. O proxy resolve a propriedade dos objetos
JavaScript, não as exigências operacionais de pthreads no navegador.

## Evidência encontrada

### O WebGPU ainda não compartilha um device entre Workers

O WebGPU Explainer diz que um único `GPUDevice` ainda não aceita uso
multithread. A seção "Unsolved: Synchronous Object Transfer" cita diretamente
programas C, C++ e Rust compilados para Wasm. Esses programas tratam handles
como dados comuns, mas o binding JavaScript não consegue materializar o objeto
correspondente em outro Worker de forma síncrona.

Fonte:
<https://gpuweb.github.io/gpuweb/explainer/#multithreading-transfer>

### O Emscripten já implementou o workaround

O PR 20124, incorporado ao Emscripten em 19 de setembro de 2023, adicionou
`__proxy: "sync"` a todas as funções do binding WebGPU. O autor descreveu isso
como a melhor opção enquanto WebGPU não tivesse suporte multithread real.

Fonte:
<https://github.com/emscripten-core/emscripten/pull/20124>

O patch central era:

```javascript
for (const key of Object.keys(LibraryWebGPU)) {
    if (
        typeof LibraryWebGPU[key] === "function" &&
        !(key + "__proxy" in LibraryWebGPU)
    ) {
        LibraryWebGPU[key + "__proxy"] = "sync";
    }
}
```

O Emscripten documenta que uma função de biblioteca marcada com
`__proxy: "sync"` bloqueia somente o pthread chamador. A main runtime thread
executa a função e devolve seu resultado.

Fonte:
<https://emscripten.org/docs/porting/pthreads.html#proxying>

### O Emdawn atual não preservou o workaround

O binding antigo `-sUSE_WEBGPU` saiu do Emscripten. O Emdawn mantido no Dawn o
substituiu. A implementação atual ainda guarda os objetos em
`WebGPU.Internals.jsObjects`, mas não marca suas funções com `__proxy`.

Fonte:
<https://github.com/google/dawn/blob/main/third_party/emdawnwebgpu/pkg/webgpu/src/library_webgpu.js>

A opção `shared_memory=true` do port apenas compila sua camada C++ com memória
compartilhada e operações atômicas. Ela não compartilha o array JavaScript nem
redireciona chamadas para seu dono.

Fonte:
<https://github.com/google/dawn/blob/main/src/emdawnwebgpu/pkg/emdawnwebgpu.port.py>

### O Chrome não enfrenta a mesma fronteira

O Graphite usado internamente pelo Chromium chama Dawn nativo. As threads
compartilham objetos C++ e o Chromium cria recorders distintos para a GPU main
thread e para a Viz compositor thread. `GraphiteSharedContext` sincroniza o
acesso ao Context.

Fontes:

- <https://chromium.googlesource.com/chromium/src/+/refs/heads/main/gpu/command_buffer/service/shared_context_state.cc>
- <https://chromium.googlesource.com/chromium/src/+/refs/heads/main/gpu/command_buffer/service/graphite_shared_context.cc>

Entre o processo renderer e o processo GPU, Chromium usa Dawn Wire. O cliente
serializa comandos e o servidor os valida e executa. Isso confirma a mesma
ideia: objetos GPU têm um dono; outras threads ou processos enviam handles e
comandos ao dono.

Fonte:
<https://github.com/google/dawn/blob/main/docs/dawn/overview.md>

## Arquitetura proposta para GraphiteSurface Web

A main runtime thread do módulo Emscripten deve ser a dona de:

- `GPUDevice` e `GPUQueue` JavaScript;
- `WebGPU.Internals.jsObjects`;
- `GPUCanvasContext` ou `OffscreenCanvas`;
- Graphite `Context` e apresentação.

Ela deve iniciar o pool de pthreads. Cada pthread recebe seu próprio Graphite
`Recorder`, produz uma `Recording` no heap Wasm compartilhado e publica esse
objeto para inserção no Context.

A thread dona não pode fazer `pthread_join`, esperar em uma condition variable
ou bloquear até os recorders terminarem. Um recorder pode estar bloqueado
esperando uma chamada WebGPU enviada por proxy para essa mesma thread. O dono
precisa continuar processando seu event loop e a fila de proxy.

O melhor arranjo coloca a main runtime thread em um Dedicated Worker:

```text
browser UI
    |
    v
Dedicated Worker, main runtime thread
    - GPUDevice e tabela Emdawn
    - Graphite Context
    - OffscreenCanvas
    |
    +-- pthread: Recorder 0
    +-- pthread: Recorder 1
```

Se o módulo continuar iniciado na UI, o mecanismo também funciona, mas todas
as chamadas WebGPU proxied passam pela UI e podem causar jank.

## Limites e caminho de teste

O patch original cobria o binding antigo. O Emdawn atual acrescentou Futures,
callbacks e funções Asyncify. Portanto, o mesmo mecanismo precisa passar por
um gate antes de entrar no runtime:

1. criar e importar o device na main runtime thread;
2. marcar as funções Emdawn com `__proxy: "sync"`;
3. criar dois pthreads;
4. criar e liberar um `WGPUBuffer` em cada pthread;
5. executar o gate Graphite com dois `Recorder`s;
6. inserir as duas `Recording`s e apresentar;
7. medir sobreposição de CPU, quantidade de chamadas proxied e tempo bloqueado;
8. manter carga por 60 segundos e validar resize, cancelamento e shutdown.

Se o custo de uma chamada síncrona por função WebGPU for alto, o passo seguinte
é um cliente Dawn Wire ou outro protocolo em lote dentro do Wasm. Trocar C++ por
Rust não remove essa fronteira JavaScript.

## Resultado do experimento

Executado em 26 de agosto de 2026 com:

- Emdawn `v20260824.202544`, revisão Dawn `84eeb817`;
- Emscripten `5.0.6` (`6ea9c28c38cdd40c1032fa04400c9d16230ee180`),
  a versão indicada pelo próprio release do Emdawn;
- Google Chrome `151.0.7922.174` no macOS;
- dois pthreads no pool;
- `crossOriginIsolated=true`, `SharedArrayBuffer` disponível e WebGPU ativo.

Foram compilados dois artefatos a partir de `emdawn-handle-gate.cpp`. O
baseline usou o Emdawn original. O segundo usou uma cópia de
`library_webgpu.js` alterada por `patch-emdawn-proxy.ts`, que adiciona
`__proxy: "sync"` às funções do binding.

No baseline, o device existia apenas na tabela JavaScript da thread dona. Os
dois pthreads confirmaram que não tinham o objeto e encerraram antes da chamada
WebGPU:

```text
[emdawn gate] owner handle=78176 local-js-object=yes
[emdawn gate] recorder=1 handle=78176 local-js-object=no
[emdawn gate] recorder=0 handle=78176 local-js-object=no

render=2
recorder0=-1
recorder1=-1
recorder0LocalHandle=-1
recorder1LocalHandle=-1
```

Na variante com proxy, os pthreads continuaram sem o objeto JavaScript local,
mas ambos executaram `wgpuDeviceCreateBuffer` e `wgpuBufferRelease` com sucesso:

```text
[emdawn gate] owner handle=78176 local-js-object=yes
[emdawn gate] recorder=1 handle=78176 local-js-object=no
[emdawn gate] recorder=0 handle=78176 local-js-object=no

render=2
recorder0=3
recorder1=3
recorder0LocalHandle=-1
recorder1LocalHandle=-1
```

O JavaScript gerado confirma que a versão alterada envolve
`emwgpuDeviceCreateBuffer` em `proxyToMainThread`; o baseline não faz isso.

### Conclusão

O workaround é tecnicamente viável no Emdawn atual: um pthread pode usar um
handle WebGPU cujo objeto JavaScript existe somente na main runtime thread,
desde que a chamada do binding seja executada nela por proxy síncrono. O teste
também confirma que a thread dona deve permanecer livre para atender a fila de
proxy.

## Teste real com Graphite

O gate `graphite-emdawn-gate.cpp` foi executado em 26 de agosto de 2026 com o
Skia m152 usado pelo projeto, recompilado com `-pthread`, Emscripten 4.0.7 e
Chrome 151.0.7922.174. Ele cria o `Context` e dois `Recorder`s nativos, executa
20.000 desenhos em cada recorder pthread, transfere as duas `Recording`s para a
thread dona, insere-as em ordem no mesmo alvo, faz `submit()` e aguarda
`GPUQueue.onSubmittedWorkDone()`.

O controle sem proxy confirmou a falha esperada. O handle nativo era o mesmo,
mas nenhum pthread tinha o `GPUDevice` em seu registro JavaScript:

```text
status=-6
recorder0=-1
recorder1=-1
recorder0LocalHandle=-1
recorder1LocalHandle=-1
```

Com `__proxy: "sync"` em todas as funções WebGPU, o mesmo teste passou:

```text
status=4
recorder0=2
recorder1=2
recorder0LocalHandle=-1
recorder1LocalHandle=-1
overlapMs=44.550048828125
deviceError=0
```

A captura final mostrou os círculos azuis e verdes produzidos pelas duas
`Recording`s sobre o fundo preto. Os intervalos de CPU dos recorders se
sobrepuseram por 44,55 ms; as chamadas WebGPU de ambos foram atendidas pela
thread dona. Portanto, o caminho completo de dois recorders nativos até a
apresentação funciona com proxy síncrono.

O gate também encontrou uma regra de apresentação importante: a textura atual
do `GPUCanvasContext` deve ser adquirida somente depois que os recorders
terminarem, imediatamente antes da inserção e do submit. Mantê-la durante o
trabalho dos pthreads permitia que o ciclo de composição do navegador a
invalidasse, produzindo `Destroyed texture used in a submit`.

### Limite de compatibilidade encontrado

O teste Graphite completo usa o binding legado `-sUSE_WEBGPU=1`, porque essa é
a ABI contra a qual o Skia m152 do projeto foi compilado. O binding foi
executado com o mesmo patch de proxy síncrono. O Emdawn atual usa a API C++ de
Futures (`WGPUMapAsyncStatus`, `CallbackMode`, novos descritores), enquanto o
backend Dawn desse Skia espera a API legada (`WGPUBufferMapAsyncStatus`,
callbacks antigos e `ShaderModuleWGSLDescriptor`). Misturar os dois falha em
compilação ou link e não constitui um teste válido.

Assim, há duas evidências complementares:

- o gate Emdawn atual prova que o proxy resolve o ownership dos handles;
- o gate Graphite real prova que dois `Recorder`s, duas `Recording`s e a
  apresentação funcionam sobre esse modelo no binding usado hoje pelo projeto.

Para migrar o projeto ao Emdawn, será necessário atualizar Skia/Graphite e
Emdawn como um par de ABI compatível. Ainda faltam medir custo por chamada,
executar carga contínua de 60 segundos e validar resize, cancelamento e
shutdown antes de considerar a arquitetura pronta para produção.
