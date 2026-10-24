# NEXUS 2.0.0-alpha.2 — implementação e validação

Atualizado em 24/10/2026 (UTC). Código de versão Android: **21**. Esta etapa preserva o aplicativo existente e seus dados locais. O Samsung Galaxy S9+ informado pelo usuário ainda não foi testado fisicamente pelo ambiente.

## Estado desta etapa

**Em validação.** A compilação local e as verificações estáticas passaram. A publicação e os testes instrumentados novos ainda estão pendentes. O [APK alpha.1 publicado](https://github.com/Cazi1n8/nexus-android/releases/download/v2.0.0-alpha.1-build-5/NEXUS-2.0.0-alpha.1.apk) permanece a última entrega verificada. Evidências dessa base estão em [RELATORIO_NEXUS_2.md](RELATORIO_NEXUS_2.md); elas não substituem a verificação do código novo.

## Alterações a verificar

| Área | Implementação da etapa | Validação necessária |
| --- | --- | --- |
| Vision: OCR | Tesseract4Android 4.9.0, variante de uma thread, modelos português e inglês locais | Compilação, reconhecimento real em Android e medição em aparelho |
| Modelos OCR | Arquivos oficiais fixados por revisão, tamanho e SHA-256; preparação no build e inclusão no APK | Suíte de preparação, inspeção dos assets no APK e execução do leitor |
| Entrada de imagem | Foto capturada ou imagem importada; galeria independente da permissão de câmera | Permissões negadas, aparelhos sem câmera e ciclo de vida |
| Resultado OCR | Copiar, ouvir e salvar memória por ação explícita | Conferir resultado, TTS, autorização, persistência e exclusão |
| Pergunta por voz | Ditado preenche campo editável, envio manual | Serviços Android de fala, cancelamento e retorno de segundo plano |
| IA local no Android | GGUF Qwen 0.5B real no JNI empacotado, executado por instrumentação | Workflow dedicado, relatório de respostas e tempos, ausência de pesos no APK |
| Regressão | Histórico, configurações, navegação, MediaPipe e runtime anteriores preservados | JVM, lint, instrumentação e inspeção do APK final |

[Detalhes e limites do OCR local](OCR_LOCAL.md).

## Modelos e privacidade

O OCR usa os arquivos públicos `por.traineddata` e `eng.traineddata` de `tesseract-ocr/tessdata_fast`, revisão `87416418657359cb625c412a48b6e1d6d41c29bd`.

| Arquivo | Tamanho | SHA-256 |
| --- | --- | --- |
| por.traineddata | 1.982.756 bytes | `c4932b937207a9514b7514d518b931a99938c02a28a5a5a553f8599ed58b7deb` |
| eng.traineddata | 4.113.088 bytes | `7d4322bd2a7749724879683fc3912cb542f19906c83bcc1a52132556427170b2` |

São **6.095.844 bytes** de dados OCR antes de compressão. Eles não são versionados no Git, mas são preparados e incluídos no APK. O reconhecimento não exige conta, chave, servidor ou download no celular. Não deve ser confundido com compreensão geral de imagens por um modelo multimodal. Reconhecimento e síntese de fala continuam sujeitos aos serviços instalados no Android e às suas condições de rede.

A prova de IA local usa separadamente o Qwen2.5-0.5B-Instruct Q4_K_M público. O script verifica tamanho e SHA-256 antes da transferência; o teste Android verifica novamente o arquivo privado. Essa fixture temporária não é um download automático no aparelho do usuário, não acompanha o APK e não deve ser publicada como evidência.

## Plano de execução reproduzível

```bash
python3 scripts/fetch_llama_cpp.py
python3 scripts/fetch_hand_model.py
python3 scripts/fetch_ocr_models.py
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
| Testes Python de preparação e runner | **30 aprovados**, 0 falhas/ignorados (0,063 s) |
| Compilação Android e testes JVM | **BUILD SUCCESSFUL in 2m 29s**; **76 testes JVM aprovados**, 0 falhas/erros/ignorados |
| Lint | **0 erros, 37 avisos e 1 informação** |
| APK de aplicativo e instrumentação | Ambos gerados; APK local 123.615.014 bytes, assinatura v2 válida, ZIP alinhado a 16 KB, quatro ABIs e modelos OCR conferidos por SHA-256. 40 bibliotecas nativas: todas as de 64 bits com ELF ≥16 KB; 32 bits ≥4 KB. |
| OCR real no Android emulado | Ainda não executado ou resultado não registrado |
| Instrumentação comum | Ainda não executada ou resultado não registrado |
| GGUF real no Android emulado | Ainda não executado ou resultado não registrado |
| Publicação e download alpha.2 | Ainda não registrados |
| Servidor opcional | **44 aprovados**, 0 falhas/ignorados (17,279 s), com acesso aos sockets de teste |
| Testes físicos no S9+ | Não executados pelo ambiente |

Após a execução, registrar revisão do código, links dos workflows, números de testes aprovados/falhos/ignorados, tamanho e SHA-256 do APK, assinatura, alinhamento ZIP e bibliotecas nativas. Bibliotecas de OCR acrescentadas exigem nova inspeção; o resultado de 24 bibliotecas da alpha.1 não descreve automaticamente a alpha.2.

## Próxima validação física

No celular, testar OCR de texto em português bem iluminado, imagem da galeria sem permissão de câmera, imagem sem texto, troca de imagem durante leitura, saída da tela e retorno, cópia, fala e autorização de memória. Conferir também ditado sem envio automático, chat local offline, consumo de RAM e aquecimento.

Câmera física, voz, precisão de mãos, mouse global e manipulação com duas mãos permanecem sem aprovação física. Libras continua limitada a exercícios preparatórios; conteúdo e avaliação pedagógica validados, reconhecimento completo da língua e publicação na Google Play seguem pendentes.

APK local preliminar: SHA-256 `6346794b574a41d585dea8c2d3caba6bdb893c0a8440a5b37a8eb280fe48db6c`. O arquivo publicado será inspecionado separadamente. Não há pesos GGUF no APK.
