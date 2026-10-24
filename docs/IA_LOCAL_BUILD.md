# Compilação da IA local

O NEXUS 2.0 alpha acrescenta inferência de texto local usando llama.cpp, em vez de exigir uma URL de servidor no modo padrão. Isso não significa que todas as funções previstas para a versão 2.0 estejam concluídas.

## Ferramentas

- Java 17, Android SDK 35 e Build Tools 35.0.0.
- Android NDK `28.2.13676358` e CMake `3.22.1`, instaláveis pelo SDK Manager do Android Studio.
- Python 3.10 ou mais recente para preparar as fontes e o modelo de mãos.
- Gradle 8.11.1 fornecido pelo wrapper do repositório.

No terminal do computador, a partir da pasta do projeto:

```sh
sdkmanager 'platforms;android-35' 'build-tools;35.0.0' 'ndk;28.2.13676358' 'cmake;3.22.1'
python3 scripts/fetch_llama_cpp.py
python3 scripts/fetch_hand_model.py
./gradlew testDebugUnitTest lintDebug assembleDebug
```

No Windows, use `gradlew.bat` e o comando Python disponível no computador. Não é necessário instalar ferramentas de desenvolvimento no celular. O GitHub Actions executa a preparação, disponibiliza artefatos e está configurado para publicar um APK direto em [Releases](https://github.com/Cazi1n8/nexus-android/releases) após um build aprovado da branch `main`. A existência do arquivo publicado deve ser conferida em cada entrega.

A fonte verificada do llama.cpp é gerada em `app/src/main/cpp/vendor/llama.cpp`, fora do Git. Seu arquivo original tem SHA-256 `b065f8ba1524df021d35078117b050d436c6a88f30c0ceede8614d9aac22713f`. O script aceita `--archive caminho.tar.gz` para usar uma cópia local com a mesma verificação. A preparação falha se o arquivo estiver corrompido ou contiver caminhos inseguros.

## Compatibilidade e tamanho

O APK universal permite instalação em `arm64-v8a`, `armeabi-v7a`, `x86_64` e `x86`, a partir do Android 8/API 26. Isso não garante todos os recursos em qualquer hardware: câmera, serviço de fala, memória disponível e bibliotecas nativas variam.

A inferência local inclui runtime apenas para `arm64-v8a` (incluindo Galaxy S9+) e `x86_64`. Em processos de 32 bits, o CMake não compila nem empacota o runtime llama.cpp; a interface, a voz e o provedor HTTP personalizado continuam disponíveis. Não há biblioteca que simule respostas. O aplicativo deve recusar o download do modelo local nesse caso e explicar a alternativa.

A dependência atual é MediaPipe Tasks Vision **0.10.35**. O AAR oficial `tasks-core` correspondente foi inspecionado e contém `libmediapipe_tasks_jni.so` para **todas as quatro ABIs**, incluindo `x86_64`. Seus segmentos ELF LOAD apresentam alinhamento `0x4000` (16 KB). A biblioteca declara Android mínimo API 24; o aplicativo mantém mínimo API 26. Esse resultado substitui a limitação observada na dependência 0.10.21 da base auditada e não equivale a teste físico de rastreamento.

O build ARM64 usa a base ARMv8-A, sem exigir instruções dotprod/i8mm de aparelhos mais novos. NDK 28 fornece suporte a páginas de memória de 16 KB para as bibliotecas compiladas aqui. A biblioteca MediaPipe 0.10.35 foi conferida no AAR oficial; o APK definitivo e todas as demais dependências nativas ainda devem passar pela inspeção final de empacotamento antes da distribuição.

Os pesos do modelo são baixados separadamente após autorização no aplicativo. Não coloque arquivos `.gguf`, tokens, chaves privadas ou APKs no Git. A velocidade, o aquecimento e a memória disponível precisam ser medidos no S9+; a compilação não substitui esse teste.

## Licenças

Consulte `THIRD_PARTY_NOTICES.md`. O aviso MIT do runtime também está incluído nos assets do APK.

## Teste instrumentado no GitHub

O workflow **NEXUS Android — teste em emulador** (`.github/workflows/android-device-smoke.yml`) inicia um emulador Android 10/API 29, x86_64, com KVM no executor temporário do GitHub. Ele roda automaticamente em alterações relevantes da branch `main` e também pode ser iniciado em **Actions → Run workflow**. Recompila o aplicativo e executa `connectedDebugAndroidTest`, guardando relatórios por 14 dias.

A configuração do workflow não comprova execução: confira o resultado e os relatórios da execução antes de declarar os testes aprovados. Os testes existentes verificam fluxos do app, cancelamento e armazenamento; não certificam câmera, voz, gestos ou velocidade no Galaxy S9+. Este workflow mantém as câmeras desativadas, não baixa os modelos de linguagem de 491 MB/1,117 GB e não testa inferência com seus pesos. A preparação nativa obtém apenas o código-fonte do runtime.

A configuração de KVM segue a documentação de https://github.com/ReactiveCircus/android-emulator-runner e só altera o executor Linux descartável. Não exige qualquer mudança no celular.
