# NEXUS para Android

Assistente Android nativo em Kotlin e Jetpack Compose, com interface preta, azul e ciano. Este é o mesmo projeto existente: o identificador Android e o banco de conversas foram preservados.

**Código atual: 2.0.0-alpha.3, versão 22 — em validação.** Esta correção trata o relato de download que continua em “Baixando” depois de receber o tamanho completo. O código conclui a transferência pelo tamanho fixado, verifica o SHA-256 e só então libera o modelo. Também preserva arquivos parciais para retomada e mostra conexão, transferência e verificação separadamente. A compilação local passou com **90 testes JVM**, incluindo 14 testes HTTPS do downloader, e lint sem erros. Os testes Android e a publicação desta versão ainda precisam de resultado registrado. [Relatório da alpha.3](docs/RELATORIO_ALPHA_3.md).

**Última publicação verificada: 2.0.0-alpha.2, versão 21.** Passou por 76 testes JVM, 13 testes comuns no Android emulado e uma prova dedicada de IA real; essa prova colocava o modelo no emulador por ADB e não validava o downloader do aplicativo. O usuário relatou o bloqueio em 100% nessa versão. Seus resultados anteriores permanecem no [relatório alpha.2](docs/RELATORIO_ALPHA_2.md).

**Base anterior preservada: 2.0.0-alpha.1.** Essa versão passou por **76 testes JVM**, lint sem erros e **8 testes no Android 10 emulado**. O download público, a assinatura v2 e o alinhamento de suas bibliotecas foram verificados. As evidências permanecem em [PROGRESSO_NEXUS_2.md](PROGRESSO_NEXUS_2.md).

## O que mudou

A alpha.3 separa “arquivo recebido” de “modelo pronto”, oferece pausa/retomada, progresso da verificação e descarte apenas do download parcial. A correção ainda aguarda a execução dos novos testes. Os modelos de conversa continuam exigindo um primeiro download autorizado; não há conta ou chave obrigatória.

NEXUS AI pode executar um modelo de texto no próprio aparelho compatível, sem URL, chave ou conta de IA. O perfil personalizado HTTPS continua disponível separadamente. A evolução também adiciona verificação do aparelho, ajustes de processamento, correções de voz pt-BR, mouse persistente, controles independentes de gestos/3D e exercícios preparatórios de Libras.

Na alpha.2, **NEXUS Vision → Ler texto offline** usa Tesseract4Android e modelos português/inglês incluídos no APK. O fluxo recebe foto ou imagem da galeria, sem depender de câmera para importar; oferece copiar, ouvir e salvar o texto como memória por escolha explícita. O ditado preenche uma pergunta editável; o envio para IA continua manual. [Funcionamento e limites do OCR](docs/OCR_LOCAL.md). Os quatro testes do leitor OCR e o teste da tela Vision passaram na rodada final em emulador. O seletor de imagens do Android, as câmeras e a voz ainda precisam de validação física.

Ainda há pedidos do texto grande pendentes: Libras completa, compreensão local geral de imagens, resumo persistente de conversas e validação física de voz, mouse e gestos. A [matriz de pedidos e estado real](docs/PEDIDOS_E_STATUS.md) detalha cada ponto. Não houve teste físico pelo ambiente no Samsung Galaxy S9+ informado pelo usuário.

## Instalar pelo celular

**APK alpha.2 de referência, com o bloqueio de download relatado; a correção alpha.3 ainda não tem publicação verificada aqui.**

