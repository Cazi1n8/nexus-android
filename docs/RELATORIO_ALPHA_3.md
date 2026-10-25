# NEXUS 2.0.0-alpha.3 — correção do download

Atualizado em **25/10/2026 (UTC)**. Código de versão Android: **22**. Esta etapa preserva o aplicativo existente e trata o relato do usuário: **a tela mostra “Baixando” com o tamanho completo, mas não conclui**.

## Estado da entrega

**APK de teste publicado e verificado.** A revisão `887da8abae76d2730f5ff228ea0e575808de414e` passou na compilação local/GitHub, em 90 testes JVM e na suíte comum Android, com 15 aprovados, zero falhas/erros e dois casos opcionais ignorados. Esses casos passaram separadamente. A prova de produção baixou os dois modelos pelo próprio aplicativo, verificou/revalidou os arquivos e executou o compacto. O APK publicado foi baixado, comparado aos checksums e inspecionado. O S9+ físico permanece sem teste pelo ambiente.

A [alpha.2 publicada](https://github.com/Cazi1n8/nexus-android/releases/tag/v2.0.0-alpha.2-build-9) permanece uma referência histórica. Seu build, OCR e inferência foram testados, mas o modelo da prova de IA era transferido por ADB: esse teste não exercitava o download feito pelo aplicativo. A alpha.3 passou por uma validação própria desse caminho, descrita abaixo. [Evidências anteriores](RELATORIO_ALPHA_2.md).

O ambiente ainda não reproduziu o problema no Samsung Galaxy S9+ físico. Portanto, a espera por fechamento da conexão é um cenário concreto tratado pelo novo código e pelos testes, sem afirmar que já foi comprovado como a causa específica nesse aparelho.

## Alterações implementadas

| Área | Comportamento da alpha.3 | Alcance e validação necessária |
| --- | --- | --- |
| Final de transferência | Recebe o número exato de bytes fixados do modelo e encerra a chamada antes de fechar o corpo HTTP; não faz outra leitura esperando EOF depois do tamanho completo | Aprovado em HTTPS controlado na JVM/Android; download real dos dois modelos também retornou ao chamador |
| Integridade e liberação | Verifica tamanho e SHA-256 do arquivo privado, promove o arquivo e publica `READY` somente após verificação válida | Corrupção rejeitada nos testes JVM; os dois arquivos reais tiveram SHA-256 válido e chegaram a `READY` |
| Retomada | Preserva `.part` em pausa/falha de rede e reaproveita o prefixo ao reabrir o gerenciador | Cancelamento e recriação do gerenciador seguidos de retomada aprovados em HTTPS controlado na JVM e no Android |
| Servidor sem retomada | Se responder `200` ao pedido de intervalo, reinicia a gravação desde o início com aviso | Reinício e intervalo inválido cobertos pelos testes JVM; comportamento do servidor é conferido antes de gravar |
| Arquivo completo já recebido | Pode conferir o parcial completo sem repetir o download | O arquivo precisa pertencer à instalação atual e ter tamanho/hash corretos |
| Limites de espera | Espera de cabeçalhos limitada a 30 s por padrão, até 45 s; leitura limitada a 20 s e timeout total de 30 min | Mensagem de falha/pausa e nova tentativa precisam aparecer; rede parada não pode parecer progresso válido |
| Interface | Mostra conexão, bytes e porcentagem recebidos, velocidade e progresso de verificação separados | “100% recebido” não é apresentado como modelo pronto antes de `READY` |
| Pausa e recuperação | Botões de pausar, continuar, concluir verificação e descartar apenas o parcial com confirmação | Conversas e outro perfil de modelo devem permanecer preservados |
| Ciclo de vida do job | O ViewModel mantém a barreira até a conclusão e libera a referência/estado ativo no término | Nova tentativa não pode ficar bloqueada por job encerrado nem concorrer com o anterior |

Fontes principais: [LocalModelManager](../app/src/main/java/br/com/nexus/assistant/data/local/LocalModelManager.kt), [ModelIntegrity](../app/src/main/java/br/com/nexus/assistant/data/local/ModelIntegrity.kt), [NexusViewModel](../app/src/main/java/br/com/nexus/assistant/NexusViewModel.kt) e [interface](../app/src/main/java/br/com/nexus/assistant/ui/NexusApp.kt).

## Testes executados e diferenças entre as provas

Os testes HTTPS controlados usam arquivos sintéticos pequenos, com SHA-256 conhecido. Eles executam o downloader e o sistema de arquivos reais; não são arquivos de linguagem capazes de responder perguntas. O cliente TLS de teste confia apenas no certificado do servidor local controlado, sem alterar a política HTTPS de produção.

- JVM: `LocalModelDownloadTest` cobre conclusão, resposta de comprimento desconhecido que permanece aberta, redirecionamento, transferência em chunks, proibição de HTTP, corrupção, tamanho incorreto, interrupção, espera sem resposta, leitura parada, cancelamento/retomada, intervalo inválido e exclusão isolada.
- Android: `LocalModelDownloadDeviceTest` reproduz o tamanho completo sem EOF e o cancelamento seguido de retomada por um gerenciador recriado, usando o diretório privado do aplicativo.
- Produção Android: `ProductionModelDownloadTest` baixou os dois GGUFs públicos pelo próprio `LocalModelManager`, comprovou retorno/`READY`/SHA-256, revalidou os arquivos e gerou uma resposta com o compacto. Não há envio de modelo por ADB nessa prova.

O teste de produção é opt-in: requer o argumento `nexusDownloadSmoke=true`. Na suíte comum ele fica explicitamente ignorado, evitando baixar aproximadamente 1,6 GB sem uma escolha de teste. O caso `LocalAiInferenceTest` também é ignorado sem sua fixture própria. Os totais de aprovados, falhos e ignorados precisam refletir a execução efetiva.

O [workflow de download real](../.github/workflows/model-download-smoke.yml) prepara Android 10/API 29 x86_64 com 3 GB de RAM no runner temporário. O [runner](../scripts/run_android_model_download.py) instala os APKs, inicia a instrumentação, recolhe JSON/log/JUnit e limpa apenas o diretório fixo dos modelos de teste. Os pesos não são incluídos no APK nem publicados como evidência. A [execução 40151770826](https://github.com/Cazi1n8/nexus-android/actions/runs/40151770826) foi aprovada; os dados observados estão preservados abaixo.

## Modelos de produção

Os catálogos e seus hashes permanecem fixados:

| Perfil | Arquivo | Bytes | SHA-256 |
| --- | --- | ---: | --- |
| Compacto | `qwen2.5-0.5b-instruct-q4_k_m.gguf` | 491.400.032 | `74a4da8c9fdbcd15bd1f6d01d621410d31c6fc00986f5eb687824e7b93d7a9db` |
| Equilibrado | `qwen2.5-1.5b-instruct-q4_k_m.gguf` | 1.117.320.736 | `6a1a2eb6d15622bf3c96857206351ba97e1af16c30d7a74ee38970e434e9407e` |

O downloader faz somente a conexão necessária para obter o modelo público autorizado. Conversas não entram no pedido de download. A inferência local é limitada ao hardware compatível; o modelo equilibrado não é carregado apenas para comprovar seu download na prova de produção.

## Execução reproduzível

```bash
python3 scripts/fetch_llama_cpp.py
python3 scripts/fetch_hand_model.py
python3 scripts/fetch_ocr_models.py
python3 scripts/fetch_ocr_runtime.py
python3 -m unittest discover -s scripts/tests -v
./gradlew --no-daemon --max-workers=2 -Pkotlin.compiler.execution.strategy=in-process testDebugUnitTest lintDebug assembleDebug assembleDebugAndroidTest
```

Com aparelho ou emulador conectado, para a suíte comum:

```bash
./gradlew connectedDebugAndroidTest
```

Para autorizar explicitamente a prova que baixa os dois modelos reais no Android conectado:

```bash
python3 scripts/run_android_model_download.py
```

O Gradle usa heap de 4 GB no computador/CI, após uma falha de empacotamento observada com 3 GB em uma rodada anterior. Isso não altera o requisito de RAM do aplicativo instalado.

## Resultados registrados nesta etapa

| Verificação alpha.3 | Resultado atual |
| --- | --- |
| Scripts Python | **51 aprovados**, conforme execução registrada pelo agente principal; não comprovam o downloader Android |
| Compilação e testes JVM | Build local: **BUILD SUCCESSFUL in 2m 5s**; build GitHub aprovado. **90 aprovados**, 0 falhas/erros/ignorados, incluindo 14 testes HTTPS novos |
| Lint | **0 erros**; 37 avisos locais/38 no GitHub e 1 informação. O aviso adicional trata do SDK alvo 35 e da revisão necessária antes da Google Play |
| APK local/assinado | **Gerado e inspecionado:** 123.615.014 bytes; assinatura v2, ZIP 16 KB, quatro ABIs e 40 bibliotecas ELF. Bibliotecas de 64 bits ≥16 KB e de 32 bits ≥4 KB. Modelos OCR verificados e nenhum GGUF |
| HTTPS controlado no Android | Os dois testes novos passaram: tamanho completo sem EOF e cancelamento/retomada. A suíte comum completa teve **15 aprovados, 0 falhas/erros e 2 ignorados**, entre 17 casos, em **55 s** |
| Download real dos dois GGUFs no Android | **1 teste aprovado**, sem falhas/erros/ignorados; ambos os modelos baixados pelo app, retorno ao chamador, `READY`, SHA-256 e revalidação confirmados. Instrumentação em **134,447 s** |
| Inferência após o download pelo app | Compacto baixado pelo próprio Android gerou **`42`** para 17 + 25 em **5.305 ms**. Isso não certifica qualidade geral ou desempenho no S9+ |
| Publicação e download do APK alpha.3 | **Publicado, baixado e verificado:** 123.631.398 bytes; digest GitHub/SHA256SUMS, assinatura v2, ZIP 16 KB, quatro ABIs, 40 ELFs e modelos OCR conferidos. Sem GGUF |
| Teste físico no S9+ | Não executado pelo ambiente; confirmação do usuário necessária após uma entrega verificável |

APK local: SHA-256 `11a8a1b2fabd712dd346fe95e6a470cf27a34aa70a87ec29c3587ace2ff9bf09`. Esse hash pertence ao arquivo local; o APK publicado tem hash próprio, verificado separadamente.

## Rodada final no GitHub

Fonte do código: `887da8abae76d2730f5ff228ea0e575808de414e`.

| Workflow | Execução | Resultado |
| --- | --- | --- |
| Build, testes e publicação | [40151770859](https://github.com/Cazi1n8/nexus-android/actions/runs/40151770859) | Aprovado; 90 testes JVM e lint sem erros |
| Android comum | [40151770843](https://github.com/Cazi1n8/nexus-android/actions/runs/40151770843) | 15 aprovados, 0 falhas/erros, 2 opcionais ignorados; [JUnit](evidencias/alpha3-android10-junit.xml) |
| Inferência com fixture | [40151770987](https://github.com/Cazi1n8/nexus-android/actions/runs/40151770987) | 1 teste dedicado aprovado; modelo fornecido por ADB nessa prova anterior preservada |
| Download real pelo app | [40151770826](https://github.com/Cazi1n8/nexus-android/actions/runs/40151770826) | 1 teste aprovado; dois modelos baixados pelo Android e inferência após download |

### Download real observado

Android 10/API 29 x86_64, com o cliente HTTPS e `LocalModelManager` do aplicativo. Não houve injeção dos GGUFs por ADB.

| Modelo | Bytes recebidos | Duração registrada | Conclusão |
| --- | ---: | ---: | --- |
| Compacto 0.5B | 491.400.032 | 37,789 s | `downloadReturned=true`, `READY`, SHA-256 correto e `refreshState=READY` |
| Equilibrado 1.5B | 1.117.320.736 | 82,455 s | `downloadReturned=true`, `READY`, SHA-256 correto e `refreshState=READY` |

Depois de baixar o compacto, o JNI gerou `42` para 17 + 25 em 5.305 ms. A instrumentação completa levou 134,447 s. Os tempos pertencem a essa execução emulada e não estimam a velocidade da rede, armazenamento ou CPU do S9+.

Evidências: [observações de produção](evidencias/alpha3-production-production-download-observations.json), [instrumentação](evidencias/alpha3-production-instrumentation.txt), [JUnit dedicado](evidencias/alpha3-production-junit.xml), [resumo CI](evidencias/alpha3-ci-final.json) e [inspeção do APK publicado](evidencias/alpha3-published-apk.json).

## APK publicado

**[Baixar NEXUS 2.0.0-alpha.3.apk](https://github.com/Cazi1n8/nexus-android/releases/download/v2.0.0-alpha.3-build-10/NEXUS-2.0.0-alpha.3.apk)**. Release [v2.0.0-alpha.3-build-10](https://github.com/Cazi1n8/nexus-android/releases/tag/v2.0.0-alpha.3-build-10), **123.631.398 bytes** (~117,9 MiB).

SHA-256: `779b42cd94980a6eea489d20db4d51be5f65829a6241c54e793ae9de8d7eecd1`. O download público foi comparado ao digest do GitHub e ao `SHA256SUMS.txt`. Assinatura v2, alinhamento ZIP de 16 KB e 40 bibliotecas ELF foram inspecionados nas quatro ABIs: as de 64 bits têm alinhamento ≥16 KB; as de 32 bits, ≥4 KB. Modelos OCR íntegros; nenhum GGUF no pacote.


## Atualização e dados existentes

Pausar/reabrir pode reaproveitar o parcial que continua no armazenamento privado da mesma instalação. **Desinstalar apaga conversas, modelos e parciais.**

A comparação dos APKs publicados confirmou certificados diferentes:

| APK | SHA-256 do certificado |
| --- | --- |
| Alpha.2, build 9 | `029b2feec73e04affde1fa613b660af8bc1ca8bff23ba82f8d1349ff6e94c12f` |
| Alpha.3, build 10 | `d1ff8275cdd5b1d2555d1c0a0e28b2abbd1921e37c82749426a7eb224e0267d0` |

O Android recusa uma atualização direta entre esses dois pacotes. A chave anterior não está disponível para assinar a nova entrega com o mesmo certificado. **Antes de desinstalar a alpha.2, exporte conversas importantes em Memória e histórico.** A exportação é de textos; imagens e token não são incluídos. Após remover a instalação antiga, o modelo privado precisará ser baixado novamente no fluxo corrigido. Esta correção não recupera arquivos apagados pela desinstalação. Uma assinatura de release estável permanece pendente para distribuição futura.

## Pendências do pedido completo

A alpha.3 trata o bloqueio do download, sem declarar todas as correções concluídas. A [matriz do texto grande](PEDIDOS_E_STATUS.md) separa implementação, teste e pendência. Permanecem sem conclusão: Libras completa e validação pedagógica, compreensão multimodal local geral, resumo persistente de conversas, validação física de voz pt-BR/mouse global/gestos/duas mãos e distribuição na Google Play. O SDK alvo 35 e a assinatura de depuração também precisam de revisão antes da publicação na loja.
