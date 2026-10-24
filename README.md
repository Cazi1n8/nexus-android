# NEXUS para Android

Assistente Android nativo em Kotlin e Jetpack Compose, com interface preta, azul e ciano. Este é o mesmo projeto existente: o identificador Android e o banco de conversas foram preservados.

**Versão de teste: 2.0.0-alpha.1.** O APK universal foi compilado com **76 testes JVM aprovados**, lint sem erros, assinatura v2 válida e bibliotecas alinhadas a 16 KB. A compilação no GitHub e **8 testes no Android 10 emulado** também passaram; o download público foi verificado. Consulte [PROGRESSO_NEXUS_2.md](PROGRESSO_NEXUS_2.md).

## O que mudou

NEXUS AI pode executar um modelo de texto no próprio aparelho compatível, sem URL, chave ou conta de IA. O perfil personalizado HTTPS continua disponível separadamente. A evolução também adiciona verificação do aparelho, ajustes de processamento, correções de voz pt-BR, mouse persistente, controles independentes de gestos/3D e exercícios preparatórios de Libras.

A implementação ainda precisa das verificações registradas no progresso. Não houve teste físico pelo ambiente no Samsung Galaxy S9+ informado pelo usuário.

## Instalar pelo celular

**[Baixar NEXUS 2.0.0-alpha.1.apk](https://github.com/Cazi1n8/nexus-android/releases/download/v2.0.0-alpha.1-build-5/NEXUS-2.0.0-alpha.1.apk)** — aproximadamente 87,3 MiB. Download direto, sem ZIP ou login no GitHub.

1. Abra [Releases do NEXUS](https://github.com/Cazi1n8/nexus-android/releases).
2. Na versão de teste mais recente, em **Assets**, baixe o arquivo `NEXUS-2.0.0-alpha.1.apk`.
3. Abra o APK no gerenciador de arquivos. Autorize instalar aplicativos pela origem utilizada se o Android solicitar.
4. Abra **Configurações → NEXUS AI**. Confira a recomendação em **Ajustar ao aparelho** e autorize o download do modelo escolhido. Prefira Wi-Fi e mantenha o app aberto.
5. Depois da verificação do arquivo, converse por texto. Para voz, permita o microfone e instale uma voz **Português (Brasil)** no mecanismo de fala do Android, se necessário.

O [APK da release de teste](https://github.com/Cazi1n8/nexus-android/releases/tag/v2.0.0-alpha.1-build-5) foi publicado e verificado. O workflow publica o APK diretamente nas releases após um build aprovado da branch `main`. Como alternativa, [GitHub Actions](https://github.com/Cazi1n8/nexus-android/actions/workflows/android.yml) oferece o artefato `nexus-debug-...`: entre na conta, baixe o ZIP, extraia e abra `app-debug.apk`. Artefatos do Actions expiram após 30 dias. Um caminho local como `/workspace/...` não é um endereço público de download.

Builds de ambientes diferentes podem usar certificados de depuração diferentes. Se o Android recusar atualizar por assinatura, exporte dados importantes antes de desinstalar a versão anterior; desinstalar apaga dados locais.

## Compatibilidade automática

A base requer **Android 8.0/API 26 ou superior** e o APK 2.0 foi inspecionado com quatro arquiteturas: ARM64, ARM32, x86_64 e x86. As 24 bibliotecas nativas têm segmentos ELF alinhados a pelo menos 16 KB, e o alinhamento ZIP foi aprovado.

O aplicativo verifica arquitetura, RAM, armazenamento, CPU, câmeras, microfone e GLES. O perfil automático ajusta recomendação de modelo, contexto, threads, resolução e frequência de análise; efeitos visuais são reduzidos em aparelhos modestos. Downloads e envio remoto sempre exigem escolha explícita.

Nem todo aparelho poderá executar todas as funções. A IA local deste runtime exige processo de 64 bits e margem de RAM. O MediaPipe 0.10.35 fornece a biblioteca nativa de tarefas para as quatro arquiteturas, incluindo x86_64, com alinhamento ELF de 16 KB verificado no AAR oficial. Isso não substitui teste de câmera ou de mãos no aparelho; funções independentes do rastreamento permanecem separadas. Sem suporte, o aplicativo informa a razão e oferece alternativas compatíveis. [Matriz e critérios](docs/COMPATIBILIDADE.md).

## Modelos locais e provedor opcional

| Perfil | Modelo público de terceiros | Download |
| --- | --- | --- |
| Compacto | Qwen2.5 0.5B Instruct Q4_K_M | 491.400.032 bytes, aproximadamente 469 MiB |
| Equilibrado | Qwen2.5 1.5B Instruct Q4_K_M | 1.117.320.736 bytes, aproximadamente 1,04 GiB |

O runtime llama.cpp é compilado no APK; os pesos são baixados separadamente, com revisão e SHA-256 fixados. Nenhuma conversa é enviada a um serviço de IA no modo local. O modelo menor apresentou erros em provas reais; o maior exige mais recursos e também pode errar. Não há promessa de desempenho ou precisão geral.

Os modelos atuais aceitam **texto**. Imagens e NEXUS Vision exigem provedor multimodal personalizado autorizado. O app analisa fotos capturadas explicitamente; não há compreensão contínua de vídeo.

O contexto local é limitado às mensagens recentes e ao orçamento de tokens do perfil. Entradas grandes são recusadas com explicação. Memórias são autorizadas manualmente, editáveis e excluíveis; não existe memória ilimitada nem resumo persistente automático.

O perfil **Personalizado** usa endpoint HTTPS compatível com OpenAI, modelo e token protegido no Android Keystore. Exige autorização de envio de mensagens, memórias e imagens. Trocar servidor remove o token anterior. Não há credenciais privadas no repositório nem fallback remoto silencioso. Para servidor próprio gratuito em computador, veja [server/README.md](server/README.md); ele é uma opção avançada e não é necessário para IA local em aparelho compatível.

A IA pode propor ações de uma lista restrita. Argumentos são validados e a interface pede confirmação de uso único. Texto livre da resposta nunca é executado como comando. Chamadas de ferramentas no perfil remoto dependem da compatibilidade do endpoint.

## Compilar no computador

Instale Android Studio ou JDK 17 e Android SDK. O projeto usa Gradle Wrapper 8.11.1, SDK 35, build-tools 35.0.0, NDK 28.2.13676358 e CMake 3.22.1. Instale os componentes no SDK Manager e defina `ANDROID_HOME` ou o caminho do SDK em `local.properties`, que não é versionado.

```bash
python3 scripts/fetch_llama_cpp.py
python3 scripts/fetch_hand_model.py
./gradlew --no-daemon --max-workers=2 -Pkotlin.compiler.execution.strategy=in-process testDebugUnitTest lintDebug assembleDebug assembleDebugAndroidTest
```

No Windows, use `gradlew.bat`. O APK fica em `app/build/outputs/apk/debug/app-debug.apk`. Os scripts obtêm fontes e modelo de mãos oficiais; pesos GGUF não são incluídos no Git ou APK. [Build offline, checksums e arquiteturas](docs/IA_LOCAL_BUILD.md).

```bash
python3 -m unittest discover -s scripts/tests -v
python3 -m unittest discover -s server/tests -v
./gradlew connectedDebugAndroidTest
```

O último comando exige aparelho ou emulador conectado. O [workflow de emulador](.github/workflows/android-device-smoke.yml) prepara Android 10/API 29 no GitHub automaticamente quando alterações relevantes chegam à branch `main`, e também pode ser iniciado manualmente. A [execução Android 10](https://github.com/Cazi1n8/nexus-android/actions/runs/40053992145) passou nos oito testes de interface, dados e inicialização nativa. Ela não certifica câmera, voz ou mãos físicas. O [workflow principal](.github/workflows/android.yml) gera APK e relatórios em alterações na branch `main`.

## Privacidade e limites

Câmera e microfone usam permissões sob demanda. Fala pode depender de serviços instalados ou de rede; o app informa essa condição. Não há escuta permanente de “Ei, NEXUS!” em segundo plano.

Rastreamento de mãos acontece no aparelho. O mouse global exige consentimento, acessibilidade e sessão de câmera em primeiro plano, com notificação e parada de emergência. O serviço não recebe eventos de acessibilidade nem recupera conteúdo das telas. Android, fabricante e aplicativos protegidos podem recusar gestos. O funcionamento global no S9+ ainda precisa de teste físico.

NEXUS Libras oferece exercícios geométricos preparatórios, progresso e referências educacionais. Não reconhece Libras completa nem certifica sinais, gramática ou proficiência. Validação pedagógica permanece pendente. O cubo usa OpenGL ES 2, sem ancoragem espacial de realidade aumentada.

Room fica no sandbox do Android, sem criptografia adicional do banco; backup automático está desativado. Exportações contêm dados pessoais. A assinatura atual é de depuração. Google Play exige assinatura de release, revisão de permissões, acessibilidade, privacidade, dependências nativas e requisitos atuais da loja.

## Documentação

- [Progresso verificável 2.0](PROGRESSO_NEXUS_2.md)
- [Auditoria anterior às mudanças](AUDITORIA_NEXUS.md)
- [Compatibilidade e ajuste automático](docs/COMPATIBILIDADE.md)
- [Relatório de testes 2.0](docs/RELATORIO_NEXUS_2.md)
- [Arquitetura 2.0](docs/ARQUITETURA_NEXUS_2.md)
- [Controle global e limites](docs/MOUSE_GLOBAL.md)
- [Alterações](CHANGELOG.md)
- [Licenças e componentes](THIRD_PARTY_NOTICES.md)
- [Progresso histórico 0.1](PROGRESSO.md)
- [GitHub e continuidade no computador](docs/GITHUB.md)