**[Baixar NEXUS 2.0.0-alpha.2.apk](https://github.com/Cazi1n8/nexus-android/releases/download/v2.0.0-alpha.2-build-9/NEXUS-2.0.0-alpha.2.apk)** — aproximadamente **117,9 MiB**. Download direto, sem ZIP ou login no GitHub.

1. Toque no link acima e abra o APK no gerenciador de arquivos. Autorize instalar aplicativos pela origem utilizada se o Android solicitar.
2. Para ler texto sem baixar IA, abra **NEXUS Vision → Escolher imagem → Ler texto no aparelho**. A leitura OCR funciona offline; câmera e microfone só são ativados por suas ações.
3. Para conversar com IA local, abra **Configurações → NEXUS AI**, confira **Ajustar ao aparelho** e autorize o download do modelo recomendado. Prefira Wi-Fi e mantenha o app aberto.
4. Após a verificação do modelo, abra **Chat IA** e envie uma mensagem.
5. Para voz, permita o microfone quando solicitado e instale uma voz **Português (Brasil)** no mecanismo de fala do Android, se necessário.

O [APK da release de teste](https://github.com/Cazi1n8/nexus-android/releases/tag/v2.0.0-alpha.2-build-9) foi publicado e verificado: **123.631.398 bytes**, assinatura v2 válida e SHA-256 `6185c50c7dd75d6c2b160d9933f37e0c23cd3717ae9007a0183faadda663e422`, conferido contra o digest do GitHub e `SHA256SUMS.txt`. O workflow publica o APK diretamente nas releases após um build aprovado da branch `main`. Como alternativa, [GitHub Actions](https://github.com/Cazi1n8/nexus-android/actions/workflows/android.yml) oferece o artefato `nexus-debug-...`: entre na conta, baixe o ZIP, extraia e abra `app-debug.apk`. Artefatos do Actions expiram após 30 dias. Um caminho local como `/workspace/...` não é um endereço público de download.

Builds de ambientes diferentes podem usar certificados de depuração diferentes. Se o Android recusar atualizar por assinatura, exporte dados importantes antes de desinstalar a versão anterior. Desinstalar apaga conversas, modelos e downloads parciais privados; a retomada implementada não preserva arquivos através de uma desinstalação.

## Compatibilidade automática

A base requer **Android 8.0/API 26 ou superior**. O APK alpha.2 publicado foi inspecionado com quatro arquiteturas: ARM64, ARM32, x86_64 e x86. São 40 bibliotecas nativas, com alinhamento ELF ≥16 KB nas 22 de 64 bits e ≥4 KB nas 18 de 32 bits; ZIP alinhado a 16 KB. Os modelos OCR estão presentes e verificados, e não há pesos GGUF no APK.

O aplicativo verifica arquitetura, RAM, armazenamento, CPU, câmeras, microfone e GLES. O perfil automático ajusta recomendação de modelo, contexto, threads, resolução e frequência de análise; efeitos visuais são reduzidos em aparelhos modestos. Downloads e envio remoto sempre exigem escolha explícita.

Nem todo aparelho poderá executar todas as funções. A IA local deste runtime exige processo de 64 bits e margem de RAM. O MediaPipe 0.10.35 fornece a biblioteca nativa de tarefas para as quatro arquiteturas, incluindo x86_64, com alinhamento ELF de 16 KB verificado no AAR oficial. Isso não substitui teste de câmera ou de mãos no aparelho; funções independentes do rastreamento permanecem separadas. Sem suporte, o aplicativo informa a razão e oferece alternativas compatíveis. [Matriz e critérios](docs/COMPATIBILIDADE.md).

## Modelos locais e provedor opcional

| Perfil | Modelo público de terceiros | Download |
| --- | --- | --- |
| Compacto | Qwen2.5 0.5B Instruct Q4_K_M | 491.400.032 bytes, aproximadamente 469 MiB |
| Equilibrado | Qwen2.5 1.5B Instruct Q4_K_M | 1.117.320.736 bytes, aproximadamente 1,04 GiB |

O runtime llama.cpp é compilado no APK; os pesos são baixados separadamente, com revisão e SHA-256 fixados. Nenhuma conversa é enviada a um serviço de IA no modo local. O modelo menor apresentou erros em provas reais; o maior exige mais recursos e também pode errar. Não há promessa de desempenho ou precisão geral.

Os modelos de linguagem locais atuais aceitam **texto**. Na alpha.2, a leitura de letras em fotos pode ocorrer offline pelo OCR separado, com modelos incluídos no APK. Descrever ambientes, interpretar circuitos ou responder perguntas gerais sobre imagens ainda exige provedor multimodal personalizado autorizado. O app processa imagens escolhidas explicitamente; não há compreensão contínua de vídeo.

O contexto local é limitado às mensagens recentes e ao orçamento de tokens do perfil. Entradas grandes são recusadas com explicação. Memórias são autorizadas manualmente, editáveis e excluíveis; não existe memória ilimitada nem resumo persistente automático.

O perfil **Personalizado** usa endpoint HTTPS compatível com OpenAI, modelo e token protegido no Android Keystore. Exige autorização de envio de mensagens, memórias e imagens. Trocar servidor remove o token anterior. Não há credenciais privadas no repositório nem fallback remoto silencioso. Para servidor próprio gratuito em computador, veja [server/README.md](server/README.md); ele é uma opção avançada e não é necessário para IA local em aparelho compatível.

A IA pode propor ações de uma lista restrita. Argumentos são validados e a interface pede confirmação de uso único. Texto livre da resposta nunca é executado como comando. Chamadas de ferramentas no perfil remoto dependem da compatibilidade do endpoint.

## Compilar no computador

Instale Android Studio ou JDK 17 e Android SDK. O projeto usa Gradle Wrapper 8.11.1, SDK 35, build-tools 35.0.0, NDK 28.2.13676358 e CMake 3.22.1. Instale os componentes no SDK Manager e defina `ANDROID_HOME` ou o caminho do SDK em `local.properties`, que não é versionado. O heap do Gradle foi ajustado para 4 GB para a compilação no computador/CI, após uma falha de empacotamento observada com 3 GB. Esse valor não é a memória exigida pelo aplicativo no celular.

```bash
python3 scripts/fetch_llama_cpp.py
python3 scripts/fetch_hand_model.py
python3 scripts/fetch_ocr_models.py
python3 scripts/fetch_ocr_runtime.py
./gradlew --no-daemon --max-workers=2 -Pkotlin.compiler.execution.strategy=in-process testDebugUnitTest lintDebug assembleDebug assembleDebugAndroidTest
```

No Windows, use `gradlew.bat`. O APK fica em `app/build/outputs/apk/debug/app-debug.apk`. Os scripts obtêm fontes, modelo de mãos, dois modelos OCR e o AAR Tesseract4Android de fontes oficiais, com revisões e checksums fixados. `fetch_ocr_runtime.py` salva o AAR em `.build-deps/ocr`, fora do Git; o Gradle confere novamente seu SHA-256. Essa preparação evita depender da resolução de metadados Maven do JitPack, que falhou em duas execuções de CI. Os modelos OCR são empacotados no APK; pesos GGUF de linguagem não são incluídos no Git ou APK. [Build offline, checksums e arquiteturas](docs/IA_LOCAL_BUILD.md).

```bash
python3 -m unittest discover -s scripts/tests -v
python3 -m unittest discover -s server/tests -v
./gradlew connectedDebugAndroidTest
```

O último comando exige aparelho ou emulador conectado. O teste `LocalAiInferenceTest` é ignorado explicitamente quando o GGUF de teste não é fornecido; o [workflow de IA local](.github/workflows/local-ai-smoke.yml) prepara esse arquivo e executa a inferência separadamente. Ele registra respostas reais, tempos e observações de cálculo, contexto e UTF-8; aprovação de transporte não certifica qualidade geral do modelo. Os pesos temporários não são publicados como artefato nem incluídos no APK. A [prova final de IA real no Android](https://github.com/Cazi1n8/nexus-android/actions/runs/40106216702) passou com quatro respostas geradas; as observações estão [neste JSON](docs/evidencias/alpha2-final-ai-local-ai-observations.json).

O [workflow de emulador](.github/workflows/android-device-smoke.yml) prepara Android 10/API 29 no GitHub automaticamente quando alterações relevantes chegam à branch `main`, e também pode ser iniciado manualmente. A [rodada final, tentativa 2](https://github.com/Cazi1n8/nexus-android/actions/runs/40106216728/attempts/2) passou: **13 testes aprovados, 0 falhas/erros e 1 ignorado** sem fixture, em 53 s de instrumentação comum. A IA real passou separadamente. Esses testes não certificam câmera, voz ou mãos físicas. O [workflow principal aprovado](https://github.com/Cazi1n8/nexus-android/actions/runs/40106216752) gera APK e relatórios em alterações na branch `main`.

## Validação nova do download

Na alpha.3, testes HTTPS controlados exercitam o próprio `LocalModelManager`: tamanho completo sem fechamento da conexão, verificação, cancelamento, retomada por `Range` e respostas inválidas. Eles usam arquivos sintéticos pequenos para testar transporte; não avaliam a qualidade de um modelo de linguagem.

O novo [workflow de download real no Android](.github/workflows/model-download-smoke.yml) foi preparado para o próprio aplicativo baixar os dois modelos públicos e executar o compacto depois de chegar a `READY`. Ele não instala o GGUF por ADB. A execução e seus resultados ainda precisam ser registrados no [relatório alpha.3](docs/RELATORIO_ALPHA_3.md). Sem o argumento de autorização do teste, `ProductionModelDownloadTest` é explicitamente ignorado na suíte comum; isso se soma ao caso de inferência que exige sua própria fixture.

## Privacidade e limites

O OCR da alpha.2 funciona no aparelho com Tesseract4Android, sem API, conta, download durante a leitura ou envio da imagem a um servidor. Resultados só entram na memória por uma ação explícita de salvar. A síntese e o reconhecimento de fala seguem as condições dos serviços Android instalados.

Câmera e microfone usam permissões sob demanda. Fala pode depender de serviços instalados ou de rede; o app informa essa condição. Não há escuta permanente de “Ei, NEXUS!” em segundo plano.

Rastreamento de mãos acontece no aparelho. O mouse global exige consentimento, acessibilidade e sessão de câmera em primeiro plano, com notificação e parada de emergência. O serviço não recebe eventos de acessibilidade nem recupera conteúdo das telas. Android, fabricante e aplicativos protegidos podem recusar gestos. O funcionamento global no S9+ ainda precisa de teste físico.

NEXUS Libras oferece exercícios geométricos preparatórios, progresso e referências educacionais. Não reconhece Libras completa nem certifica sinais, gramática ou proficiência. Validação pedagógica permanece pendente. O cubo usa OpenGL ES 2, sem ancoragem espacial de realidade aumentada.

Room fica no sandbox do Android, sem criptografia adicional do banco; backup automático está desativado. Exportações contêm dados pessoais. A assinatura atual é de depuração. Google Play exige assinatura de release, revisão de permissões, acessibilidade, privacidade, dependências nativas e requisitos atuais da loja.

## Documentação

- [Progresso verificável 2.0](PROGRESSO_NEXUS_2.md)
- [Auditoria anterior às mudanças](AUDITORIA_NEXUS.md)
- [Compatibilidade e ajuste automático](docs/COMPATIBILIDADE.md)
- [Pedidos do texto grande e estado real](docs/PEDIDOS_E_STATUS.md)
- [Relatório de testes alpha.3](docs/RELATORIO_ALPHA_3.md)
- [Relatório histórico alpha.2](docs/RELATORIO_ALPHA_2.md)
- [OCR local do NEXUS Vision](docs/OCR_LOCAL.md)
- [Relatório de testes alpha.1](docs/RELATORIO_NEXUS_2.md)
- [Arquitetura 2.0](docs/ARQUITETURA_NEXUS_2.md)
- [Controle global e limites](docs/MOUSE_GLOBAL.md)
- [Alterações](CHANGELOG.md)
- [Licenças e componentes](THIRD_PARTY_NOTICES.md)
- [Progresso histórico 0.1](PROGRESSO.md)
- [GitHub e continuidade no computador](docs/GITHUB.md)
