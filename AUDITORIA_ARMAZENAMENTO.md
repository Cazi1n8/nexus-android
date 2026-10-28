# NEXUS — auditoria de armazenamento da alpha.2 e do CORE

Baseline medido em 27/10/2026, **antes das alterações de modularização**. Projeto existente, `3.0.0-alpha.2`, versionCode 25. Em 28/10/2026, os APKs CORE compilados, `3.0.0-alpha.3-core`, versionCode 26, foram inspecionados e comparados com a cópia preservada do APK antigo. Os números abaixo são medições dos arquivos reais; não são uma previsão do tamanho instalado no Samsung Galaxy A03.

## 1. Medição efetivamente realizada

- APK debug local: **1.738.201.604 bytes** (1.738202 GB; 1.618826 GiB).
- SHA-256 local: `645f2df7eb851bf9f11b61471ef9cc44d0d62d9e3793c32ad7996ab41df24a9e`.
- Entradas de arquivo no APK: **609**.
- Soma dos tamanhos descompactados: **1.791.076.370 bytes**.
- Soma dos dados comprimidos/armazenados das entradas: **1.737.735.449 bytes**.
- Diretório ZIP. cabeçalhos. assinatura e alinhamento: **466.155 bytes** além dessa soma.
- O APK local possui a mesma árvore de código principal e o mesmo tamanho total do arquivo publicado no build 16. Certificado e hash do APK local diferem do APK do GitHub; não misturar os identificadores.

O relato do Samsung Galaxy A03 é: aplicativo 1,90 GB; dados 498 MB; cache 13,51 MB; total 2,41 GB. **Não houve acesso ao sistema de arquivos ou ADB desse aparelho.** As unidades/arredondamentos da tela e os bytes de código otimizado pelo Android não foram medidos aqui. Portanto, esta auditoria identifica precisamente o conteúdo do APK, mas não inventa uma decomposição exata dos 1,90 GB instalados ou dos 498 MB do aparelho.

## 2. Vinte maiores arquivos do APK

Ordenação por tamanho descompactado. MB nesta tabela é decimal: 1 MB = 1.000.000 bytes. Os modelos GGUF e bibliotecas nativas estão armazenados sem compressão no ZIP; esse tamanho ocupa praticamente o mesmo espaço no arquivo de instalação.

