# Migração do GraphiteSurface Web para pthreads

Este documento descreve a migração da implementação Web atual para recorders
nativos do Skia Graphite executados em pthreads. Ele é um plano de execução e
um conjunto de critérios de aceite. Os resultados experimentais que justificam
a arquitetura estão em [EMDAWN_PTHREAD_PROXY_FINDINGS.md](EMDAWN_PTHREAD_PROXY_FINDINGS.md).

## Objetivo

O resultado final deve ter:

- um `GPUDevice` e um `Graphite Context` com dono único;
- um `Recorder` nativo e persistente por worker;
- gravação de CPU realmente paralela em pthreads;
- transferência de `Recording` por handle no heap Wasm compartilhado;
- inserção ordenada das recordings pelo dono do `Context`;
- apresentação sem bloquear o event loop do dono;
- suporte explícito a resize, cancelamento, shutdown e device loss;
- dois artefatos Web: threaded e compatível sem pthreads.

Esta migração não troca a API pública do GraphiteSurface e não coloca o
`Graphite Context` em vários threads. O paralelismo ocorre somente durante a
gravação de cada `Recorder`.

## Estado atual

### JVM, Android e iOS

Essas plataformas já usam um `Recorder` nativo do Skia Graphite por worker.
Cada worker produz uma `Recording` nativa. O thread de apresentação insere as
recordings no `Context` na ordem declarada pelo frame.

### JavaScript e WasmJS

O Web ainda usa `WebValidationWorker`. O worker recebe os comandos
transferíveis, valida o protocolo e devolve uma gravação sem handle nativo. A
apresentação reproduz os comandos no recorder dono do frame.

```text
estado Web atual

Kotlin render callback
    |
    +-- Web Worker 0: valida command buffer --+
    +-- Web Worker 1: valida command buffer --+--> replay no owner --> submit
```

Esse caminho continua sendo o fallback de compatibilidade até a variante
pthread passar por todos os gates deste documento.

### Protótipo pthread validado

O gate real já provou a topologia essencial:

```text
owner Emscripten
    - GPUDevice e GPUQueue JavaScript
    - tabela WebGPU.Internals.jsObjects
    - Graphite Context
    - Recorder de apresentação
    |
    +-- pthread 0: Recorder 0 --> Recording 0 --+
    +-- pthread 1: Recorder 1 --> Recording 1 --+--> owner --> insert --> submit
```

Cada pthread desenhou 20.000 círculos com seu próprio `Recorder`. As duas
janelas de CPU se sobrepuseram por 44,55 ms. O owner inseriu as duas
`Recording`s, apresentou a textura e aguardou `GPUQueue.onSubmittedWorkDone()`.
O resultado visual continha as gravações azul e verde e não houve erro de
device.

O gate também demonstrou que o handle nativo do device é visível no heap
compartilhado, mas o objeto JavaScript correspondente existe somente no realm
do owner. Marcar as funções do binding WebGPU com `__proxy: "sync"` faz o
pthread enviar cada chamada ao owner e aguardar a resposta.

## Por que o proxy é necessário

`SharedArrayBuffer` compartilha o heap Wasm. Ele não compartilha objetos
JavaScript entre Workers. Cada Worker possui seu próprio realm e sua própria
tabela de objetos do binding WebGPU.

Sem proxy:

```text
pthread
    WGPUDevice = 78176
    WebGPU.Internals.jsObjects[78176] = ausente
    wgpuDeviceCreateBuffer(...) = falha
```

Com proxy síncrono:

```text
pthread
    wgpuDeviceCreateBuffer(...)
        |
        +-- proxy --> owner
                         WebGPU.Internals.jsObjects[78176] = GPUDevice
                         executa a chamada
        <-- resultado --+
```

Somente o pthread chamador fica bloqueado. O owner precisa permanecer livre
para consumir a fila de proxy. Por isso, o owner nunca pode fazer
`pthread_join`, esperar em condition variable ou executar espera síncrona por
um recorder.

## Arquitetura de produção

