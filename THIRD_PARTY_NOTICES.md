# Componentes de terceiros

NEXUS identifica as tecnologias utilizadas e não apresenta modelos públicos como se tivessem sido treinados pela equipe do aplicativo. As marcas pertencem a seus titulares.

## Inferência local: llama.cpp / ggml

- Projeto: https://github.com/ggml-org/llama.cpp
- Versão: `b11838`, commit `4231b23dc2c46661d01157aac5245bdcf03ce4b1`.
- Licença: MIT, Copyright (c) 2023–2026 The ggml authors.
- Texto completo incluído no APK: `app/src/main/assets/licenses/llama.cpp-MIT.txt`.
- A fonte é obtida explicitamente por `scripts/fetch_llama_cpp.py`, com SHA-256 do arquivo verificado antes da extração. Não há download de código durante a execução do aplicativo.

## Modelos de linguagem incluídos na versão completa

Os pesos Qwen são modelos públicos de terceiros; NEXUS não os apresenta como modelos treinados por sua equipe. Ambos são de texto, sob Apache 2.0. A preparação `scripts/fetch_bundled_model.py --with-balanced` inclui os dois perfis no mesmo APK, fora do Git. A cópia privada e verificação no telefone não fazem outro download. A variante de desenvolvimento `-PnexusBundleModel=false` mantém download autorizado para regressão.