| # | Arquivo | Bytes descompactados | MB | Bytes no ZIP | Finalidade |
| --- | --- | ---: | ---: | ---: | --- |
| 1 | `assets/models/qwen2.5-1.5b-instruct-q4_k_m.gguf` | 1.117.320.736 | 1.117,321 | 1.117.320.736 | Pesos Qwen2.5 1,5B Q4_K_M: geração de texto offline; não é necessário ao mouse. |
| 2 | `assets/models/qwen2.5-0.5b-instruct-q4_k_m.gguf` | 491.400.032 | 491,400 | 491.400.032 | Pesos Qwen2.5 0,5B Q4_K_M: perfil compacto de texto offline; não é necessário ao mouse. |
| 3 | `classes.dex` | 35.575.088 | 35,575 | 9.451.229 | Bytecode Android/Kotlin/Java e dependências; inclui 9.266 classes de ícones Material, além de Compose e CameraX. |
| 4 | `classes14.dex` | 21.905.752 | 21,906 | 7.368.969 | Bytecode adicional; inclui 2.132 classes de ícones, Compose UI e ferramentas de prévia/depuração. |
| 5 | `lib/x86/libmediapipe_tasks_jni.so` | 14.961.388 | 14,961 | 14.961.388 | Runtime JNI MediaPipe: rastreamento de mãos e tarefas visuais para x86. |
| 6 | `lib/x86_64/libmediapipe_tasks_jni.so` | 13.032.736 | 13,033 | 13.032.736 | Runtime JNI MediaPipe: rastreamento de mãos e tarefas visuais para x86_64. |
| 7 | `lib/arm64-v8a/libmediapipe_tasks_jni.so` | 10.533.648 | 10,534 | 10.533.648 | Runtime JNI MediaPipe: rastreamento de mãos e tarefas visuais para arm64-v8a. |
| 8 | `classes15.dex` | 8.991.864 | 8,992 | 3.380.797 | Bytecode de dependências: Guava, MediaPipe, protobuf/DataStore e transporte do SDK. |
| 9 | `assets/hand_landmarker.task` | 7.819.105 | 7,819 | 7.819.105 | Modelo MediaPipe de mão: detector da palma e localizador de pontos. Necessário ao mouse offline. |
| 10 | `lib/armeabi-v7a/libmediapipe_tasks_jni.so` | 7.405.000 | 7,405 | 7.405.000 | Runtime JNI MediaPipe: rastreamento de mãos e tarefas visuais para armeabi-v7a. |
| 11 | `lib/x86_64/libnexus_llama.so` | 6.015.560 | 6,016 | 6.015.560 | Runtime CPU/JNI llama.cpp: inferência do chat offline para x86_64. |
| 12 | `lib/arm64-v8a/libnexus_llama.so` | 5.443.048 | 5,443 | 5.443.048 | Runtime CPU/JNI llama.cpp: inferência do chat offline para arm64-v8a. |
| 13 | `assets/vision/efficientnet_lite0_int8.tflite` | 5.434.517 | 5,435 | 5.434.517 | Modelo quantizado de classificação local de objetos; usado pelo NEXUS Vision. |
| 14 | `lib/x86/libtesseract.so` | 4.339.620 | 4,340 | 4.339.620 | Motor nativo Tesseract de OCR para x86. |
| 15 | `lib/x86_64/libtesseract.so` | 4.332.136 | 4,332 | 4.332.136 | Motor nativo Tesseract de OCR para x86_64. |
| 16 | `lib/arm64-v8a/libtesseract.so` | 4.164.360 | 4,164 | 4.164.360 | Motor nativo Tesseract de OCR para arm64-v8a. |
| 17 | `assets/ocr/eng.traineddata` | 4.113.088 | 4,113 | 2.059.486 | Modelo Tesseract de leitura de texto em inglês; preparação de OCR local. |
| 18 | `classes16.dex` | 3.944.440 | 3,944 | 1.608.171 | Bytecode restante: Kotlin/Coroutines, OkHttp, CommonMark/Markdown e suporte. |
| 19 | `lib/x86_64/libleptonica.so` | 3.104.384 | 3,104 | 3.104.384 | Processamento nativo de imagens usado pelo OCR para x86_64. |
| 20 | `lib/armeabi-v7a/libtesseract.so` | 3.004.948 | 3,005 | 3.004.948 | Motor nativo Tesseract de OCR para armeabi-v7a. |

## 3. De onde vem o tamanho do aplicativo

| Grupo | Arquivos | Descompactado (bytes) | No APK (bytes) |
| --- | ---: | ---: | ---: |
| Modelos generativos GGUF | 2 | 1.608.720.768 | 1.608.720.768 |
| Bibliotecas nativas de quatro ABIs | 40 | 88.881.912 | 88.881.912 |
| Código DEX | 16 | 72.876.264 | 22.799.247 |
| Modelo obrigatório de mãos | 1 | 7.819.105 | 7.819.105 |
| Modelo de objetos Vision | 1 | 5.434.517 | 5.434.517 |
| Modelos OCR português/inglês | 2 | 6.095.844 | 3.105.373 |
| Recursos. manifesto e metadados | 530 | 1.124.638 | 923.820 |
| Licenças e avisos | 17 | 123.322 | 50.707 |

**Causa principal confirmada: os dois modelos generativos somam 1.608.720.768 bytes, equivalentes a 92,5509% do APK.** A alpha.2 inclui obrigatoriamente o modelo compacto e o de 1,5B, mesmo quando o telefone só usa o compacto ou não suporta IA local. Esse conteúdo não participa do rastreamento das mãos, dos cliques ou da rolagem.