O módulo Emscripten deve ser iniciado em um Dedicated Worker. Esse Worker é a
main runtime thread do Emscripten e o owner de todos os objetos JavaScript do
WebGPU.

```text
browser UI
    - Compose/Kotlin UI
    - eventos de input
    - transfere OffscreenCanvas uma vez
    |
    | mensagens assíncronas
    v
Dedicated Worker, main runtime thread
    - módulo Emscripten
    - OffscreenCanvas e GPUCanvasContext
    - GPUAdapter, GPUDevice e GPUQueue
    - tabela de handles Emdawn
    - Graphite Context
    - Recorder de apresentação
    - aquisição da textura atual
    - inserção, submit e apresentação
    |
    +-- pthread 0
    |     - Recorder 0
    |     - fila de jobs 0
    |     - Recording resultante
    |
    +-- pthread 1
          - Recorder 1
          - fila de jobs 1
          - Recording resultante
```

Colocar o owner no Dedicated Worker evita que chamadas WebGPU proxied parem a
UI do navegador. Os pthreads são filhos dessa main runtime thread. O pool deve
ter uma entrada por recorder. O Dedicated Worker owner não pertence ao pool.

O protótipo antigo inicia o runtime na página e cria um pthread adicional para
render. Nesse arranjo experimental, três threads exigem
`PTHREAD_POOL_SIZE=3`. Na arquitetura de produção, o próprio Dedicated Worker
é o owner e somente os dois recorders pertencem ao pool, portanto
`PTHREAD_POOL_SIZE=2`.

## Invariantes de ownership

Estas regras não podem depender de convenção informal:

1. O `Context` é criado, usado e destruído somente pelo owner.
2. O `GPUCanvasContext` e a `OffscreenCanvas` são usados somente pelo owner.
3. Cada `Recorder` pertence a exatamente um pthread durante toda a sua vida.
4. Um job é gravado pelo pthread que possui seu `Recorder`.
5. A `Recording` pronta pode ser publicada ao owner porque está no heap Wasm
   compartilhado.
6. Somente o owner chama `Context::insertRecording()` e `Context::submit()`.
7. A ordem de inserção é a ordem do frame, não a ordem de término dos jobs.
8. A textura atual do canvas só é adquirida imediatamente antes da
   apresentação.
9. O owner nunca espera de forma bloqueante por um pthread.
10. Nenhum objeto nativo é destruído enquanto um job ainda pode referenciá-lo.

## Matriz de versões

O Skia, Dawn/Emdawn e Emscripten formam uma única unidade de compatibilidade.
Não é válido atualizar somente um componente.

| Componente | Gate já executado | Migração de produção |
| --- | --- | --- |
| Skia | `m152-7bb45c7c26` | escolher revisão com ABI Emdawn compatível |
| Dawn do Skia | `1e897275172a23f27b0022fa6beae3084ed54a9b` | usar exatamente a revisão exigida pelo Skia escolhido |
| Binding do gate Graphite | legado `-sUSE_WEBGPU=1` | Emdawn da mesma geração do Skia |
| Gate Emdawn isolado | `v20260824.202544`, Dawn `84eeb817` | manter o par definido pelo release |
| Emscripten do gate Graphite | `4.0.7` | versão exigida pelo Skia e pelo Emdawn escolhidos |
| Emscripten do gate Emdawn | `5.0.6` | mesma versão em todos os módulos linkados |

O Skia m152 espera callbacks e descritores da ABI WebGPU legada. O Emdawn
atual usa a API de Futures. Misturar essas gerações falha em compilação, link
ou execução. A primeira decisão da migração é escolher uma revisão de Skia que
já consuma a ABI do Emdawn selecionado, ou portar o backend Dawn do Skia e
manter esse port dentro do fork.

Registre as revisões completas no version catalog e no CI. Tags móveis ou a
branch `main` não são entradas reproduzíveis.

## Flags de compilação

Todos os objetos estáticos, o main module, os side modules e o binding WebGPU
devem ser compilados com o mesmo modelo de memória.