| Modelo / fonte oficial | Revisão | Bytes / SHA-256 | Licença no APK |
| --- | --- | --- | --- |
| [Qwen2.5-0.5B-Instruct-GGUF](https://huggingface.co/Qwen/Qwen2.5-0.5B-Instruct-GGUF), Q4_K_M | `9217f5db79a29953eb74d5343926648285ec7e67` | 491.400.032 / `74a4da8c9fdbcd15bd1f6d01d621410d31c6fc00986f5eb687824e7b93d7a9db` | `licenses/Qwen2.5-0.5B-Apache-2.0.txt` |
| [Qwen2.5-1.5B-Instruct-GGUF](https://huggingface.co/Qwen/Qwen2.5-1.5B-Instruct-GGUF), Q4_K_M | `91cad51170dc346986eccefdc2dd33a9da36ead9` | 1.117.320.736 / `6a1a2eb6d15622bf3c96857206351ba97e1af16c30d7a74ee38970e434e9407e` | `licenses/Qwen2.5-1.5B-Apache-2.0.txt` |

`licenses/QWEN-MODEL-NOTICES.txt` registra as fontes e a preparação. O runtime e os pesos têm licenças próprias. OCR e classificação de objetos podem fornecer evidências textuais ao modelo; isso não transforma o Qwen em um modelo multimodal. A avaliação atual ainda encontra falhas semânticas: [TESTES_NEXUS_AI.md](TESTES_NEXUS_AI.md).

## Classificação visual local

EfficientNet-Lite0 INT8, distribuição oficial MediaPipe versão 1, sob Apache 2.0. [Arquivo oficial](https://storage.googleapis.com/mediapipe-models/image_classifier/efficientnet_lite0/int8/1/efficientnet_lite0.tflite): 5.434.517 bytes, SHA-256 `bc2ffe19c1118de0c0c2a9088992da5589722656e0fba81421385300a4a34b16`. Preparação: `scripts/fetch_object_model.py`; inferência real pelo MediaPipe ImageClassifier. Licença e aviso incluídos: `licenses/EfficientNet-Lite0-Apache-2.0.txt` e `licenses/VISION-CLASSIFIER-NOTICES.txt`.

O resultado é uma categoria aproximada de ImageNet, sem identificação de pessoas, localização espacial ou compreensão geral de cenas. A fotografia oficial usada pelos testes fica apenas no APK de instrumentação, não no APK do aplicativo.

## Markdown: Markwon

[Markwon 4.6.2](https://github.com/noties/Markwon/tree/v4.6.2), dependência `io.noties.markwon:core:4.6.2`, licença Apache 2.0. A [licença do tag oficial](https://raw.githubusercontent.com/noties/Markwon/v4.6.2/LICENSE) foi obtida por HTTPS: 11.325 bytes, SHA-256 `cb5e8e7e5f4a3988e1063c142c60dc2df75605f4c46515e776e3aca6df976e14`. Cópia e aviso: `licenses/Markwon-Apache-2.0.txt` e `licenses/MARKWON-NOTICES.txt`. Usado para Markdown textual e blocos de código; o app não inclui o plugin de carregamento de imagens remotas.

## OCR local: Tesseract4Android, Tesseract e idiomas

- Integração Android: [Tesseract4Android 4.9.0](https://github.com/adaptech-cz/Tesseract4Android/tree/4.9.0), Copyright (C) 2019 Adaptech s.r.o., Robert Pösel, licença Apache 2.0. AAR oficial da coordenada JitPack `cz.adaptech.tesseract4android:tesseract4android:4.9.0`, variante padrão sem OpenMP. `scripts/fetch_ocr_runtime.py` verifica tamanho 12.848.454 bytes e SHA-256 `bce5d6413a1a5ae3d7240033fbbc851ba3217d0a08d9769400e17a077f42cb2a` antes do uso como dependência local pelo Gradle; o AAR fica fora do Git. A coordenada canônica `com.github.adaptech-cz.Tesseract4Android:tesseract4android:4.9.0` distribui o mesmo conteúdo verificado.
- Motor: [Tesseract OCR 5.5.1](https://github.com/tesseract-ocr/tesseract/tree/5.5.1), licença Apache 2.0. O AAR também incorpora Leptonica 1.85.0 (BSD), libjpeg 9f (IJG) e libpng 1.6.48 (PNG Reference Library License), com licenças próprias preservadas nos avisos do APK. This software is based in part on the work of the Independent JPEG Group.
- Idiomas: [tessdata_fast](https://github.com/tesseract-ocr/tessdata_fast/tree/87416418657359cb625c412a48b6e1d6d41c29bd), revisão `87416418657359cb625c412a48b6e1d6d41c29bd`, Apache 2.0. `por.traineddata` e `eng.traineddata` são preparados com tamanho e SHA-256 fixados por `scripts/fetch_ocr_models.py`, ficam fora do Git e são incorporados ao APK. Não são modelos de linguagem conversacional.
- Textos completos no APK: `licenses/Tesseract4Android-Apache-2.0.txt`, `licenses/Tesseract-Apache-2.0.txt`, `licenses/tessdata_fast-Apache-2.0.txt`, `licenses/Leptonica-BSD.txt`, `licenses/libjpeg-README.txt`, `licenses/libpng-LICENSE.txt` e `licenses/OCR-NOTICES.txt`.
- A função lê texto de uma foto no próprio aparelho; não envia a imagem ou o texto a um serviço de OCR. Ditado Android, síntese de voz e análise multimodal personalizada têm configurações e políticas separadas. Limitações e preparação: [docs/OCR_LOCAL.md](docs/OCR_LOCAL.md).

## Plataforma, câmera e rede

AndroidX (Compose, Room, DataStore, Lifecycle e CameraX), Kotlin, kotlinx.coroutines, MediaPipe Tasks Vision e OkHttp são dependências declaradas no Gradle. As versões constam em `app/build.gradle.kts`; seus termos e avisos devem ser revisados antes da distribuição comercial:

- AndroidX: https://android.googlesource.com/platform/frameworks/support/ — Apache 2.0.
- Kotlin: https://github.com/JetBrains/kotlin — Apache 2.0.
- kotlinx.coroutines: https://github.com/Kotlin/kotlinx.coroutines — Apache 2.0.
- MediaPipe: https://github.com/google-ai-edge/mediapipe — Apache 2.0.
- OkHttp: https://github.com/square/okhttp — Apache 2.0.

O arquivo de mãos é obtido pelo script `scripts/fetch_hand_model.py` a partir da distribuição oficial do MediaPipe. Nenhum modelo de linguagem é enviado a servidores junto com as conversas no modo NEXUS AI local. Um provedor personalizado usa sua própria política e exige consentimento separado no aplicativo.

## MediaPipe: derivação documentada de privacidade

O app usa `tasks-vision:0.10.35` com um AAR `tasks-core` derivado, preparado por `scripts/prepare_mediapipe_runtime.py`. A única classe alterada é `TasksStatsLoggerFactory`, para selecionar a fábrica no-op `TasksStatsDummyLogger` prevista pela geração padrão do [BUILD oficial v0.10.35](https://github.com/google-ai-edge/mediapipe/blob/v0.10.35/mediapipe/tasks/java/com/google/mediapipe/tasks/core/BUILD). Não se apresenta o derivado como um AAR Google original.

A preparação verifica as entradas e conserva o payload das demais classes, recursos e bibliotecas nativas; a etapa Gradle/NDK posterior pode remover símbolos nativos. O AAR derivado tem 50.686.496 bytes, SHA-256 `25ac823c37f77d2870c32bfbe0dfae30b2b1a921b7fce395e743399c3b638bf3`. Licença e aviso incluídos: `licenses/MediaPipe-Apache-2.0.txt` e `licenses/MEDIAPIPE-RUNTIME-NOTICES.txt`. [Proveniência](docs/evidencias/ai3-mediapipe-runtime.json) e [auditoria](AUDITORIA_PLAY_PROTECT.md) descrevem o logger encontrado, a derivação e seus limites. Ela não comprova ausência de todo tráfego nem aprovação do Play Protect.

## Ícones Material selecionados

Os 22 vetores Filled usados pelo NEXUS que antes vinham de `androidx.compose.material:material-icons-extended-android:1.7.8` foram conservados em `app/src/main/java/br/com/nexus/assistant/ui/icons/`, Copyright 2025 The Android Open Source Project, Apache 2.0. Apenas a declaração de pacote Kotlin foi alterada; geometria, dimensões, comportamento e avisos originais permanecem. Os ícones básicos e ArrowBack/Send AutoMirrored continuam na dependência pequena `material-icons-core:1.7.8`. Não há carregamento remoto de ícones.

Fonte: [sources JAR oficial AndroidX 1.7.8](https://dl.google.com/dl/android/maven2/androidx/compose/material/material-icons-extended-android/1.7.8/material-icons-extended-android-1.7.8-sources.jar), **12.086.044 bytes**, SHA-256 `77b7e782a519a3e074fc91e09d59334ac1b815edd87d2e89c0c9ca7c95678e93`, conferidos contra os metadados oficiais Gradle. A [proveniência por arquivo](app/src/main/java/br/com/nexus/assistant/ui/icons/PROVENANCE.json) registra hashes e a transformação de pacote. Os cabeçalhos e o [texto completo da licença](app/src/main/java/br/com/nexus/assistant/ui/icons/LICENSE.txt) foram preservados; a licença Apache 2.0 completa também está presente nos avisos existentes do APK.