### Arquiteturas e bibliotecas de visão

| ABI | Bibliotecas | Bytes |
| --- | ---: | ---: |
| `arm64-v8a` | 11 | 24.795.656 |
| `armeabi-v7a` | 9 | 12.955.376 |
| `x86` | 9 | 22.833.288 |
| `x86_64` | 11 | 28.297.592 |

- São 40 arquivos `.so`, somando **88.881.912 bytes**. O mesmo nome de biblioteca em ABIs diferentes representa código de máquina diferente; não é uma duplicata que possa ser apagada indiscriminadamente.
- Os grupos x86/x86_64 somam **51.130.880 bytes**; são usados em emuladores e aparelhos Intel, não em processadores ARM. Remover ABIs deve ser decisão explícita de distribuição, preservando uma alternativa compatível.
- O A03 não deve ser classificado apenas pelo nome comercial: a central deve consultar `Build.SUPPORTED_ABIS` e o processo Android real. Não presumimos que todos os A03 tenham sistema de 64 bits ou uma quantidade específica de RAM.
- `libmediapipe_tasks_jni.so` e `assets/hand_landmarker.task` são necessários ao rastreamento real. O `.task` contém `hand_detector.tflite` (2.339.878 bytes) e `hand_landmarks_detector.tflite` (5.478.949 bytes). Eles não são o modelo de linguagem.
- OCR usa Tesseract + Leptonica; Vision utiliza EfficientNet-Lite0 de 5.434.517 bytes. Separar esses dados opcionais não autoriza remover o MediaPipe usado pelo mouse.
- Não foram encontrados `.onnx` ou outros pesos generativos ocultos: os arquivos de modelo empacotados estão identificados nas categorias acima.

### Arquivos duplicados e depuração

- Todos os 609 arquivos foram comparados por SHA-256. Duplicatas exatas representam somente **87.275 bytes descompactados**, cerca de 0,005% do APK. Predominam textos de licença e pequenos recursos/metadados. Apagar essas duplicatas não resolve o armazenamento e pode remover avisos legais necessários.
- Nenhuma das 40 bibliotecas nativas continha seções `.debug*`, `.zdebug*` ou `.symtab` no ELF empacotado. Os símbolos nativos de depuração já foram removidos dos arquivos entregues; não existe um arquivo de símbolos de gigabytes a eliminar.
- A distribuição examinada é **debug**, com `debuggable=true`, Compose tooling e código sem redução R8. `classes.dex` e `classes14.dex` contêm, juntos, **11.398 classes do pacote de ícones Material**. A dependência `material-icons-extended` inteira no debug contribui ao DEX; remover/trocar essa dependência exige preservar os ícones realmente utilizados.
- O release alpha.2 existente usa R8: seu DEX total é **4.058.376 bytes descompactados / 1.979.460 bytes comprimidos**, contra 72.876.264 / 22.799.247 no debug. Mesmo assim, seu APK permanece em **1.715.971.484 bytes**, pois continua incluindo os mesmos 1.608.720.768 bytes de GGUF. Esse release antigo é sem assinatura e não instalável.
- O manifesto compilado declara `extractNativeLibs=false`. Não há base para atribuir o tamanho instalado do A03 à extração de todas as quatro ABIs; otimizações ART e a contabilização do sistema exigem medição no dispositivo.

## 4. Investigação dos 498 MB de dados após instalação

A fonte confirma que o app copia arquivos que já estão no APK para o armazenamento privado. Isso é diferente de cache. O arquivo original dentro do APK continua presente depois da cópia.