Flags mínimas da variante threaded:

```text
-pthread
-sPTHREAD_POOL_SIZE=2
-sALLOW_BLOCKING_ON_MAIN_THREAD=0
-sOFFSCREENCANVAS_SUPPORT=1
-sENVIRONMENT=web,worker
-sNO_EXIT_RUNTIME=1
```

Quando o runtime continuar dividido entre core e Graphite:

```text
main module: -sMAIN_MODULE=2
side module: -sSIDE_MODULE=2
loader:      -sAUTOLOAD_DYLIBS=0
```

`-pthread` habilita memória compartilhada, atomics e bulk memory. Uma
biblioteca Skia compilada sem essas features não pode ser linkada no artefato
threaded. O patch experimental
`graphite-surface/experiments/wasm-pthreads/skia-pthreads.patch` mostra os
pontos do build atual que precisam receber as flags, mas deve ser refeito e
revisado para a nova revisão de Skia.

Use `-sASSERTIONS=1` nos gates e em builds de diagnóstico. O artefato de
produção pode removê-lo depois que o CI cobrir os casos de falha.

## Build Emdawn com proxy

O port Emdawn precisa ser compilado com memória compartilhada:

```text
--use-port=/caminho/emdawnwebgpu.port.py:shared_memory=true
```

Antes do build, uma cópia controlada de `library_webgpu.js` recebe
`__proxy: "sync"` nas funções do binding. O protótipo automatiza isso com:

```shell
bun run graphite-surface/experiments/wasm-pthreads/patch-emdawn-proxy.ts \
  /caminho/emdawnwebgpu_pkg/webgpu/src/library_webgpu.js
```

Não altere uma instalação global do emsdk ou um checkout compartilhado. O CI
deve criar uma cópia versionada ou temporária do pacote, aplicar o patch e
falhar se o marcador esperado não existir. Isso transforma mudanças internas
do Emdawn em falhas visíveis, em vez de gerar um binding parcialmente
alterado.

O patch atual adiciona proxy a toda função que ainda não declara uma política.
Antes da produção, classifique as funções em três grupos:

- funções que tocam handles JavaScript e precisam do owner;
- funções puramente locais que não precisam de proxy;
- callbacks, Futures e Asyncify que exigem um gate específico.

Começar com proxy em todas as funções é o caminho correto para validar
corretude. Reduzir o conjunto só deve ocorrer depois de medir chamadas e tempo
bloqueado.

## Ordem de inicialização

A inicialização deve ser assíncrona e seguir esta ordem:

1. A página verifica `crossOriginIsolated`, `SharedArrayBuffer`, WebGPU,
   `Worker` e `OffscreenCanvas`.
2. A UI cria o Dedicated Worker.
3. A UI chama `transferControlToOffscreen()` uma única vez e envia o canvas.
4. O Dedicated Worker carrega o main module Emscripten.
5. O owner carrega o side module Graphite com `emscripten_dlopen()`.
6. O owner solicita `GPUAdapter` e `GPUDevice`.
7. O owner configura o `GPUCanvasContext`.
8. O owner cria o `Graphite Context` e o recorder de apresentação.
9. O owner cria todos os `Recorder`s de worker.
10. Somente depois disso o runtime cria ou libera os pthreads do pool para
    executar jobs.
11. O Worker envia `Ready` à UI.

Carregar o side module antes do primeiro job evita que um pthread provoque um
`dlopen` tardio enquanto o owner atende uma chamada proxied. O loader deve
tratar a reentrada do runtime durante a configuração como erro explícito, não
como pedido para criar um segundo módulo.

## Modelo de jobs

Um job de recorder contém somente dados que podem viver no heap compartilhado
ou ser copiados para ele:

```text
RecorderJob
    jobId
    frameId
    recorderIndex
    surfaceGeneration
    pixelWidth
    pixelHeight
    commandBufferPointer
    commandBufferSize
    resourceTablePointer
    state
    recordingHandle
    errorCode
    startedAt
    finishedAt
```

