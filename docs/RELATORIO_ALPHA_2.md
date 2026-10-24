# NEXUS 2.0.0-alpha.2 — implementação e validação

Atualizado em 24/10/2026 (UTC). Código de versão Android: **21**. Esta etapa preserva o aplicativo existente e seus dados locais. O Samsung Galaxy S9+ informado pelo usuário ainda não foi testado fisicamente pelo ambiente.

## Estado desta etapa

**APK de teste publicado e verificado.** A revisão `b7a5c1b52186fd5ad014c74dafec4649d62c7bb5` passou na compilação local/GitHub, testes JVM/Python, lint e instrumentação Android. A suíte comum aprovou **13 testes**, sem falhas/erros, e informou **1 ignorado** pela ausência da fixture de IA; o workflow dedicado aprovou separadamente **1 teste de IA real**, sem ignorados. Os quatro testes nativos de OCR e o teste da tela Vision passaram. O APK final foi baixado, comparado com os checksums publicados e inspecionado. Isso não certifica o funcionamento físico no S9+.

O [APK alpha.1 de referência](https://github.com/Cazi1n8/nexus-android/releases/download/v2.0.0-alpha.1-build-5/NEXUS-2.0.0-alpha.1.apk) e suas evidências em [RELATORIO_NEXUS_2.md](RELATORIO_NEXUS_2.md) foram preservados. Resultados anteriores e arquivos intermediários não substituem a verificação da revisão final.

## Implementação e alcance da validação

| Área | Implementação da etapa | Validação necessária |
| --- | --- | --- |
| Vision: OCR | Tesseract4Android 4.9.0, variante de uma thread, modelos português e inglês locais | Compilado; quatro testes do leitor OCR aprovados na rodada final. Medição física pendente |
| Modelos OCR | Arquivos oficiais fixados por revisão, tamanho e SHA-256; preparação no build e inclusão no APK | Preparação testada e hashes conferidos nos APKs local e publicado |
| Entrada de imagem | Foto capturada ou imagem importada; galeria independente da permissão de câmera | Tela validada sem ativar sensores e com texto persistido após recriação; seletor Android, permissões negadas e câmeras físicas ainda pendentes |
| Resultado OCR | Copiar, ouvir e salvar memória por ação explícita | Conferir resultado, TTS, autorização, persistência e exclusão |
| Pergunta por voz | Ditado preenche campo editável, envio manual | Serviços Android de fala, cancelamento e retorno de segundo plano |
| IA local no Android | GGUF Qwen 0.5B real no JNI empacotado, executado por instrumentação | Um teste final aprovado, quatro respostas reais registradas. Ausência de GGUF conferida nos APKs local e publicado |
| Regressão | Histórico, configurações, navegação, MediaPipe e runtime anteriores preservados | JVM, lint, instrumentação comum e inspeção do APK publicado aprovados; regressão física pendente |

[Detalhes e limites do OCR local](OCR_LOCAL.md).

## Modelos e privacidade

O OCR usa os arquivos públicos `por.traineddata` e `eng.traineddata` de `tesseract-ocr/tessdata_fast`, revisão `87416418657359cb625c412a48b6e1d6d41c29bd`.

| Arquivo | Tamanho | SHA-256 |
| --- | --- | --- |
| por.traineddata | 1.982.756 bytes | `c4932b937207a9514b7514d518b931a99938c02a28a5a5a553f8599ed58b7deb` |
| eng.traineddata | 4.113.088 bytes | `7d4322bd2a7749724879683fc3912cb542f19906c83bcc1a52132556427170b2` |

São **6.095.844 bytes** de dados OCR antes de compressão. Eles não são versionados no Git, mas são preparados e incluídos no APK. O reconhecimento não exige conta, chave, servidor ou download no celular. Não deve ser confundido com compreensão geral de imagens por um modelo multimodal. Reconhecimento e síntese de fala continuam sujeitos aos serviços instalados no Android e às suas condições de rede.

A prova de IA local usa separadamente o Qwen2.5-0.5B-Instruct Q4_K_M público. O script verifica tamanho e SHA-256 antes da transferência; o teste Android verifica novamente o arquivo privado. Essa fixture temporária não é um download automático no aparelho do usuário, não acompanha o APK e não deve ser publicada como evidência.

## Preparação do runtime OCR

Duas execuções de CI falharam ao resolver metadados do JitPack. A preparação agora baixa o AAR oficial diretamente antes do Gradle, com tamanho e SHA-256 fixados, em `.build-deps/ocr/tesseract4android-4.9.0.aar`. O arquivo não é versionado; o Gradle verifica novamente seu SHA-256 antes de usá-lo. O app não baixa bibliotecas em execução.

- Script: `scripts/fetch_ocr_runtime.py`.
- Tamanho esperado: **12.848.454 bytes**.
- SHA-256: `bce5d6413a1a5ae3d7240033fbbc851ba3217d0a08d9769400e17a077f42cb2a`.
- A suíte Python dos scripts tem 44 testes aprovados, incluindo 14 para o transporte/verificação do AAR.

## Plano de execução reproduzível

```bash
python3 scripts/fetch_llama_cpp.py
python3 scripts/fetch_hand_model.py
python3 scripts/fetch_ocr_models.py
python3 scripts/fetch_ocr_runtime.py
python3 -m unittest discover -s scripts/tests -v
./gradlew --no-daemon --max-workers=2 -Pkotlin.compiler.execution.strategy=in-process testDebugUnitTest lintDebug assembleDebug assembleDebugAndroidTest
```

Com aparelho ou emulador conectado:

```bash
./gradlew connectedDebugAndroidTest
```

A suíte comum não baixa automaticamente um GGUF de aproximadamente 469 MiB. `LocalAiInferenceTest` informa um caso **ignorado** quando o argumento `nexusModelPath` não é fornecido. Isso deve aparecer nos totais registrados; não equivale a um teste de inferência aprovado.

O workflow [local-ai-smoke.yml](../.github/workflows/local-ai-smoke.yml) prepara um emulador Android 10/API 29 x86_64, transfere a fixture verificada para os arquivos privados do aplicativo e executa somente a prova real de IA. Ele recolhe respostas, tempos e observações sobre cálculo, contexto e Unicode. Respostas incorretas devem permanecer visíveis no relatório: sucesso de execução e transporte não certifica a precisão geral do modelo.

O workflow [android-device-smoke.yml](../.github/workflows/android-device-smoke.yml) executa a instrumentação comum. O [android.yml](../.github/workflows/android.yml) compila e publica o APK de teste depois das verificações configuradas.

## Registro dos resultados

| Verificação alpha.2 | Resultado atual |
| --- | --- |
| Testes Python de preparação e runner | **44 aprovados**, 0 falhas/ignorados (incluindo o novo transporte do AAR OCR) |
| Compilação Android e testes JVM | Local: **BUILD SUCCESSFUL in 2m 10s**. GitHub: **BUILD SUCCESSFUL in 4m 2s**. **76 testes JVM aprovados**, 0 falhas/erros/ignorados |
| Lint | **0 erros**; 37 avisos locais/38 no GitHub e 1 informação. O aviso adicional `OldTargetApi` trata do SDK alvo 35 e de futuras exigências da Google Play; publicação na loja permanece pendente. |
| APK de aplicativo e instrumentação | Ambos gerados; APK local final **123.858.856 bytes**, assinatura v2 válida, ZIP alinhado a 16 KB, quatro ABIs e modelos OCR conferidos por SHA-256. **40 bibliotecas nativas: 22 de 64 bits com ELF ≥16 KB e 18 de 32 bits com ELF ≥4 KB**. Sem GGUF. |
| OCR real no Android emulado | **4 testes do leitor OCR aprovados** na rodada final: texto impresso real, imagem branca/EXIF e redução de resolução, limites/caminhos privados, leitor encerrado. Precisão física pendente. |
| Instrumentação comum | **13 aprovados, 0 falhas/erros e 1 ignorado** entre 14 casos declarados, em **53 s** de `connectedDebugAndroidTest`; 8 testes anteriores, 4 OCR e 1 da tela Vision. O caso de IA sem fixture é o único ignorado. |
| GGUF real no Android emulado | **1 teste aprovado**, 0 falhas/erros/ignorados, quatro respostas reais; instrumentação em **35,648 s**, na execução final [40106216702](https://github.com/Cazi1n8/nexus-android/actions/runs/40106216702). |
| Publicação e download alpha.2 | **Publicado, baixado e verificado:** 123.631.398 bytes; SHA-256, assinatura v2, ZIP 16 KB, quatro ABIs, 40 ELFs e modelos OCR conferidos. Sem GGUF. |
| Servidor opcional | **44 aprovados**, 0 falhas/ignorados: 17,279 s local e 17,427 s no GitHub, com acesso aos sockets de teste |
| Testes físicos no S9+ | Não executados pelo ambiente |

### Inferência Android da revisão final

Execução [40106216702](https://github.com/Cazi1n8/nexus-android/actions/runs/40106216702), fonte `b7a5c1b52186fd5ad014c74dafec4649d62c7bb5`: Android 10/API 29 x86_64, contexto de 512 tokens e uma thread. O GGUF tinha 491.400.032 bytes e SHA-256 `74a4da8c9fdbcd15bd1f6d01d621410d31c6fc00986f5eb687824e7b93d7a9db`.

| Caso | Resposta exata observada | Tempo de geração |
| --- | --- | --- |
| 17 + 25 | `42` | 7.400 ms |
| Apresentação do objeto no contexto | `O cubo ciano é meu favorito.` | 7.983 ms |
| Pergunta sobre a cor do objeto | `Ciano.` | 11.569 ms |
| Acentos e emoji | `Olá, coração! 🚀` | 7.036 ms |

Cálculo, recuperação da cor e reprodução do texto solicitado coincidiram com o esperado nesses casos. A frase de apresentação trocou a perspectiva do usuário pela do assistente; as respostas exatas foram preservadas. São observações pequenas, sem certificação de qualidade geral. O carregamento levou 185 ms no emulador, e esses tempos não estimam desempenho no S9+.

Evidência final: [observações JSON](evidencias/alpha2-final-ai-local-ai-observations.json). A instrumentação marcou 35,648 s; o wrapper JUnit registrou 41,121 s, incluindo transferência/coleta e limpeza. A instrumentação comum está preservada em [JUnit Android 10](evidencias/alpha2-final-android10-junit.xml).

A primeira prova real também foi aprovada na execução [40104633801](https://github.com/Cazi1n8/nexus-android/actions/runs/40104633801), fonte `379669afe2d7fa5fd15c544d947a90c0ac262ac8`, antes das correções finais de preparação. Foram as mesmas respostas, instrumentação em 33,578 s e wrapper em 40,864 s; o carregamento levou 174 ms. Evidências históricas: [JSON](evidencias/alpha2-local-ai-observations.json), [instrumentação](evidencias/alpha2-ai-instrumentation.txt) e [JUnit](evidencias/alpha2-ai-junit.xml).

### Workflows finais aprovados

Fonte do APK e das provas finais: `b7a5c1b52186fd5ad014c74dafec4649d62c7bb5`.

| Workflow final | Execução | Resultado |
| --- | --- | --- |
| Build, testes e publicação | [40106216752](https://github.com/Cazi1n8/nexus-android/actions/runs/40106216752) | Aprovado; build em 4m 2s e APK publicado |
| Instrumentação comum | [40106216728, tentativa 2](https://github.com/Cazi1n8/nexus-android/actions/runs/40106216728/attempts/2) | Aprovada; 13 testes aprovados, 0 falhas/erros e 1 ignorado sem fixture |
| IA local real | [40106216702](https://github.com/Cazi1n8/nexus-android/actions/runs/40106216702) | Aprovada; 1 teste, 0 falhas/erros/ignorados, 4 respostas geradas |

A primeira tentativa da suíte comum terminou por falta de heap durante o empacotamento no runner. A repetição concluiu com sucesso; esse incidente de build não demonstra uma falha de memória em uso do aplicativo.

A falha de tela de uma rodada anterior foi corrigida usando a ação de clique de acessibilidade e aguardando o destino real da navegação. O teste aprovado verifica galeria disponível, ausência de câmera/microfone ativos, impossibilidade de enviar sem imagem e persistência do texto após recriação. Ele não abre o seletor de imagens do Android nem testa sensores físicos.

### APK público verificado

**[Baixar NEXUS 2.0.0-alpha.2.apk](https://github.com/Cazi1n8/nexus-android/releases/download/v2.0.0-alpha.2-build-9/NEXUS-2.0.0-alpha.2.apk)**. Release [v2.0.0-alpha.2-build-9](https://github.com/Cazi1n8/nexus-android/releases/tag/v2.0.0-alpha.2-build-9).

- **123.631.398 bytes** (~117,9 MiB).
- SHA-256: `6185c50c7dd75d6c2b160d9933f37e0c23cd3717ae9007a0183faadda663e422`, igual ao digest do GitHub e a `SHA256SUMS.txt`.
- Assinatura v2 válida e alinhamento ZIP de 16 KB.
- Quatro ABIs e 40 bibliotecas nativas: 22 de 64 bits com ELF ≥16 KB e 18 de 32 bits com ELF ≥4 KB.
- Modelos OCR presentes com os hashes esperados; nenhum GGUF no APK.

Inspeções estruturadas: [resumo CI final](evidencias/alpha2-ci-final.json) e [APK publicado](evidencias/alpha2-published-apk.json).

O alinhamento de 24 bibliotecas da alpha.1 permanece um resultado histórico. A alpha.2 tem os 40 componentes nativos descritos acima.

## Próxima validação física

No celular, testar OCR de texto em português bem iluminado, imagem da galeria sem permissão de câmera, imagem sem texto, troca de imagem durante leitura, saída da tela e retorno, cópia, fala e autorização de memória. Conferir também ditado sem envio automático, chat local offline, consumo de RAM e aquecimento.

Câmera física, voz, precisão de mãos, mouse global e manipulação com duas mãos permanecem sem aprovação física. Libras continua limitada a exercícios preparatórios; conteúdo e avaliação pedagógica validados, reconhecimento completo da língua e publicação na Google Play seguem pendentes.

APK local final: **123.858.856 bytes**, SHA-256 `2ad35b79059437e7959971cbccd568d2609a8274e333854aa063a367f71218cf`. Assinatura v2, ZIP 16 KB, bibliotecas nativas e modelos OCR verificados. O arquivo publicado também foi inspecionado; certificados de depuração e hashes diferem entre os ambientes. Se a atualização for recusada por assinatura, exporte os dados importantes antes de desinstalar; a desinstalação apaga os dados locais.