| Origem/caminho privado | Conteúdo e gatilho | Tamanho conhecido ou limite |
| --- | --- | --- |
| `files/models/qwen2.5-0.5b-instruct-q4_k_m.gguf` | Perfil compacto copiado do asset quando se abre o Chat, ou baixado na variante sem pesos | **491.400.032 bytes** |
| `files/models/qwen2.5-1.5b-instruct-q4_k_m.gguf` | Perfil equilibrado, preparado se selecionado e compatível | **1.117.320.736 bytes**, se instalado |
| `files/models/*.gguf.part` | Cópia/download incompleto que pode ser retomado; removido após conclusão válida | Até o tamanho do modelo selecionado |
| `files/ocr/tessdata/{por,eng}.traineddata` | Cópias privadas dos assets Tesseract, criadas quando OCR é usado | **6.095.844 bytes** no total |
| `files/chat-images/*.jpg` | Fotos importadas/capturadas mantidas no histórico | Variável, depende das conversas |
| `databases/nexus.db`, `-wal`, `-shm` | Room: conversas, mensagens e memórias autorizadas | Variável |
| `files/datastore/nexus_preferences.preferences_pb` | Preferências DataStore | Variável, normalmente pequeno |
| `shared_prefs/` | Gestos, comandos, Libras e dados protegidos do token opcional | Variável |
| `cache/nexus-import-*`, `nexus-image-*`, `nexus-camera-*` | Arquivos temporários de fotos/importação | Variável; são cache, não o GGUF concluído |

**Hipótese forte, não uma medição do A03:** 491.400.032 bytes do GGUF compacto + 6.095.844 bytes das cópias OCR = **497.495.876 bytes (~497,50 MB)**, muito próximo dos 498 MB relatados. A coincidência e os caminhos da fonte justificam conferir esses grupos primeiro na nova Central de Armazenamento. Não provam que o telefone tenha exatamente essas cópias; conversas, fotos, downloads parciais e arredondamento podem alterar o resultado.

Pacotes de voz do mecanismo TTS/SpeechRecognizer podem pertencer a aplicativos do sistema ou a outro provedor; não devem ser somados ou apagados como se fossem arquivos privados do NEXUS sem medição própria.

O cache informado de 13,51 MB não contém a causa dos 1,90 GB do aplicativo. Limpar cache não remove pesos embutidos no APK e não deve apagar conversas ou modelos concluídos disfarçando-os como temporários. A remoção de um modelo deve ser uma ação própria e explícita.

## 5. Arquitetura implementada e recursos preservados

- CORE: CameraX, MediaPipe, modelo de mãos, cursor, clique, rolagem, configurações e serviços autorizados foram mantidos. O modelo de mãos de **7.819.105 bytes**, SHA-256 `fbc2a30080c3c557093b5ddfc334698132eb341044ccee322ccf8bcf3607cde1`, está nos dois APKs CORE examinados. O mouse não depende de um download de pesos de chat. A usabilidade do rastreamento com câmera real no A03 ainda exige teste físico.
- IA offline: os dois modelos de linguagem passam a ser dados opcionais, com tamanho prévio, confirmação de download, checksum/revisão pinados, retomada e remoção por perfil. Abrir a central não inicia download automaticamente. A remoção respeita o uso do arquivo pelo motor. Não se baixam arquivos `.so`, `.dex` ou APKs arbitrários dos servidores de modelos.
- IA online: cliente/servidor seguro, sem GGUF obrigatório e sem credencial privada dentro do APK. Backend público continua uma dependência distinta do tamanho do pacote.
- A Central de Extensões organiza os recursos e separa dois pacotes de dados do Vision: objetos, **5.434.517 bytes**, e OCR português/inglês, **6.095.844 bytes**. Os dados são baixados por HTTPS e validados por SHA-256 antes de serem usados. O código leve de Vision, 3D, Libras e Voice continua integrado ao base APK; as telas informam isso. Não foi implementado download remoto de código nem remoção binária desses recursos.
- Pela Google Play, Android App Bundle pode entregar APKs por ABI e Play Feature Delivery/Play Asset Delivery podem entregar módulos/assets sob demanda. Isso requer estrutura/configuração de publicação própria; não é automaticamente obtido removendo um asset do Gradle.
- Na instalação manual por APK, um APK universal pode manter as ABIs compatíveis, enquanto dados verificados são baixados sob demanda. Módulos split de código exigem APIs oficiais de instalação, assinatura compatível e consentimento do instalador; não há autorização para baixar/executar DEX de origem desconhecida.
- A atualização que elimina GGUF do APK não apaga automaticamente cópias já existentes em `files/models`. A Central de Armazenamento e a gestão de modelos mostram os arquivos e permitem remoção explícita por perfil. As cópias OCR antigas são reutilizadas, evitando uma segunda cópia privada. Conversas e preferências são preservadas.
- A Central de Armazenamento mede APKs/splits, modelos completos e parciais, dados de extensões, OCR legado, imagens das conversas, banco/preferências, outros arquivos, cache temporário e cache de código. A consulta de contabilização Android é restrita ao próprio UID, sem `PACKAGE_USAGE_STATS`; a interface diferencia os números do sistema dos tamanhos lógicos dos arquivos e evita somar o cache duas vezes. Limpar temporários afeta somente `cacheDir`, sem apagar modelos, conversas ou `code_cache`.