Estados mínimos:

```text
Queued -> Recording -> Ready
                  \-> Failed
       \-----------> Cancelled
```

O owner publica o job na fila do recorder e acorda o pthread. O pthread
compila os comandos para o seu `Recorder`, chama `snap()` e publica o handle da
`Recording` com store release. O owner observa o estado com load acquire e
agenda a continuação do frame no event loop.

Não use `pthread_join` por frame. Os pthreads são persistentes e recebem vários
jobs durante a vida do runtime.

## Integração Kotlin

`PlatformRecorderWorker.web.kt` deve deixar de criar `WebValidationWorker` na
variante threaded. Ele passa a ser um adaptador para o pool nativo:

```text
GraphiteRecorder.record { ... }
    -> compila DSL para GraphiteCommandBuffer
    -> publica recursos imutáveis
    -> envia job ao recorder pthread
    -> suspende sem bloquear o owner
    -> recebe PlatformRecording(nativeHandle)
```

`PlatformRecording.web.kt` passa a possuir:

- handle opaco da `Recording` nativa;
- geração da surface para impedir uso após recreate;
- ownership explícito para liberação;
- metadados necessários para diagnóstico.

O caminho público continua igual para Kotlin/JS e Kotlin/Wasm. A implementação
de interop difere, mas ambas chamam a mesma ABI C exportada pelo módulo
Graphite. Mantenha os comandos como fronteira de entrada: a DSL Kotlin compila
para dados, e somente o pthread toca o `Recorder` nativo.

O fallback sem pthreads continua usando o worker de comandos atual. Não tente
esconder os dois modelos dentro do mesmo binário Wasm. O Emscripten exige
artefatos diferentes porque a memória compartilhada é definida no momento de
compilação e instanciação.

## Montagem e apresentação do frame

O frame guarda inserções em ordem. Cada inserção pode ser:

- uma `Recording` nativa produzida por pthread;
- uma gravação fallback produzida pelo caminho sem pthreads;
- comandos do recorder de apresentação.

Na variante pthread de produção, a sequência é:

1. aguardar todos os jobs do frame de forma assíncrona;
2. rejeitar resultados cuja `surfaceGeneration` não seja a atual;
3. adquirir `GPUCanvasContext.getCurrentTexture()`;
4. envolver a textura em `BackendTexture` e `SkSurface`;
5. fazer `snap()` de comandos pendentes do recorder de apresentação em cada
   fronteira de inserção;
6. inserir cada `Recording` na ordem do frame;
7. inserir o `snap()` final, se houver comandos pendentes;
8. chamar `Context::submit()`;
9. liberar os objetos transitórios da textura;
10. sinalizar conclusão após a política de apresentação escolhida.

A textura atual não pode ser adquirida antes dos recorders terminarem. O gate
real reproduziu `Destroyed texture used in a submit` quando a textura ficou
retida durante o trabalho dos pthreads.

## Resize

Resize cria uma nova geração de apresentação:

1. a UI envia o novo tamanho físico e um número de geração crescente;
2. o owner atualiza as dimensões do canvas;
3. jobs novos recebem a nova geração;
4. jobs antigos podem terminar, mas seus resultados são descartados;
5. o owner não reutiliza `SkSurface`, `BackendTexture` nem textura atual da
   geração anterior;
6. cada recorder permanece vivo, desde que a mudança não exija recriar o
   `Context`.

O tamanho do job deve ser capturado no início da gravação. Ler dimensões
globais no meio do job produz recordings incoerentes.

Gate obrigatório: alternar continuamente entre dois tamanhos enquanto os dois
recorders trabalham, sem validation error, use-after-free ou frame com geração
misturada.

## Cancelamento e backpressure

Cancelar uma coroutine Kotlin não pode liberar imediatamente memória que um
pthread ainda usa.

Política recomendada:

- job ainda `Queued`: remover da fila e publicar `Cancelled`;
- job `Recording`: marcar cancelamento solicitado, deixar `record()` e
  `snap()` chegarem a um ponto seguro, descartar a `Recording` e então publicar
  `Cancelled`;
