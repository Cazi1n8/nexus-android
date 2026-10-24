# Componentes de terceiros

NEXUS identifica as tecnologias utilizadas e não apresenta modelos públicos como se tivessem sido treinados pela equipe do aplicativo. As marcas pertencem a seus titulares.

## Inferência local: llama.cpp / ggml

- Projeto: https://github.com/ggml-org/llama.cpp
- Versão: `b11838`, commit `4231b23dc2c46661d01157aac5245bdcf03ce4b1`.
- Licença: MIT, Copyright (c) 2023–2026 The ggml authors.
- Texto completo incluído no APK: `app/src/main/assets/licenses/llama.cpp-MIT.txt`.
- A fonte é obtida explicitamente por `scripts/fetch_llama_cpp.py`, com SHA-256 do arquivo verificado antes da extração. Não há download de código durante a execução do aplicativo.

## Modelo de linguagem opcional

- Distribuidor: Qwen, modelo `Qwen2.5-0.5B-Instruct-GGUF`.
- Fonte e licença Apache 2.0: https://huggingface.co/Qwen/Qwen2.5-0.5B-Instruct-GGUF
- Os pesos não fazem parte do repositório ou do APK. O usuário autoriza o download do modelo; a identificação, revisão, tamanho e SHA-256 constam no catálogo do aplicativo.
- O runtime e os pesos são projetos diferentes, com licenças próprias. O modelo compacto é de texto; sua inclusão não oferece compreensão de imagens ou vídeo.

## OCR local: Tesseract4Android, Tesseract e idiomas

- Integração Android: [Tesseract4Android 4.9.0](https://github.com/adaptech-cz/Tesseract4Android/tree/4.9.0), Copyright (C) 2019 Adaptech s.r.o., Robert Pösel, licença Apache 2.0. Coordenada JitPack `com.github.adaptech-cz.Tesseract4Android:tesseract4android:4.9.0`, variante padrão sem OpenMP.
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