## 6. Antes/depois efetivamente compilado

Comparação de APKs universais, incluindo as quatro ABIs compatíveis. **MB = 1.000.000 bytes.** A primeira comparação mantém a variante debug, para separar o efeito de retirar os dados opcionais do efeito do R8. A segunda mostra o APK de teste otimizado e assinado.

| Artefato local real | Bytes do APK | MB | Redução em relação ao debug alpha.2 |
| --- | ---: | ---: | ---: |
| alpha.2 debug, preservado | 1.738.201.604 | 1.738,202 | — |
| CORE debug | 121.054.931 | 121,055 | 1.617.146.673 bytes; **93,0356%** |
| CORE `coreTest`, R8 + redução de recursos | 98.878.796 | 98,879 | 1.639.322.808 bytes; **94,3114%** |

Os dois APKs CORE examinados contêm **zero `.gguf`, `.onnx`, `.traineddata` ou modelos em `assets/vision/`**. As licenças e o modelo obrigatório de mãos continuam presentes. O CORE debug ocupa 171.126.373 bytes somando as entradas descompactadas; o CORE `coreTest`, 100.830.135 bytes. O APK não precisa ser descompactado integralmente para instalação.

| Componente do CORE `coreTest` | Quantidade | Bytes descompactados | Bytes no ZIP |
| --- | ---: | ---: | ---: |
| Bibliotecas nativas | 40 | 88.034.744 | 88.034.744 |
| Modelo de mãos | 1 | 7.819.105 | 7.819.105 |
| DEX reduzido por R8 | 1 | 4.145.636 | 2.016.787 |
| Recursos, manifesto e metadados | 432 | 701.540 | 531.072 |
| Licenças | 17 | 123.322 | 45.822 |
| Perfis Android de inicialização | 2 | 5.788 | 5.788 |

As bibliotecas nativas continuam a maior parcela do CORE: `arm64-v8a`, 24.408.352 bytes; `armeabi-v7a`, 12.955.376; `x86`, 22.833.288; `x86_64`, 27.837.728. Foram preservadas as quatro ABIs; a redução de tamanho **não depende de excluir aparelhos de 32 bits ou emuladores**. A distribuição futura por App Bundle pode entregar somente a ABI necessária. As bibliotecas já chegam sem seções `.debug*`, `.zdebug*` e `.symtab`; não se apagaram símbolos inexistentes ou arquivos legais para obter os números.

Hashes dos APKs locais:

- CORE debug: `c9a8cf01216f0fcc9d007d97f217458adcfa82c8302829e58e74d2c389c63090`.
- CORE `coreTest`: `e407d5d35732321fca76e9723433feb297cdcb2da14f900e33d5ba44492064fe`.