- frame substituído por outro: descartar seus resultados no owner;
- fila cheia: manter no máximo um job pendente por recorder e substituir o
  pendente mais antigo no modo contínuo;
- modo on-demand: preservar todos os frames aceitos ou devolver erro de
  backpressure, conforme contrato público.

O owner não espera cancelamento de forma síncrona. Ele recebe a confirmação
por evento.

## Shutdown

Estados do runtime:

```text
Starting -> Ready -> Closing -> Closed
                    \-------> Failed
```

Sequência de shutdown:

1. parar de aceitar frames;
2. cancelar jobs enfileirados;
3. solicitar encerramento dos jobs em execução;
4. continuar atendendo a fila de proxy;
5. receber confirmação assíncrona de cada pthread;
6. destruir cada `Recording` restante no owner ou no thread definido pela ABI;
7. destruir os `Recorder`s;
8. destruir surface, backend texture transitória e recorder de apresentação;
9. destruir o `Context`;
10. liberar queue e device;
11. terminar os pthreads;
12. enviar `Closed` à UI e terminar o Dedicated Worker.

O shutdown tem timeout observável. Um timeout muda o runtime para `Failed` e
registra quais jobs e pthreads não confirmaram encerramento. Não libere o heap
Wasm enquanto qualquer pthread puder voltar a acessá-lo.

## Device loss

O owner registra `device.lost` e `uncapturederror` antes de criar o `Context`.
Ao perder o device:

1. mudar o runtime para `Failed`;
2. rejeitar novos frames;
3. cancelar jobs;
4. concluir suspensões Kotlin com um erro de backend;
5. executar o shutdown sem tentar novo submit;
6. permitir que a camada superior crie um runtime novo.

Não tente reutilizar recordings criadas pelo device antigo.

## Métricas necessárias

O build de diagnóstico deve expor por frame:

- tempo de fila por recorder;
- início e fim da gravação;
- duração e sobreposição de CPU;
- quantidade de comandos e recursos;
- quantidade de chamadas WebGPU proxied;
- tempo total e p95 bloqueado em proxy por pthread;
- tempo entre recording pronta e inserção;
- tempo de submit;
- tempo até `onSubmittedWorkDone()`;
- frames cancelados e descartados por geração;
- tamanho máximo das filas;
- memória Wasm antes e depois do teste;
- erros WebGPU e device loss.

A otimização só vale se a redução do tempo de gravação superar o custo de proxy,
coordenação e cópia. Compare a variante pthread com o worker de comandos atual
na mesma cena, navegador, resolução e duração. Não use apenas média; compare
p50, p95, p99, throughput e jank da UI.

## Headers e publicação

A variante pthread precisa ser servida em contexto cross-origin isolated:

```http
Cross-Origin-Opener-Policy: same-origin
Cross-Origin-Embedder-Policy: require-corp
Cross-Origin-Resource-Policy: same-origin
```

Todos os scripts, Wasm, workers, imagens, fontes e demais recursos carregados
pela página precisam ser same-origin ou responder com CORS/CORP compatível. Um
único recurso incompatível pode impedir o isolamento.

O ambiente válido deve satisfazer:

```javascript
crossOriginIsolated === true
typeof SharedArrayBuffer === "function"
typeof OffscreenCanvas === "function"
navigator.gpu != null
```

Em produção, publique dois manifests:

```text
threaded
    graphite-threaded.mjs
    graphite-threaded.wasm
    pthread workers
    requer COOP/COEP e SharedArrayBuffer

fallback
    graphite.mjs
    graphite.wasm
    usa command workers
    não requer memória compartilhada
```

O bootstrap escolhe o manifest antes de instanciar Wasm. Não existe fallback
automático de um módulo pthread para memória não compartilhada.

## Etapas de implementação

### Etapa 0: congelar o baseline

- registrar tamanho dos artefatos atuais;
- medir as cenas sample com 1 e 2 recorders;
- registrar p50, p95, p99, throughput, memória e responsividade da UI;
- guardar navegador, GPU, resolução e flags usados.

Saída: relatório reproduzível do caminho de command workers.

### Etapa 1: escolher o trio compatível

- selecionar revisão de Skia com backend Dawn compatível com Emdawn;
- usar a revisão de Dawn exigida por esse Skia;
- usar o Emscripten indicado pelo pacote Emdawn;
- atualizar o fork sem misturar alterações funcionais da API;
- documentar os hashes completos.

Saída: Context Graphite simples funcionando sem pthreads no novo trio.

### Etapa 2: reconstruir toda a cadeia com memória compartilhada

- compilar Skia com `-pthread`, atomics e bulk memory;
- compilar Skiko Graphite com as mesmas flags;
- compilar main e side modules com o mesmo limite e layout de memória;
- habilitar `shared_memory=true` no Emdawn;
- verificar que nenhum archive sem atomics entra no link.

Saída: artefato threaded carrega em Dedicated Worker.

### Etapa 3: portar e validar o proxy

- aplicar o patch ao Emdawn da revisão escolhida;
- executar o gate mínimo de create/release buffer em dois pthreads;
- cobrir Futures, callbacks e funções assíncronas usadas pelo Graphite;
- instrumentar quantidade e duração das chamadas proxied.

Saída: os dois pthreads usam o device do owner sem objeto JS local.

### Etapa 4: portar o gate Graphite real

- criar Context e recorders com a nova ABI;
- executar os dois jobs de 20.000 círculos;
- inserir em ordem;
- adquirir a textura somente antes do submit;
- aguardar conclusão da queue;
- validar screenshot e métricas.

Saída: paridade com o gate que já passou no binding legado.

### Etapa 5: implementar o runtime nativo Web

- criar Dedicated Worker owner;
- transferir `OffscreenCanvas`;
- implementar pool persistente;
- exportar ABI de create, enqueue, cancel, release e shutdown;
- publicar resultados por estado atômico e evento assíncrono;
- impedir espera bloqueante no owner.

Saída: runtime C++ executa vários frames sem Kotlin.

### Etapa 6: conectar Kotlin/JS e Kotlin/Wasm

- substituir `WebValidationWorker` somente no build threaded;
- mapear command buffers e recursos para a ABI nativa;
- retornar `PlatformRecording` com handle real;
- preservar ordem do `GraphiteFrameBuilder`;
- propagar falhas e cancelamento para coroutines;
- manter o build fallback inalterado.

Saída: sample DualRecorder usa dois recorders nativos no Web threaded.

### Etapa 7: fechar ciclo de vida

- implementar resize por geração;
- implementar cancelamento em todos os estados;
- implementar shutdown assíncrono;
- implementar device loss;
- testar criação e destruição repetida da surface.

Saída: nenhum job, handle ou thread sobrevive ao runtime.

### Etapa 8: carga e decisão de adoção

- executar carga contínua por no mínimo 60 segundos;
- medir memória, proxy, filas e tempos de frame;
- executar em Chrome estável e ao menos outro navegador com WebGPU e pthreads;
- comparar com o fallback;
- adotar pthreads somente onde houver ganho mensurável.

Saída: relatório de decisão, incluindo cenários em que o fallback é mais rápido.

### Etapa 9: distribuição gradual

- publicar os dois manifests;
- selecionar threaded apenas quando todos os recursos estiverem presentes;
- incluir kill switch para forçar fallback;
- coletar falhas de inicialização e device loss por variante;
- aumentar a exposição gradualmente.

Saída: rollout reversível sem retirar compatibilidade do Web atual.

## Gates de aceite

### Build

- [ ] Skia, Skiko, Emdawn, main module e side module usam o mesmo emsdk.
- [ ] Todos os objetos do artefato threaded foram compilados com `-pthread`.
- [ ] O build é reproduzível a partir de hashes fixos.
- [ ] Kotlin/JS e Kotlin/Wasm produzem variantes threaded e fallback.