O `coreTest` usa assinatura de teste **já existente** no ambiente, certificado SHA-256 `ffc6524eee21a185872e946e78649a977f4b82011ee573c20adf5ca6ba376f79`, e não uma identidade de produção. `apksigner verify` confirmou assinatura v2 válida; `zipalign -c -P 16 4` confirmou alinhamento de 16 KiB. O manifesto compilado confirma applicationId `br.com.nexus.assistant`, versionCode 26, minSdk 26, targetSdk 35 e `extractNativeLibs=false`. O `coreTest` não é depurável. Certificados e hashes de uma compilação futura no GitHub podem diferir; estes identificadores não devem ser atribuídos a outro APK.

**Assinatura e atualização:** o APK público alpha.2 do build 16 foi assinado com outro certificado (`22f77037ab58341a0066ab5089d5122755cebd79610f22e3b51cfb5c6cdfebc4`). O CORE local acima não é uma atualização direta compatível desse APK. Este relatório não recomenda apagar a instalação ou os dados para resolver a diferença; primeiro devem ser preservados/exportados os dados e preparada uma estratégia de distribuição com assinatura persistente.

**Limite da comparação:** a redução de **94,31%** foi medida no **APK**, e não nos 2,41 GB contabilizados pelo A03. O Android pode criar ART/OAT/VDEX e arredondar alocações. Os 498 MB privados antigos podem permanecer após uma atualização com assinatura compatível até que o usuário remova o modelo opcional. Não há medição de espaço físico liberado no A03 nem confirmação de que o total instalado agora seja 98,879 MB.

## 7. Testes e verificações executados

- Gradle: `testDebugUnitTest lintDebug lintCoreTest assembleDebug assembleCoreTest assembleDebugAndroidTest` terminou com **BUILD SUCCESSFUL**. Compilados ambos os APKs principais e o APK de instrumentação debug.
- JUnit local: **205 testes, 0 falhas, 0 erros, 0 ignorados**, em 28 relatórios XML de `testDebugUnitTest`.
- Android Lint: debug e `coreTest`, cada um com **0 erros fatais/erros, 50 avisos e 1 informação**. Não se afirma ausência de avisos.
- Auditoria ZIP/SHA-256: modelo de mãos íntegro, quatro ABIs presentes, dados opcionais ausentes, inventário de arquivos e tamanhos reais registrado.
- `apksigner`, `zipalign` e `aapt2`: assinatura, alinhamento e manifesto compilado verificados no APK `coreTest` local.
- Os testes de instalação em emulador, download real de extensões e quaisquer verificações posteriores não estão presumidos neste registro; seus resultados devem ser anexados quando executados.
- Permanecem testes físicos: câmera e gestos/clique/rolagem no A03, consumo, continuidade dos serviços no firmware Samsung, valores instalados em Configurações e instalação com Play Protect ativado. **Nenhuma aprovação do Play Protect foi comprovada.**

## Evidência e fontes de código

- [Inventário completo, 20 maiores, SHA-256, duplicatas, ABIs e seções ELF](docs/evidencias/storage-alpha2-baseline.json).
- [Medição dos dois APKs CORE compilados, comparação, manifestos, assinatura e verificações](docs/evidencias/storage-core-after.json).
- [Preparação/download/remoção dos modelos](app/src/main/java/br/com/nexus/assistant/data/local/LocalModelManager.kt).
- [Seleção de perfil e cópia ao abrir Chat](app/src/main/java/br/com/nexus/assistant/NexusViewModel.kt).
- [Cópias privadas de OCR](app/src/main/java/br/com/nexus/assistant/device/vision/LocalTextReader.kt).
- [Dependências e configuração do pacote](app/build.gradle.kts).
- Referências oficiais de arquitetura: [App Bundle](https://developer.android.com/guide/app-bundle), [Play Feature Delivery](https://developer.android.com/guide/playcore/feature-delivery), [Play Asset Delivery](https://developer.android.com/guide/playcore/asset-delivery), [PackageInstaller](https://developer.android.com/reference/android/content/pm/PackageInstaller). Os links são referências técnicas; este relatório não afirma publicação na Google Play nem entrega dinâmica já implantada.