### Inicialização

- [ ] Falha de COOP/COEP é detectada antes de instanciar o módulo.
- [ ] `OffscreenCanvas` é transferida uma única vez.
- [ ] Side modules carregam antes do pool executar jobs.
- [ ] Context e device possuem um único owner.

### Corretude

- [ ] Dois recorders nativos executam em pthreads distintos.
- [ ] As janelas de CPU apresentam sobreposição positiva.
- [ ] Recordings são inseridas na ordem do frame.
- [ ] O screenshot contém o resultado de ambos os recorders.
- [ ] `insertRecording()` e `submit()` reportam sucesso.
- [ ] Não há validation error nem uncaptured WebGPU error.
- [ ] A textura atual é adquirida somente na fase de apresentação.

### Ciclo de vida

- [ ] Resize sob carga não mistura gerações.
- [ ] Cancelar job enfileirado não executa o job.
- [ ] Cancelar job em execução não libera memória prematuramente.
- [ ] Shutdown durante gravação termina sem deadlock.
- [ ] Device loss conclui todas as suspensões com erro.
- [ ] Criar e destruir a surface repetidamente não aumenta memória sem limite.

### Desempenho

- [ ] Carga contínua de 60 segundos termina sem erro.
- [ ] Contagem e duração das chamadas proxied estão disponíveis.
- [ ] p50, p95 e p99 são comparados com o fallback.
- [ ] A UI permanece responsiva durante gravação e submit.
- [ ] O relatório identifica cenas onde pthreads não compensam.

### Distribuição

- [ ] Headers de isolamento existem no ambiente real de produção.
- [ ] Todos os recursos da página são compatíveis com COEP.
- [ ] O bootstrap escolhe o artefato antes da instanciação.
- [ ] O kill switch retorna ao fallback sem novo deploy.

## Rollback

O rollback não tenta reinicializar o mesmo módulo com memória diferente. Ele
encerra o runtime threaded e recarrega a aplicação ou a surface usando o
manifest fallback.

Acione fallback quando:

- o ambiente não for cross-origin isolated;
- `SharedArrayBuffer`, OffscreenCanvas ou WebGPU não estiver disponível;
- a criação do pool, device ou Context falhar;
- o watchdog detectar deadlock ou timeout de proxy;
- device loss se repetir;
- métricas mostrarem regressão relevante para a cena atual.

Falha de um frame após o runtime estar pronto deve ser reportada. Não esconda
erro estrutural alternando silenciosamente entre backends no meio do frame.

## Arquivos de referência

- `EMDAWN_PTHREAD_PROXY_FINDINGS.md`: pesquisa, fontes e resultados medidos.
- `SURFACE_AND_NATIVE_THREADS.md`: contrato geral entre Surface, Engine,
  Recorder e threads nativos.
- `graphite-surface/experiments/wasm-pthreads/README.md`: como construir e
  executar os gates existentes.
- `graphite-surface/experiments/wasm-pthreads/graphite-emdawn-gate.cpp`: gate
  completo de dois recorders Graphite.
- `graphite-surface/experiments/wasm-pthreads/emdawn-handle-gate.cpp`: gate
  mínimo de ownership de handles.
- `graphite-surface/experiments/wasm-pthreads/patch-emdawn-proxy.ts`: patch
  experimental de proxy síncrono.
- `graphite-surface/experiments/wasm-pthreads/skia-pthreads.patch`: mudanças de
  build exigidas pelo Skia m152 experimental.
- `graphite-surface/experiments/wasm-pthreads/server.ts`: servidor local com
  headers COOP, COEP e CORP.

## Definição de concluído

A migração termina quando o sample Web threaded usa dois `Recorder`s nativos
persistentes, apresenta suas `Recording`s em ordem, passa todos os gates de
corretude e ciclo de vida, mantém carga por 60 segundos, apresenta métricas de
proxy e supera ou iguala o fallback nas cenas que justificam paralelismo. Até
lá, o caminho Web suportado continua sendo o worker de comandos transferíveis.
